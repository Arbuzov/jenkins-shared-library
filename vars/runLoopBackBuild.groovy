def call(String PACKAGE_NAME) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
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
            def envMap = [:]
            sh(script: 'env', returnStdout: true).trim().split('\n').each { line ->
                def index = line.indexOf('=')
                if (index != -1) {
                    def key = line.substring(0, index)
                    def value = line.substring(index + 1)
                    envMap[key] = value
                }
            }
            def dockerENV = envMap
                  .findAll { key, value -> value != null && value.toString().isAllWhitespace() == false }
                  .collect { key, value -> "--build-arg ${key}='${value.replaceAll("(\\r|\\n)", "")}'" }
                  .join(' ')
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build(
                "spacebridge/${PACKAGE_NAME}",
                "${WORKSPACE} ${dockerENV}"
              )
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
              tagToGitlab(
                "${env.GIT_URL}",
                "${env.VERSION}",
                "${env.GIT_COMMIT}",
                "This tag ${env.VERSION} was automaticaly created by Jenkins pipeline.",
                "## Release ${VERSION}\n\n__Build reference:__ ${BUILD_URL}\n\n### Build result\n\ndocker pull registry.nexus.asatnet.net/spacebridge/${PACKAGE_NAME}:${VERSION}"
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
          label 'farmer-docker-ubuntu'
        }
        environment {
          VERSION = "${env.SONAR_PROJECT_VERSION}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH}b${BUILD_NUMBER}"
        }
        steps {
          script {
            def envMap = [:]
            sh(script: 'env', returnStdout: true).trim().split('\n').each { line ->
                def index = line.indexOf('=')
                if (index != -1) {
                    def key = line.substring(0, index)
                    def value = line.substring(index + 1)
                    envMap[key] = value
                }
            }
            def dockerENV = envMap
                  .findAll { key, value -> value != null && value.toString().isAllWhitespace() == false }
                  .collect { key, value -> "--build-arg ${key}='${value.replaceAll("(\\r|\\n)", "")}'" }
                  .join(' ')
            docker.withRegistry('http://registry.nexus.asatnet.net', 'local-registry-access') {
              def image = docker.build(
                "spacebridge/${makeYMD()}/" + PACKAGE_NAME,
                "${WORKSPACE} ${dockerENV}"
              )
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
          VERSION = "${env.SONAR_PROJECT_VERSION}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH}b${BUILD_NUMBER}"
        }
        steps {
          sh "grype registry.nexus.asatnet.net/spacebridge/${makeYMD()}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.grype.txt"
          sh "syft registry.nexus.asatnet.net/spacebridge/${makeYMD()}/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.syft.txt"
          archiveArtifacts artifacts: '**/*.syft.txt,**/*.grype.txt,'
        }
      }
      stage('Inspect production image') {
        when {
          allOf {
            branch 'master'
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
          VERSION = "${env.SONAR_PROJECT_VERSION}-b${BUILD_NUMBER}"
        }
        steps {
          sh "grype -vv registry.nexus.asatnet.net/spacebridge/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.grype.txt"
          sh "syft registry.nexus.asatnet.net/spacebridge/${PACKAGE_NAME}:${VERSION} > ${PACKAGE_NAME}:${VERSION}.syft.txt"
          archiveArtifacts artifacts: "**/*${PACKAGE_NAME}:${VERSION}.syft.txt,**/*${PACKAGE_NAME}:${VERSION}.grype.txt"
        }
      }
      stage('Publish release notes') {
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
          sh "env"
        }
      }
    }
  }
}