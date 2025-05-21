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

def updateEnvironment() {
  if (fileExists('.env')) {
    def props = readProperties(
      interpolate: true,
      file: '.env'
    )
    props.each{ key, val ->
      if (MANIFEST.environment.containsKey(key)) {
        props[key] = MANIFEST.environment.get(key)
      }
    }
    writeFile(
      file: '.env',
      text: props.collect { key, val -> "${key}=${val}" }.join('\n')
    )
  }
}

def saveImages(images, dest ) {
  sh "install -d -m 777 ./${dest}"
  images.each{ im ->
    def image = docker.image(im)
    image.pull()
    sh "docker image save -o ${dest}/${image.imageName().split('/').last()}.tar ${im}"
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

def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  MANIFEST = [:]
  IMAGES = new HashSet<String>()

  pipeline {
    agent any
    options {
      timestamps()
      disableConcurrentBuilds()
      buildDiscarder(logRotator(numToKeepStr: '10'))
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
            echo "Cleaning the old version info"
          }
        }
      }
      stage('Collect the plugins') {
        agent {
          docker {
            image 'alpine:3.21.3'
          }
        }
        steps {
          script {
            MANIFEST = readYaml(file: 'Manifest')
          }
          fileOperations(
            [folderCreateOperation(
              folderPath: "${WORKSPACE}/package"
            )] +
            readYaml(file: 'Manifest').plugins.collect { pluginName, pluginVersion ->
              [
                fileDownloadOperation(
                  url: "http://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/${pluginName}/${pluginVersion.replace('-offline','')}/${pluginName}-${pluginVersion}.tar.gz",
                  targetFileName: "${pluginName}-${pluginVersion}.tar.gz",
                  userName: '',
                  password: '',
                  proxyHost: '',
                  proxyPort: '',
                  targetLocation: "${WORKSPACE}/package"
                ),
                fileUnTarOperation(
                  filePath: "${WORKSPACE}/package/${pluginName}-${pluginVersion}.tar.gz",
                  isGZIP: true,
                  targetLocation: "${WORKSPACE}/package/${pluginName}-${pluginVersion}"
                ),
                fileDeleteOperation(
                  includes: "package/${pluginName}-${pluginVersion}.tar.gz"
                ),
                fileDeleteOperation(
                  includes: "package/${pluginName}-${pluginVersion}/images/**"
                ),
                fileCopyOperation(
                  includes: "Manifest",
                  targetLocation: "package"
                )
              ]
            }.flatten()
          )
          stash name: 'package', includes: 'package/**'
        }
        post {
          cleanup {
            cleanWs()
          }
        }
      }
      stage ('Build release') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
          }
        }
        agent {
          label 'farmer-docker-ubuntu'
        }
        steps {
          unstash 'package'
          script {
            MANIFEST.plugins.each{ pluginName, pluginVersion ->
              dir("package/${pluginName}-${pluginVersion}") {
                updateEnvironment()
                IMAGES += getImageList()
              }
            }
            saveImages(IMAGES, 'package/images' )
            MANIFEST.version = "${MANIFEST.version}-b${BUILD_ID}"
          }
          tar(
            file: "${MANIFEST.name}-${MANIFEST.version}.tar.gz",
            dir: 'package/',
            archive: true,
            overwrite: true
          )
          stash name: 'archive', includes: '*.tar.gz'
        }
        post {
          cleanup {
            cleanWs()
          }
        }
      }
      stage ('Build snapshot') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        agent {
          label 'farmer-docker-ubuntu'
        }
        steps {
          unstash 'package'
          script {
            MANIFEST.plugins.each{ pluginName, pluginVersion ->
              dir("package/${pluginName}-${pluginVersion}") {
                updateEnvironment()
                IMAGES += getImageList()
              }
            }
            saveImages(IMAGES, 'package/images' )
            MANIFEST.version = "${MANIFEST.version}-mr${CHANGE_ID}b${BUILD_ID}-SNAPSHOT"
          }
          tar(
            file: "${MANIFEST.name}-${MANIFEST.version}.tar.gz",
            dir: 'package/',
            archive: true,
            overwrite: true
          )
          stash name: 'archive', includes: '*.tar.gz'
        }
        post {
          cleanup {
            cleanWs()
          }
        }
      }
      stage('Check images') {
        agent {
          docker {
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:1.0.2-b23'
            args '--privileged'
          }
        }
        steps {
          inspectImages(new ArrayList<>(IMAGES))
          archiveArtifacts artifacts: '**/*.txt'
          stash name: 'reports', includes: '**/*.txt'
        }
      }
      stage ('Publish release') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
          }
        }
        agent {
          docker {
            image 'alpine:3.21.3'
          }
        }
        steps {
          unstash 'archive'
          nexusArtifactUploader(
            nexusVersion: 'nexus3',
            protocol: 'http',
            nexusUrl: 'nexus.asatnet.net',
            version: "${MANIFEST.version}",
            groupId: "com.spacebridge.application-metaplugin",
            repository: 'maven-releases',
            credentialsId: 'jira-username-password',
            artifacts: [
              [
                artifactId: MANIFEST.name,
                file: "${MANIFEST.name}-${MANIFEST.version}.tar.gz",
                classifier: 'offline',
                type: 'tar.gz',
              ]
            ]
          )
          script {
            currentBuild.displayName = "${MANIFEST.name}-${MANIFEST.version}"
          }
          publishSummary(
            arrayToHrefList([
              "http://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-metaplugin/${MANIFEST.name}/${MANIFEST.version}/${MANIFEST.name}-${MANIFEST.version}.tar.gz"
            ])
          )
        }
      }
      stage ('Publish snapshot') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        agent {
          docker {
            image 'alpine:3.21.3'
          }
        }
        steps {
          unstash 'archive'
          nexusArtifactUploader(
            nexusVersion: 'nexus3',
            protocol: 'http',
            nexusUrl: 'nexus.asatnet.net',
            version: "${MANIFEST.version}",
            groupId: "com.spacebridge.application-metaplugin",
            repository: 'maven-snapshots',
            credentialsId: 'jira-username-password',
            artifacts: [
              [
                artifactId: MANIFEST.name,
                file: "${MANIFEST.name}-${MANIFEST.version}.tar.gz",
                classifier: 'offline',
                type: 'tar.gz',
              ]
            ]
          )
          script {
            currentBuild.displayName = "${MANIFEST.name}-${MANIFEST.version}"
          }
          publishSummary(
            arrayToHrefList([
              "http://nexus.asatnet.net/#browse/browse:maven-snapshots:com%2Fspacebridge%2Fapplication-metaplugin%2F${MANIFEST.name}%2F${MANIFEST.version}"
            ])
          )
        }
      }
      stage('Publish release report') {
        when {
          allOf {
            branch 'master'
            not { changeRequest() }
          }
        }
        agent {
          docker {
            image 'alpine:3.21.3'
          }
        }
        steps {
          unstash 'reports'
          addLinkToBuild(
            HEADER: 'Release',
            LINK: createConfluencePage(
                TITLE: "Release ${MANIFEST.name}-${MANIFEST.version}",
                SPACE: MANIFEST.confluence.space,
                ROOT: MANIFEST.confluence.root,
                CONTENT: inflateTemplate(
                  STRINGIFY: true,
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>(IMAGES), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "${MANIFEST.name}-${MANIFEST.version}",
                  projectId: MANIFEST.jira.project,
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-metaplugin/${MANIFEST.name}/${MANIFEST.version}/${MANIFEST.name}-${MANIFEST.version}-offline.tar.gz",
                  boms: loadReports ("*.syft.txt"),
                  vulnerability: loadReports ("*.grype.txt"),
                  build: currentBuild
                )
              ),
            TEXT: "Release ${MANIFEST.name}-${MANIFEST.version} (Confluence)",
            ICON: 'info.gif'
          )
        }
      }
      stage('Publish snapshot report') {
        when {
          anyOf {
            changeRequest()
            not { branch 'master' }
          }
        }
        agent {
          docker {
            image 'alpine:3.21.3'
          }
        }
        steps {
          unstash 'reports'
          addLinkToBuild(
            HEADER: 'Release',
            LINK: createConfluencePage(
                TITLE: "Release ${MANIFEST.name}-${MANIFEST.version}",
                SPACE: MANIFEST.confluence.space,
                ROOT: MANIFEST.confluence.root,
                CONTENT: inflateTemplate(
                  STRINGIFY: true,
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>(IMAGES), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "${MANIFEST.name}-${MANIFEST.version}",
                  projectId: MANIFEST.jira.project,
                  pluginUrl: "https://nexus.asatnet.net/#browse/browse:maven-snapshots:com%2Fspacebridge%2Fapplication-metaplugin%2F${MANIFEST.name}%2F${MANIFEST.version}-SNAPSHOT",
                  boms: loadReports ("*.syft.txt"),
                  vulnerability: loadReports ("*.grype.txt"),
                  build: currentBuild
                )
              ),
            TEXT: "Release ${MANIFEST.name}-${MANIFEST.version} (Confluence)",
            ICON: 'info.gif'
          )
        }
      }
    }
    post {
      cleanup {
        cleanWs()
      }
    }
  }
}