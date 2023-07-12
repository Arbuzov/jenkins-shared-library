def call(String PACKAGE_NAME, String NAMESPACE = 'spacebridge', String BASE_VERSION = '1.0.0') {
  pipeline {
    agent {
      label 'farmer-docker-ubuntu'
    }
    options { timestamps() }
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
            not { branch 'master' }
          }
        }
        environment {
          VERSION = "${BASE_VERSION}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.branch}b${BUILD_NUMBER}"
        }
        steps {
          script {
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build("${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}", "${WORKSPACE} --no-cache --build-arg BASE_VERSION=${BASE_VERSION}")
              image.push(VERSION)
              sh "docker rmi ${NAMESPACE}/${makeYMD()}/${PACKAGE_NAME}:${VERSION}"
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
          }
        }
      }
      stage ('Create production image') {
        when {
          allOf {
            branch 'master'
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
              sh "docker rmi ${NAMESPACE}/${PACKAGE_NAME}:${VERSION} ${NAMESPACE}/${PACKAGE_NAME}:latest"
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
    }
  }
}