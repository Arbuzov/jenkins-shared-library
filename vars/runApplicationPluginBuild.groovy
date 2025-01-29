Set<String> getImageList() {
  def images = new HashSet<String>()
  def composeRaw = readFile(
    file: fileExists('docker-compose.yaml')?'docker-compose.yaml':'docker-compose.yml'
  )

  if (fileExists('.env')) {
    def props = readProperties(
      interpolate: true,
      file: '.env'
    )
    props.each{ key, val ->
      echo "Replacing '${key}' with '${val}'"
      composeRaw = composeRaw.replaceAll('\\$\\{'+key.trim()+'\\}', val.trim())
    }
  }

  readYaml(text: composeRaw).services.each{ key, val ->
    images.add(val.image)
  }
  return images
}

def getManifestData() {
  node {
    stage('Get manifest data') {
      checkout scm
      return readJSON(file: 'manifest.json')
    }
  }
}

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

def saveImages(images, dest ) {
  sh "install -d -m 777 ./${dest}"
  images.each{ im ->
    def image = docker.image(im)
    image.pull()
    echo image.toString()
    echo image.imageName().split('/').last()
    sh "docker image save -o ${dest}/${image.imageName().split('/').last()}.tar ${im}"
  }
}

String makeclassifier() {
  if (params.INCLUDE_IMAGES) {
    return params.INCLUDE_IMAGES.toBoolean()?'offline':''
  }
  return ''
}

def inspectImages(images) {
  images.each{ im ->
    echo im.split('/').last()
    sh "grype ${im} > ${im.split('/').last()}.grype.txt"
    sh "syft ${im} > ${im.split('/').last()}.syft.txt"
  }
  writeFile(
    file: "images.txt",
    text: arrayToString(new ArrayList<>(images), ["", "\n"]),
    encoding: "UTF-8"
  )
}

def updateManifest(buildVersion) {
  def manifest = readJSON(file: 'manifest.json')
  manifest.version = buildVersion
  writeJSON(file: 'manifest.json', json: manifest, pretty: 2)
}

def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  pipeline {
    agent {
      label 'farmer-docker-ubuntu'
    }
    options {
      timestamps()
    }
    triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'ACTION', value: '$.action', expressionType: 'JSONPath'],
                [key: 'REPOSITORY_NAME', value: '$.repositoryName', expressionType: 'JSONPath'],
                [key: 'COMPONENT_NAME', value: '$.component.name', expressionType: 'JSONPath'],
                [key: 'COMPONENT_VERSION', value: '$.component.version', expressionType: 'JSONPath']
            ],
            causeString: 'Triggered by application plugin deletion',
            printContributedVariables: true,
            printPostContent: true,
            regexpFilterText: '$ACTION $REPOSITORY_NAME $COMPONENT_NAME',
            regexpFilterExpression: "^DELETED maven\\-releases ${getManifestData().name.replaceAll('-','\\-')}\$"
        )
    }
    parameters { booleanParam(name: 'INCLUDE_IMAGES', defaultValue: false, description: 'Include images from docker-compose.yml into archive') }
    environment {
      ARTIFACT_ID="${readJSON(file: 'manifest.json').name}"
      BASE_VERSION="${readJSON(file: 'manifest.json').version}"
    }
    stages {
      stage ('Clean the old version info') {
        when {
          allOf {
            triggeredBy 'GenericCause'
            expression { return arg.JIRA_PROJECT }
          }
        }
        steps {
          script {
            currentBuild.displayName = COMPONENT_VERSION
            def PLUGIN_DATA = readJSON(file: 'manifest.json')
            def responseVersions = httpRequest(
              url: "https://jira.spacebridge.com/rest/api/2/project/${arg.JIRA_PROJECT}/versions",
              authentication: 'jira-username-password',
              httpMode: 'GET',
              acceptType: 'APPLICATION_JSON',
              consoleLogResponseBody: false,
              quiet: true
            )
            def jsonVersions = readJSON(text: responseVersions.content)
            def version = jsonVersions.find {elem -> elem.name.equals(PLUGIN_DATA.name+'-'+COMPONENT_VERSION) }
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
              url: "https://confluence.spacebridge.com/rest/api/content/search?cql=space=${arg.CONFLUENCE_SPACE}%20AND%20type=page%20AND%20title=%27Release%20${PLUGIN_DATA.name}-${COMPONENT_VERSION}%27&expand=version",
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
            currentBuild.result = 'ABORTED'
            Jenkins.instance.getItemByFullName(env.JOB_NAME).updateNextBuildNumber((env.BUILD_NUMBER as int) - 1)
            throw new org.jenkinsci.plugins.workflow.steps.FlowInterruptedException(hudson.model.Result.ABORTED)
          }
        }
      }
      stage ('Service step') {
        steps {
          echo 'Service step to create an extra pipelines and checks'
        }
      }
      stage ('Save images') {
        when {
          environment name: 'INCLUDE_IMAGES', value: "true"
        }
        steps {
          saveImages(
            getImageList(),
            "images"
          )
        }
      }
      stage ('Build archive release') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
          }
        }
        environment {
          BUILD_VERSION="${BASE_VERSION}-b${BUILD_ID}".toString()
        }
        steps {
          updateManifest(BUILD_VERSION)
          tar(
            file: "${ARTIFACT_ID}.${BUILD_ID}.tar.gz",
            compress: true,
            dir: '.',
            exclude: 'Jenkinsfile,sonar-project.properties'
          )
        }
      }
      stage ('Build archive test') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        environment {
          BUILD_VERSION="${BASE_VERSION}-mr${CHANGE_ID}b${BUILD_ID}-SNAPSHOT".toString()
        }
        steps {
          updateManifest(BUILD_VERSION)
          tar(
            file: "${ARTIFACT_ID}.${BUILD_ID}.tar.gz",
            compress: true,
            dir: '.',
            exclude: 'Jenkinsfile,sonar-project.properties'
          )
        }
      }
      stage('Check images') {
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:latest'
            args '--privileged'
          }
        }
        steps {
          inspectImages(getImageList())
          archiveArtifacts artifacts: '**/*.txt'
          stash name: 'reports', includes: '**/*.txt'
        }
      }
      stage ('Publish test') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        steps {
          unstash 'reports'
          nexusArtifactUploader(
            nexusVersion: 'nexus3',
            protocol: 'http',
            nexusUrl: 'nexus.asatnet.net',
            version: "${BASE_VERSION}-mr${CHANGE_ID}b${BUILD_ID}-SNAPSHOT",
            groupId: "com.spacebridge.application-plugin",
            repository: 'maven-snapshots',
            credentialsId: 'jira-username-password',
            artifacts: [
              [
                artifactId: ARTIFACT_ID,
                classifier: "${makeclassifier()}",
                file: "${ARTIFACT_ID}.${BUILD_ID}.tar.gz",
                type: 'tar.gz',
              ],
              [
                artifactId: ARTIFACT_ID,
                classifier: "images",
                file: "images.txt",
                type: 'txt',
              ]
            ]
          )
          script {
            currentBuild.displayName = "${ARTIFACT_ID}-${BASE_VERSION}-mr${CHANGE_ID}b${BUILD_ID}-SNAPSHOT"
          }
        }
      }
      stage ('Publish release') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
          }
        }
        steps {
          unstash 'reports'
          nexusArtifactUploader(
            nexusVersion: 'nexus3',
            protocol: 'http',
            nexusUrl: 'nexus.asatnet.net',
            version: "${BASE_VERSION}-b${BUILD_ID}",
            groupId: 'com.spacebridge.application-plugin',
            repository: 'maven-releases',
            credentialsId: 'jira-username-password',
            artifacts: [
              [
                artifactId: ARTIFACT_ID,
                classifier: "${makeclassifier()}",
                file: "${ARTIFACT_ID}.${BUILD_ID}.tar.gz",
                type: 'tar.gz',
              ],
              [
                artifactId: ARTIFACT_ID,
                classifier: "images",
                file: "images.txt",
                type: 'txt',
              ]
            ]
          )
          script {
            currentBuild.displayName = "${ARTIFACT_ID}-${BASE_VERSION}-b${BUILD_ID}"
          }
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
          createNewJiraVersion("${ARTIFACT_ID}-${BASE_VERSION}-b${BUILD_ID}", arg.JIRA_PROJECT)
        }
      }
      stage ('Create Release notes') {
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
          echo "Creating release notes(not implemented)"
          unstash 'reports'
          publishSummary(
            arrayToHrefList([
              createConfluencePage(
                TITLE: "Release ${ARTIFACT_ID}-${BASE_VERSION}-b${BUILD_ID}",
                SPACE: arg.CONFLUENCE_SPACE,
                ROOT: arg.ROOT_PAGE,
                CONTENT: inflateTemplate(
                  STRINGIFY: true,
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>(getImageList()), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "${ARTIFACT_ID}-${BASE_VERSION}-b${BUILD_ID}",
                  projectId: arg.JIRA_PROJECT,
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/${ARTIFACT_ID}/${BASE_VERSION}-b${BUILD_ID}/${ARTIFACT_ID}-${BASE_VERSION}-b${BUILD_ID}.tar.gz",
                  boms: loadReports ("*.syft.txt"),
                  vulnerability: loadReports ("*.grype.txt"),
                  build: currentBuild
                )
              )
            ]),
            "Release notes"
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