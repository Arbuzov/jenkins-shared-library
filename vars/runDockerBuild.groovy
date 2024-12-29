String loadReports (mask) {
  def filesContent = ''
  def fileList = findFiles(glob: mask)
  fileList.each { file ->
      def content = readFile(file.path)
        content = content.replaceAll(/(\r\n|\r|\n)/, '\\\\n')
        filesContent += file.name + '\\n' +content + '\\n'
  }
  return filesContent
}

String getKeyComponentsInfo(PACKAGE_NAME, VERSION) {
    def componentLines = readFile("${PACKAGE_NAME}:${VERSION}.syft.txt").readLines()
    def result = []
    KEY_COMPONENTS.each { component, details ->
        def componentInfo = ''
        def lineWithVersion = componentLines.find { it.contains(component) }
        def changesInfo = false
        def version = 'unknown'
        if (lineWithVersion) {
            version = lineWithVersion.split("\\s+")[1]
            componentInfo += "<h3>Component: ${component}&nbsp;${version}</h3>"
            def matcher = (lineWithVersion =~ details['buildMask'])
            if (matcher.find()) {
                def buildNumber = matcher.group()
                changesInfo = getJenkinsBuildChanges(
                  PROJECT_ID: details['project'],
                  HEADER: '4',
                  BUILD_NUMBER: buildNumber,
                  DEBUG: false
                )
            }
        }
        if (changesInfo) {
            componentInfo += changesInfo
        } else {
            componentInfo += "Build for ${version} not found"
        }
        result.add(componentInfo)
    }
    return result
}

def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String PACKAGE_NAME = arg.PACKAGE_NAME
  String NAMESPACE = arg.NAMESPACE ?: 'spacebridge'
  String BASE_VERSION = arg.BASE_VERSION ?: '1.0.0'
  Boolean MULTI_BRANCH = arg.MULTI_BRANCH ?: false
  Map KEY_COMPONENTS = arg.KEY_COMPONENTS ?: [:]
  
  pipeline {
    agent {
      label 'farmer-docker-ubuntu'
    }
    options {
      buildDiscarder(logRotator(daysToKeepStr: '183', artifactDaysToKeepStr: '183'))
      timestamps()
    }
    stages {
      stage ('Run hadolint check') {
        agent {
          docker { image 'hadolint/hadolint:latest-alpine' }
        }
        steps {
          sh 'hadolint --no-fail -f checkstyle Dockerfile > hadolint-report.xml'
          stash name: 'hadolint', includes: 'hadolint-report.xml'
        }
      }
      stage ('Run sonarqube check') {
        agent {
            docker { image 'sonarsource/sonar-scanner-cli' }
        }
        environment {
          SONAR_DOCKER_FILE_PATTERNS = "*.dockerfile"
          SONAR_LANG_PATTERNS_DOCKERFILE = "Dockerfile"
        }
        steps {
          script {
            if (env.CHANGE_ID) {
              env.putAt('SONAR_PULLREQUEST_KEY', env.CHANGE_ID)
              env.putAt('SONAR_PULLREQUEST_BRANCH', env.CHANGE_BRANCH)
              env.putAt('SONAR_PULLREQUEST_BASE', env.GIT_BRANCH)
            }
          }
          unstash 'hadolint'
          withSonarQubeEnv('SonarQube Server') {
            sh "sonar-scanner -Dsonar.docker.file.patterns=*.dockerfile -Dsonar.lang.patterns.dockerfile=Dockerfile"
          }
        }
      }
      stage ('Create test image') {
        when {
          anyOf {
            changeRequest()
            not { 
              anyOf {
                branch 'master'
                expression { return MULTI_BRANCH }
              }
            }
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-${changeDefinition()}b${BUILD_NUMBER}"
        }
        steps {
          script {
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build("${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}", "${WORKSPACE} --no-cache --build-arg BASE_VERSION=${BASE_VERSION}")
              image.push(VERSION)
            }
            currentBuild.displayName = VERSION
            publishSummary(
              arrayToString (
                ["docker pull registry.nexus.asatnet.net/${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION}"],
                ["<li>","</li>"],
                ["<ul>","</ul>"]
              ),
              "Image usage"
            )
            withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
              noteToMrGitlab(
                "${env.GITLAB_OA_TARGET_GIT_SSH_URL}",
                "${env.CHANGE_ID}",
                "**Pipeline execution result is an image.**" + 
                "\n\n" + 
                "Use it with:" +
                "\n\n" + 
                "docker pull registry.nexus.asatnet.net/${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION}" + 
                "\n\n"
              )
            }
          }
        }
      }
      stage ('Create production image') {
        when {
          allOf {
            anyOf {
              branch 'master'
              expression { return MULTI_BRANCH }
            }
            not { changeRequest() }
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-b${BUILD_NUMBER}"
        }
        steps {
          script {
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build("${NAMESPACE}/${PACKAGE_NAME}", "${WORKSPACE} --no-cache --build-arg BASE_VERSION=${BASE_VERSION}")
              image.push('latest')
              image.push(VERSION)
              sh "docker rmi ${NAMESPACE}/${PACKAGE_NAME}:latest"
            }
            currentBuild.displayName = VERSION
            publishSummary(
              arrayToString(
              [
                "docker pull registry.nexus.asatnet.net/${NAMESPACE}/${PACKAGE_NAME}:${VERSION}",
                "docker pull registry.nexus.asatnet.net/${NAMESPACE}/${PACKAGE_NAME}:latest",
              ],
              ["<li>","</li>"],
              ["<ul>","</ul>"]
              ),
              "Image usage"
            )
          }
        }
      }
      stage('Inspect test image') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:latest'
            args '--privileged'
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-${changeDefinition()}b${BUILD_NUMBER}"
        }
        steps {
          sh (
            script: "grype -v registry.nexus.asatnet.net/${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.grype.txt",
            returnStatus: true
          )
          sh (
            script: "syft registry.nexus.asatnet.net/${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.syft.txt",
            returnStatus: true
          )
          stash name: 'reports', includes: '**/*.syft.txt,**/*.grype.txt'
          archiveArtifacts artifacts: '**/*.syft.txt,**/*.grype.txt,'
        }
        post {
          cleanup {
            cleanWs()
          }
        }
      }
      stage('Inspect production image') {
        when {
          allOf {
            anyOf {
              branch 'master'
              expression { return MULTI_BRANCH }
            }
            not { changeRequest() }
          }
        }
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:latest'
            args '--privileged'
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-b${BUILD_NUMBER}"
        }
        steps {
          sh (
            script: "grype -v registry.nexus.asatnet.net/${NAMESPACE}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.grype.txt",
            returnStatus: true
          )
          sh (
            script: "syft registry.nexus.asatnet.net/${NAMESPACE}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.syft.txt",
            returnStatus: true
          )
          stash name: 'reports', includes: '**/*.syft.txt,**/*.grype.txt'
          archiveArtifacts artifacts: "**/*${PACKAGE_NAME}:${VERSION}.syft.txt,**/*${PACKAGE_NAME}:${VERSION}.grype.txt"
        }
      }
      stage ('Create Jira Version') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
            expression { return arg.JIRA_PROJECT }
          }
        }
        steps {
          createNewJiraVersion("${PACKAGE_NAME}-${BASE_VERSION}-b${BUILD_NUMBER}", arg.JIRA_PROJECT)
        }
      }
      stage ('Create Confluence description') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
            expression { return arg.CONFLUENCE_SPACE }
            expression { return arg.ROOT_PAGE }
            expression { return arg.JIRA_PROJECT }
          }
        }
        steps {
          unstash 'reports'
          publishSummary(
            arrayToHrefList([
              createConfluencePage(
                TITLE: "Release ${PACKAGE_NAME}-${BASE_VERSION}-b${BUILD_NUMBER}",
                SPACE: arg.CONFLUENCE_SPACE,
                ROOT: arg.ROOT_PAGE,
                CONTENT: inflateTemplate(
                  STRINGIFY: true,
                  TEMPLATE: libraryResource('com/spacebridge/confluence/docker.release.gtpl'),
                  build: currentBuild,
                  keyComponents: getKeyComponentsInfo(PACKAGE_NAME, "${BASE_VERSION}-b${BUILD_NUMBER}").join(""),
                  version: "${BASE_VERSION}-b${BUILD_NUMBER}",
                  imageUri: "registry.nexus.asatnet.net/spacebridge/${PACKAGE_NAME}:${BASE_VERSION}-b${BUILD_NUMBER}",
                  projectId: arg.JIRA_PROJECT,
                  boms: loadReports ("**/*.syft.txt"),
                  vulnerability: loadReports ("**/*.grype.txt")
                )
              )
            ])
          )
        }
      }
    }
    post {
      always {
        cleanWs()
      }
      cleanup {
        cleanWs()
      }
    }
  }
}