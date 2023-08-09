def call(String PACKAGE_NAME) {
  pipeline {
    agent none
    options {
      buildDiscarder(logRotator(daysToKeepStr: '20', artifactDaysToKeepStr: '7'))
      disableConcurrentBuilds()
      preserveStashes(buildCount: 5)
      timestamps()
    }
    stages {
      stage ('Build') {
        agent {
          dockerfile {
            filename 'Dockerfile'
          }
        }
        steps {
          sh 'npm run ci'
          stash(name: 'report', includes: 'report*,report/**,coverage/**', allowEmpty: true)
        }
        post {
          failure {
            junit(
              healthScaleFactor: 0,
              testResults: 'test-results.xml',
              skipMarkingBuildUnstable: true,
              allowEmptyResults: true
            )
          }
          cleanup {
            cleanWs()
          }
        }
      }
      stage ('Run sonarqube check') {
        agent {
            docker { image 'sonarsource/sonar-scanner-cli' }
        }
        steps {
          unstash(name: 'report')
          script {
            env.putAt('SONAR_PROJECT_VERSION', readJSON(file: 'package.json').version)
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
      stage ('Create production docker image') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
          }
        }
        agent {
          label 'farmer-docker-ubuntu'
        }
        environment {
          VERSION = "${env.SONAR_PROJECT_VERSION}-b${BUILD_NUMBER}"
        }
        steps {
          script {
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build("spacebridge/${PACKAGE_NAME}")
              image.push('latest')
              image.push(VERSION)
              currentBuild.displayName = VERSION
              publishSummary(
                arrayToString(
                [
                  "docker pull registry.nexus.asatnet.net/spacebridge/${PACKAGE_NAME}:${VERSION}",
                  "docker pull registry.nexus.asatnet.net/spacebridge/${PACKAGE_NAME}:latest",
                ],
                ["<li>","</li>"],
                ["<ul>","</ul>"]
                ),
                "Image usage"
              )
            }
          }
        }
        post {
          always {
            cleanWs()
          }
        }
      }
      stage ('Create test docker image') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        agent {
          label 'sisyphus.asatnet.net'
        }
        environment {
          VERSION = "${env.SONAR_PROJECT_VERSION}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH}b${BUILD_NUMBER}"
        }
        steps {
          script {
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build("spacebridge/${makeYMD()}/" + PACKAGE_NAME)
              image.push(VERSION)
              currentBuild.displayName = VERSION
              publishSummary(
                arrayToString (
                  ["docker pull registry.nexus.asatnet.net/spacebridge/${makeYMD()}/${PACKAGE_NAME}:${VERSION}"],
                  ["<li>","</li>"],
                  ["<ul>","</ul>"]
                ),
                "Image usage"
              )
            }
          }
        }
        post {
          always {
            cleanWs()
          }
        }
      }
    }
  }
}