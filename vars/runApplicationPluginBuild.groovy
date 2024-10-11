Set<String> getImageList() {
  def images = []
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
    parameters { booleanParam(name: 'INCLUDE_IMAGES', defaultValue: false, description: 'Include images from docker-compose.yml into archive') }
    environment {
      ARTIFACT_ID="${readJSON(file: 'manifest.json').name}"
      BASE_VERSION="${readJSON(file: 'manifest.json').version}"
    }
    stages {
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
          BUILD_VERSION="${BASE_VERSION}.b${BUILD_ID}".toString()
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
          BUILD_VERSION="${BASE_VERSION}.b${BUILD_ID}mr${CHANGE_ID}-SNAPSHOT".toString()
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
          nexusArtifactUploader(
            nexusVersion: 'nexus3',
            protocol: 'http',
            nexusUrl: 'nexus.asatnet.net',
            version: "${BASE_VERSION}.b${BUILD_ID}mr${CHANGE_ID}-SNAPSHOT",
            groupId: "com.spacebridge.application-plugin.${makeYMD().replaceAll('/','.')}",
            repository: 'maven-snapshots',
            credentialsId: 'jira-username-password',
            artifacts: [[
              artifactId: ARTIFACT_ID,
              classifier: "${makeclassifier()}",
              file: "${ARTIFACT_ID}.${BUILD_ID}.tar.gz",
              type: 'tar.gz',
            ]]
          )
          script {
            currentBuild.displayName = "${BASE_VERSION}.b${BUILD_ID}mr${CHANGE_ID}-SNAPSHOT"
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
          nexusArtifactUploader(
            nexusVersion: 'nexus3',
            protocol: 'http',
            nexusUrl: 'nexus.asatnet.net',
            version: "${BASE_VERSION}.b${BUILD_ID}",
            groupId: 'com.spacebridge.application-plugin',
            repository: 'maven-releases',
            credentialsId: 'jira-username-password',
            artifacts: [[
              artifactId: ARTIFACT_ID,
              classifier: "${makeclassifier()}",
              file: "${ARTIFACT_ID}.${BUILD_ID}.tar.gz",
              type: 'tar.gz',
            ]]
          )
          script {
            currentBuild.displayName = "${ARTIFACT_ID}-${BASE_VERSION}.b${BUILD_ID}"
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
          createNewJiraVersion("${ARTIFACT_ID}-${BASE_VERSION}.b${BUILD_ID}", arg.JIRA_PROJECT)
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
                TITLE: "Release ${ARTIFACT_ID}-${BASE_VERSION}.b${BUILD_ID}",
                SPACE: arg.CONFLUENCE_SPACE,
                ROOT: arg.ROOT_PAGE,
                CONTENT: inflateTemplate(
                  STRINGIFY: true,
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(getImageList(), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "${ARTIFACT_ID}-${BASE_VERSION}.b${BUILD_ID}",
                  projectId: arg.JIRA_PROJECT,
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/${ARTIFACT_ID}/${BASE_VERSION}.b${BUILD_ID}/${ARTIFACT_ID}-${BASE_VERSION}.b${BUILD_ID}.tar.gz",
                  boms: loadReports ("*.syft.txt"),
                  vulnerability: loadReports ("*.grype.txt")
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
    }
  }
}