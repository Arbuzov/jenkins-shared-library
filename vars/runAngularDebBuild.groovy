def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  pipeline {
    agent {
      label 'farmer-docker-ubuntu'
    }
    options {
      timestamps()
    }
    stages {
      stage('Checkout') {
        steps {
          checkout scm
        }
      }
    }
  }
}