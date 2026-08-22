def getApplicationData() {
  node {
    stage('Get application data') {
      checkout scm
      return readJSON(file: 'package.json')
    }
  }
}


def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  pipeline {
    agent {
      label 'farmer-docker-ubuntu'
    }
    options {
      buildDiscarder(logRotator(daysToKeepStr: '20', artifactDaysToKeepStr: '7'))
      disableConcurrentBuilds()
      preserveStashes(buildCount: 5)
      timestamps()
    }
    triggers {
        GenericTrigger(
            token: 'docker-image',
            genericVariables: [
                [key: 'ACTION', value: '$.action', expressionType: 'JSONPath'],
                [key: 'REPOSITORY_NAME', value: '$.repositoryName', expressionType: 'JSONPath'],
                [key: 'COMPONENT_NAME', value: '$.component.name', expressionType: 'JSONPath'],
                [key: 'COMPONENT_VERSION', value: '$.component.version', expressionType: 'JSONPath']
            ],
            causeString: 'Triggered by image deletion',
            printContributedVariables: true,
            printPostContent: true,
            regexpFilterText: '$ACTION $REPOSITORY_NAME $COMPONENT_NAME',
            regexpFilterExpression: "^DELETED registry\\-host .+${getApplicationData().name.replaceAll('/','-').replaceAll('@','').replaceAll('-','\\-')}\$"
        )
    }
    stages {
      stage ('Clean the old version info') {
        when {
          allOf {
            triggeredBy 'GenericCause'
          }
        }
        steps {
          script {
            currentBuild.displayName = COMPONENT_VERSION
            def APPLICATION_DATA = readJSON(file: 'package.json')
            def responseVersions = httpRequest(
              url: "https://jira.spacebridge.com/rest/api/2/project/${APPLICATION_DATA?.spacebridge?.jira?.project}/versions",
              authentication: 'jira-username-password',
              httpMode: 'GET',
              acceptType: 'APPLICATION_JSON',
              consoleLogResponseBody: false,
              quiet: true
            )
            def jsonVersions = readJSON(text: responseVersions.content)
            def imageName = COMPONENT_NAME.split('/')[-1]
            def version = jsonVersions.find {elem -> elem.name.equals(imageName+'-'+COMPONENT_VERSION) }
            if (version) {
              echo "Found version: ${version.name}"
              def responseAffected = httpRequest(
                url: "https://jira.spacebridge.com/rest/api/2/version/${version.id}/relatedIssueCounts",
                authentication: 'jira-username-password',
                httpMode: 'GET',
                acceptType: 'APPLICATION_JSON',
                consoleLogResponseBody: false,
                quiet: true
              )
              def jsonAffected = readJSON(text: responseAffected.content)
              if (jsonAffected.issuesFixedCount + jsonAffected.issuesAffectedCount + jsonAffected.issueCountWithCustomFieldsShowingVersion > 0) {
                echo "Version ${version.name} has ${jsonAffected.issueCount} issues"
                echo "Version ${version.name} will not be deleted"
              } else {
                echo "Version ${version.name} has no issues"
                def responseDelete = httpRequest(
                  url: "https://jira.spacebridge.com/rest/api/2/version/${version.id}/removeAndSwap",
                  authentication: 'jira-username-password',
                  httpMode: 'POST',
                  contentType: 'APPLICATION_JSON',
                  requestBody: "{\"moveAffectedIssuesTo\": \"\"}",
                  acceptType: 'APPLICATION_JSON',
                  consoleLogResponseBody: false,
                  quiet: true
                )
                echo "Version ${version.name} deleted"
              }
            } else {
              echo "Version with name ${imageName}-${COMPONENT_VERSION} not found"
            }
            def responceNotes = httpRequest(
              url: "https://confluence.spacebridge.com/rest/api/content/search?cql=space=${APPLICATION_DATA?.spacebridge?.confluence?.space}%20AND%20type=page%20AND%20title=%27Release%20${imageName}-${COMPONENT_VERSION}%27&expand=version",
              authentication: 'jira-username-password',
              httpMode: 'GET',
              acceptType: 'APPLICATION_JSON',
              consoleLogResponseBody: false,
              quiet: true
            )
            def jsonNotes = readJSON(text: responceNotes.content)
            if (jsonNotes?.results.size() > 0) {
              if (jsonNotes?.results[0].version.number>1) {
                echo "Note ${jsonNotes.results[0].title} has ${jsonNotes.results[0].version.number} version will not be deleted"
              } else {
                def note = jsonNotes.results[0]
                echo "Note ${note.title} deleted"
                def responseDelete = httpRequest(
                  url: "https://confluence.spacebridge.com/rest/api/content/${note.id}",
                  authentication: 'jira-username-password',
                  httpMode: 'DELETE',
                  contentType: 'APPLICATION_JSON',
                  acceptType: 'APPLICATION_JSON',
                  consoleLogResponseBody: false,
                  quiet: true
                )
              }
            } else {
              echo "No notes found"
            }
            Jenkins.instance.getItemByFullName(env.JOB_NAME).updateNextBuildNumber((env.BUILD_NUMBER as int) - 1)
            currentBuild.result = 'ABORTED'
            throw new org.jenkinsci.plugins.workflow.steps.FlowInterruptedException(hudson.model.Result.ABORTED)
          }
        }
      }
      stage ('Build') {
        agent {
          dockerfile {
            filename 'Dockerfile'
            additionalBuildArgs "${readJSON(file: 'package.json').spacebridge.devops.multistage.toBoolean() ? '--target=tools' : ''}"
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
        }
      }
      stage ('Run sonarqube check') {
        agent {
            docker { image 'registry.nexus.asatnet.net/base/sonar-scanner-cli:latest' }
        }
        steps {
            unstash(name: 'report')
            sh "install -d -m 777 ${WORKSPACE}/.npm/_cacache"
            sh "install -d -m 777 ${WORKSPACE}/.npm/_logs"
            launchSonarQube()
        }
      }
      stage ('Create production docker image') {
        when {
            not { changeRequest() }
        }
        agent {
          label 'farmer-docker-ubuntu'
        }
        environment {
          VERSION = "${readJSON(file: 'package.json').version}-b${BUILD_NUMBER}"
          PROJECT_VERSION = "${readJSON(file: 'package.json').version}-b${BUILD_NUMBER}"
          SONAR_PROJECT_VERSION = "${readJSON(file: 'package.json').version}-b${BUILD_NUMBER}"
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
          }
        }
        agent {
          label 'farmer-docker-ubuntu'
        }
        environment {
          VERSION = "${readJSON(file: 'package.json').version}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH_NAME?.toLowerCase().replaceAll('/','-').replaceAll('@','')}b${BUILD_NUMBER}"
          PROJECT_VERSION = "${readJSON(file: 'package.json').version}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH_NAME?.toLowerCase().replaceAll('/','-').replaceAll('@','')}b${BUILD_NUMBER}"
          SONAR_PROJECT_VERSION = "${readJSON(file: 'package.json').version}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH_NAME?.toLowerCase().replaceAll('/','-').replaceAll('@','')}b${BUILD_NUMBER}"
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
          }
        }
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:latest'
            args '--privileged'
          }
        }
        environment {
          VERSION = "${readJSON(file: 'package.json').version}-${env.CHANGE_ID?('mr'+env.CHANGE_ID):env.BRANCH_NAME?.toLowerCase()}b${BUILD_NUMBER}"
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
            not { changeRequest() }
        }
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:latest'
            args '--privileged'
          }
        }
        environment {
          VERSION = "${readJSON(file: 'package.json').version}-b${BUILD_NUMBER}"
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
          VERSION = "${readJSON(file: 'package.json').version}-b${BUILD_NUMBER}"
          PACKAGE_NAME = "${readJSON(file: 'package.json').name.replaceAll('/','-').replaceAll('@','')}"
        }
        steps {
          script {
            def APPLICATION_DATA = readJSON(file: 'package.json')
            jiraCreateVersion(version: "${env.PACKAGE_NAME}-${VERSION}", projectKey: APPLICATION_DATA.spacebridge.jira.project)
            if (APPLICATION_DATA.spacebridge?.confluence?.notes && APPLICATION_DATA.spacebridge?.confluence?.space && APPLICATION_DATA.spacebridge?.jira?.project) {
              def CONFLUENCE_SPACE = APPLICATION_DATA.spacebridge.confluence.space
              def ROOT_PAGE = APPLICATION_DATA.spacebridge.confluence.notes
              def JIRA_PROJECT = APPLICATION_DATA.spacebridge.jira.project
              addLinkToBuild(
                HEADER: 'Release',
                LINK: createConfluencePage(
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
                    ),
                    FILES: (findFiles(glob: '**/*.syft.txt') + findFiles(glob: '**/*.grype.txt')).collect { it.path }
                  ),
                TEXT: "Release ${env.PACKAGE_NAME}-${VERSION} (Confluence)",
                ICON: 'info.gif'
              )
            }
          }
        }
      }
    }
  }
}