def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String PACKAGE_NAME = arg.PACKAGE_NAME
  String NAMESPACE = arg.NAMESPACE ?: 'spacebridge'
  String BASE_VERSION = arg.BASE_VERSION ?: '1.0.0'
  Boolean MULTI_BRANCH = arg.MULTI_BRANCH ?: false
  
  pipeline {
    agent {
      label 'farmer-docker-ubuntu'
    }
    options {
      buildDiscarder(logRotator(daysToKeepStr: '183', artifactDaysToKeepStr: '183'))
      timestamps()
    }
    stages {
      stage ('Run sonarqube check') {
        agent {
            docker { image 'sonarsource/sonar-scanner-cli' }
        }
        steps {
          script {
            if (env.CHANGE_ID) {
              env.putAt('SONAR_PULLREQUEST_KEY', env.CHANGE_ID)
              env.putAt('SONAR_PULLREQUEST_BRANCH', env.CHANGE_BRANCH)
              env.putAt('SONAR_PULLREQUEST_BASE', env.GIT_BRANCH)
            }
          }
          withSonarQubeEnv('SonarQube Server') {
            sh "sonar-scanner"
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
            sh "env"
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
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:1.0.2-b9'
            args '--privileged'
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-${changeDefinition()}b${BUILD_NUMBER}"
        }
        steps {
          sh "grype registry.nexus.asatnet.net/${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.grype.txt"
          sh "syft registry.nexus.asatnet.net/${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.syft.txt"
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
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:1.0.2-b9'
            args '--privileged'
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-b${BUILD_NUMBER}"
        }
        steps {
          sh "grype -vv registry.nexus.asatnet.net/${NAMESPACE}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.grype.txt"
          sh "syft registry.nexus.asatnet.net/${NAMESPACE}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.syft.txt"
          archiveArtifacts artifacts: "**/*${PACKAGE_NAME}:${VERSION}.syft.txt,**/*${PACKAGE_NAME}:${VERSION}.grype.txt"
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