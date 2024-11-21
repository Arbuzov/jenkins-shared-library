def call(String PACKAGE_NAME = '') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  pipeline {
    agent any
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
            additionalBuildArgs "${readJSON(file: 'package.json')?.spacebridge?.devops?.multistage ? '--target=tools' : ''}"
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
          PACKAGE_NAME = "${readJSON(file: 'package.json').name.replaceAll('/','-').replaceAll('@','')}"
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
                "spacebridge/${env.PACKAGE_NAME}",
                "${WORKSPACE} ${dockerENV}"
              )
              image.push('latest')
              image.push(VERSION)
              currentBuild.displayName = VERSION
              publishSummary(
                arrayToString(
                [
                  "docker pull registry.nexus.asatnet.net/spacebridge/${env.PACKAGE_NAME}:${VERSION}",
                  "docker pull registry.nexus.asatnet.net/spacebridge/${env.PACKAGE_NAME}:latest",
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
                "## Release ${VERSION}\n\n__Build reference:__ ${BUILD_URL}\n\n### Build result\n\ndocker pull registry.nexus.asatnet.net/spacebridge/${env.PACKAGE_NAME}:${VERSION}"
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
          VERSION = "${env.SONAR_PROJECT_VERSION}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH_NAME.replaceAll('/','-').replaceAll('@','')}b${BUILD_NUMBER}"
          PACKAGE_NAME = "${readJSON(file: 'package.json').name.replaceAll('/','-').replaceAll('@','')}"
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
                "spacebridge/${makeYMD()}/${env.PACKAGE_NAME}",
                "${WORKSPACE} ${dockerENV}"
              )
              image.push(VERSION)
              currentBuild.displayName = VERSION
              publishSummary(
                arrayToString (
                  ["docker pull registry.nexus.asatnet.net/spacebridge/${makeYMD()}/${env.PACKAGE_NAME}:${VERSION}"],
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
          PACKAGE_NAME = "${readJSON(file: 'package.json').name.replaceAll('/','-').replaceAll('@','')}"
        }
        steps {
          sh "grype registry.nexus.asatnet.net/spacebridge/${makeYMD()}/${env.PACKAGE_NAME}:${VERSION} > ${env.PACKAGE_NAME}:${VERSION}.grype.txt"
          sh "syft registry.nexus.asatnet.net/spacebridge/${makeYMD()}/${env.PACKAGE_NAME}:${VERSION} > ${env.PACKAGE_NAME}:${VERSION}.syft.txt"
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
          PACKAGE_NAME = "${readJSON(file: 'package.json').name.replaceAll('/','-').replaceAll('@','')}"
        }
        steps {
          sh "grype -vv registry.nexus.asatnet.net/spacebridge/${env.PACKAGE_NAME}:${VERSION} > ${env.PACKAGE_NAME}:${VERSION}.grype.txt"
          sh "syft registry.nexus.asatnet.net/spacebridge/${env.PACKAGE_NAME}:${VERSION} > ${env.PACKAGE_NAME}:${VERSION}.syft.txt"
          archiveArtifacts artifacts: "**/*${env.PACKAGE_NAME}:${VERSION}.syft.txt,**/*${env.PACKAGE_NAME}:${VERSION}.grype.txt"
        }
      }
      stage('Publish release notes') {
        when {
            not { changeRequest() }
        }
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/debian:bullseye-20240812-b2'
          }
        }
        environment {
          VERSION = "${readJSON(file: 'package.json').version}-${env.BRANCH_NAME.equals('master')?'':env.BRANCH_NAME}b${BUILD_NUMBER}"
          PACKAGE_NAME = "${readJSON(file: 'package.json').name.replaceAll('/','-').replaceAll('@','')}"
        }
        steps {
          script {
            def APPLICATION_DATA = readJSON(file: 'package.json')
            println("APPLICATION_DATA: ${APPLICATION_DATA}")
            sh 'env'
            if (APPLICATION_DATA.spacebridge?.confluence?.notes && APPLICATION_DATA.spacebridge?.confluence?.space && APPLICATION_DATA.spacebridge?.jira?.project) {
              def CONFLUENCE_SPACE = APPLICATION_DATA.spacebridge.confluence.space
              def ROOT_PAGE = APPLICATION_DATA.spacebridge.confluence.notes
              def JIRA_PROJECT = APPLICATION_DATA.spacebridge.jira.project
              publishSummary(
                arrayToHrefList([
                  createConfluencePage(
                    TITLE: "Release ${env.PACKAGE_NAME}-${VERSION}",
                    SPACE: CONFLUENCE_SPACE,
                    ROOT: ROOT_PAGE,
                    CONTENT: inflateTemplate(
                      STRINGIFY: true,
                      TEMPLATE: libraryResource('com/spacebridge/confluence/docker.release.gtpl'),
                      build: currentBuild,
                      version: "${VERSION}",
                      imageUri: "registry.nexus.asatnet.net/spacebridge/${env.PACKAGE_NAME}:${VERSION}",
                      projectId: JIRA_PROJECT
                    )
                  )
                ])
              )
            }
          }
        }
      }
    }
  }
}