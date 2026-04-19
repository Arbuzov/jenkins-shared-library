import java.net.URLEncoder

def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  // Default parameters
  def sonarQubeEnvName = arg.sonarQubeEnvName ?: 'SonarQube Server'
  def gitlabUrl = arg.gitlabUrl ?: 'https://gitlab.example.com/'
  def sonarCredentialsId = arg.sonarCredentialsId ?: 'sonar-secret'
  def gitlabCredentialsId = arg.gitlabCredentialsId ?: 'sonarqube-from-jenkins'
  def projectId = arg.projectId ?: env.GITLAB_PROJECT_ID
  def timeoutValue = arg.timeout ?: 5
  def timeoutUnit = arg.timeoutUnit ?: 'MINUTES'
  def abortPipeline = arg.abortPipeline ?: false
  def maxViolations = arg.maxViolations ?: 99999
  def minSeverity = arg.minSeverity ?: 'INFO'
  def reportViolations = arg.reportViolations != null ? arg.reportViolations : true
  def DEBUG = arg.DEBUG != null ? arg.DEBUG : false

  def componentKey = null
  try {
    def sonarProperties = readFile('sonar-project.properties')
    def projectKeyLine = sonarProperties.split('\n').find { it.startsWith('sonar.projectKey=') }
    if (projectKeyLine) {
      componentKey = projectKeyLine.split('=', 2)[1].trim()
    } else {
      error "sonar.projectKey not found in sonar-project.properties and componentKey parameter not provided"
    }
  } catch (Exception e) {
    error "Failed to process sonar-project.properties: ${e.getMessage()}"
  }

  // Wrap all SonarQube operations in withSonarQubeEnv to access SONAR_HOST_URL
  withSonarQubeEnv(sonarQubeEnvName) {
    env.EXPOSED_SONAR_HOST_URL = env.SONAR_HOST_URL
    if (env.MERGE_REQUEST_IID || env.CHANGE_ID) {
      env.SONAR_PULLREQUEST_KEY = (env.CHANGE_ID ?: env.MERGE_REQUEST_IID)
      env.SONAR_PULLREQUEST_BRANCH = env.CHANGE_BRANCH
      env.SONAR_PULLREQUEST_BASE = env.GIT_BRANCH
    }
    def scannerCommand = DEBUG ? "sonar-scanner -X" : "sonar-scanner"
    
    try {
      sh "${scannerCommand}"
    } catch (Exception e) {
      error "SonarQube analysis failed: ${e.getMessage()}"
    }
  }
  // Wait for quality gate
  timeout(time: timeoutValue, unit: timeoutUnit) {
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
      def basicAuth = 'Basic ' + "${SONAR_TOKEN}:".bytes.encodeBase64().toString()
      writeFile(
        file: ".scannerwork/sonar-report.json",
        text: httpRequest(
          url: "${env.EXPOSED_SONAR_HOST_URL}/api/issues/search?componentKeys=${URLEncoder.encode(componentKey, 'UTF-8')}&pullRequest=${URLEncoder.encode(pullRequestId, 'UTF-8')}",
          httpMode: 'GET',
          ignoreSslErrors: true,
          customHeaders: [[name: 'Authorization', value: basicAuth, maskValue: true]],
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
**Reporter**: [{{violation.reporter}}{{#violation.rule}}](${env.EXPOSED_SONAR_HOST_URL}/project/issues?pullRequest=${URLEncoder.encode(pullRequestId, 'UTF-8')}&files={{violation.file}}&issueStatuses=OPEN%2CCONFIRMED&id=${URLEncoder.encode(componentKey, 'UTF-8')})

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
