ArrayList<String> getImageList() {
  def images = []
  def props = {}
  if (fileExists('.env')) {
    props = readProperties(
      interpolate: true,
      file: '.env'
    )
  }
  def composeRaw = readFile(
    file: fileExists('docker-compose.yaml')?'docker-compose.yaml':'docker-compose.yml'
  )
  props.each{ key, val ->
    composeRaw = composeRaw.replaceAll('\\$\\{'+key.trim()+'\\}', val.trim())
  }
  readYaml(text: composeRaw).services.each{ key, val ->
    images.add(val.image)
  }
  return images
}

def saveImages(ArrayList<String> images, dest ) {
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
  echo params.toString()
  if (params.INCLUDE_IMAGES) {
    echo 'at condition'
    return params.INCLUDE_IMAGES.equals('true')?'offline':''
  }
  return ''
}

def inspectImages(ArrayList<String> images) {
  images.each{ im ->
    echo im.split('/').last()
    sh "grype ${im} > ${im.split('/').last()}.grype.txt"
    sh "syft ${im} > ${im.split('/').last()}.syft.txt"
  }
}

def call() {
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
      stage ('Build archive') {
        steps {
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
            image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:1.0.0-b2'
            args '--privileged'
          }
        }
        steps {
          inspectImages(getImageList())
          archiveArtifacts artifacts: '**/*.txt'
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
            version: "${BASE_VERSION}.b${BUILD_ID}",
            groupId: "com.spacebridge.${makeYMD().replaceAll('/','.')}",
            repository: 'application-plugins-dev',
            credentialsId: '25d60c09-852c-4464-9bf1-877c7a165c2c',
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
            groupId: 'com.spacebridge',
            repository: 'application-plugins',
            credentialsId: '25d60c09-852c-4464-9bf1-877c7a165c2c',
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
    }
    post {
      always {
        cleanWs()
      }
    }
  }
}