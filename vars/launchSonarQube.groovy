def call(Map params = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  // Default parameters
  def sonarQubeEnvName = params.sonarQubeEnvName ?: 'SonarQube Server'
  def gitlabUrl = params.gitlabUrl ?: 'https://gitlab.asatnet.net/'
  def sonarCredentialsId = params.sonarCredentialsId ?: 'sonar-secret'
  def gitlabCredentialsId = params.gitlabCredentialsId ?: 'gitlab-pat-01'
  def projectId = params.projectId ?: env.GITLAB_PROJECT_ID
  def timeout = params.timeout ?: 5
  def timeoutUnit = params.timeoutUnit ?: 'MINUTES'
  def abortPipeline = params.abortPipeline ?: false
  def maxViolations = params.maxViolations ?: 99999
  def minSeverity = params.minSeverity ?: 'INFO'
  def reportViolations = params.reportViolations != null ? params.reportViolations : true
  def DEBUG = params.DEBUG != null ? params.DEBUG : false

  def sonarProperties = readFile('sonar-project.properties')
  def projectKeyLine = sonarProperties.split('\n').find { it.startsWith('sonar.projectKey=') }
  if (projectKeyLine) {
    componentKey = projectKeyLine.split('=', 2)[1].trim()
  } else {
    error "sonar.projectKey not found in sonar-project.properties and componentKey parameter not provided"
  }

  // Wrap all SonarQube operations in withSonarQubeEnv to access SONAR_HOST_URL
  withSonarQubeEnv(sonarQubeEnvName) {
    // Run SonarQube analysis
    if (env.MERGE_REQUEST_IID || env.CHANGE_ID) {
      def sonarPrKey = env.CHANGE_ID ?: env.MERGE_REQUEST_IID
      env.putAt('SONAR_PULLREQUEST_KEY', sonarPrKey)
      env.putAt('SONAR_PULLREQUEST_BRANCH', env.CHANGE_BRANCH)
      env.putAt('SONAR_PULLREQUEST_BASE', env.GIT_BRANCH)
    }
    def scannerCommand = DEBUG ? "sonar-scanner -X" : "sonar-scanner"
    
    try {
      sh "${scannerCommand}"
    } catch (Exception e) {
      error "SonarQube analysis failed: ${e.getMessage()}"
    }

    // Wait for quality gate
    timeout(time: timeout, unit: timeoutUnit) {
      waitForQualityGate(abortPipeline: abortPipeline)
    }

    // Get SonarQube issues report for pull requests
    if (componentKey && (env.MERGE_REQUEST_IID || env.CHANGE_ID)) {
      def pullRequestId = env.MERGE_REQUEST_IID ?: env.CHANGE_ID
      
      withCredentials([
        string(
          credentialsId: sonarCredentialsId,
          variable: 'SONAR_TOKEN'
        )
      ]) {
        writeFile(
          file: ".scannerwork/sonar-report.json",
          text: httpRequest(
            url: "${env.SONAR_HOST_URL}/api/issues/search?componentKeys=${componentKey}&pullRequest=${pullRequestId}",
            httpMode: 'GET',
            ignoreSslErrors: true,
            customHeaders: [[name: 'Authorization', value: SONAR_TOKEN]],
            consoleLogResponseBody: DEBUG,
            quiet: !DEBUG
          ).content,
          encoding: "UTF-8"
        )
      }

      // Report violations to GitLab only if reportViolations is true
      if (reportViolations) {
        step([
          $class: 'ViolationsToGitLabRecorder',
          config: [
            gitLabUrl: gitlabUrl,
            projectId: projectId,
            mergeRequestIid: pullRequestId,
            commentOnlyChangedContent: true,
            commentOnlyChangedContentContext: 0,
            commentOnlyChangedFiles: true,
            createSingleFileComments: true,
            createCommentWithAllSingleFileComments: true,
            minSeverity: minSeverity,
            maxNumberOfViolations: maxViolations,
            enableLogging: DEBUG,
            apiTokenCredentialsId: gitlabCredentialsId,
            apiTokenPrivate: true,
            authMethodHeader: true,
            ignoreCertificateErrors: true,
            keepOldComments: false,
            commentTemplate: """
**Reporter**: [{{violation.reporter}}{{#violation.rule}}](${env.SONAR_HOST_URL}/project/issues?pullRequest=${pullRequestId}&files={{violation.file}}&issueStatuses=OPEN%2CCONFIRMED&id=${componentKey})

**Rule**: {{violation.rule}}{{/violation.rule}}
**Severity**: {{violation.severity}}
**File**: {{violation.file}} L{{violation.startLine}}{{#violation.source}}

**Source**: {{violation.source}}{{/violation.source}}

{{violation.message}}
            """,
            violationConfigs: [[
              pattern: '.*sonar-report\\.json$',
              parser: 'SONAR',
              reporter: 'Sonar'
            ]]
          ]
        ])
      }
    }
  }
}
