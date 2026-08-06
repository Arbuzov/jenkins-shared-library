String resolveDebPackage() {
  def packages = findFiles(glob: 'artifacts/*.deb')
  if (packages.size() != 1) {
    error("Expected exactly one .deb in artifacts/, found ${packages.size()}: ${packages*.name.join(', ') ?: 'none'}")
  }
  return packages[0].path
}

def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  if (!arg.PROJECT_NAME) {
    error('runAngularDebBuild: PROJECT_NAME is required')
  }

  pipeline {
    agent none
    options {
      durabilityHint('MAX_SURVIVABILITY')
      timestamps()
      ansiColor('xterm')
      preserveStashes(buildCount: 5)
      buildDiscarder(logRotator(daysToKeepStr: '183', artifactDaysToKeepStr: '183'))
      disableConcurrentBuilds()
    }
    environment {
      SPACEBRIDGE_PROJECT_NAME = "${arg.PROJECT_NAME}"
    }
    stages {
      stage ('Build project') {
        agent {
          dockerfile {
            filename 'Dockerfile'
            additionalBuildArgs '--target tools'
          }
        }
        steps {
          sh '''#!/bin/bash
          set -euo pipefail
          rm -rf coverage
          npm cache clean --force
          npm install
          sed -i "s|develver|$BUILD_NUMBER|" package.json
          sed -i "s|develver|$BUILD_NUMBER|" src/environments/environment.config.ts
          '''
          script { currentBuild.displayName = readJSON(file: 'package.json').version }
          sh '''#!/bin/bash
          set -euo pipefail
          npm run lint:ci
          npm run lint:styles:ci
          npm run test:coverage
          npm run audit
          npm run stats
          npm run deb:prod
          '''
          stash includes: 'artifacts/**', name: 'package'
          stash includes: 'dist/**', name: 'dist'
          stash includes: 'checkstyle*.xml', name: 'reports'
          stash includes: 'coverage/**,audit.*,documentation/**', name: 'test-results'
        }
        post {
          always {
            recordIssues(
              tools: [
                checkStyle(pattern: 'checkstyle*.xml', reportEncoding: 'UTF-8')
              ]
            )
            junit(
              healthScaleFactor: 0,
              testResults: 'coverage/**/test-results.old.xml',
              allowEmptyResults: true
            )
            publishHTML (target: [
              allowMissing: true,
              alwaysLinkToLastBuild: false,
              keepAll: true,
              reportDir: 'documentation',
              reportFiles: 'report.html,stats.html',
              reportName: "Documentation"
            ])
            publishHTML (target: [
              allowMissing: true,
              alwaysLinkToLastBuild: false,
              keepAll: true,
              reportDir: 'coverage',
              reportFiles: 'index.html',
              reportName: "Test Reports"
            ])
          }
          failure {
            sh '''#!/bin/bash
            mkdir -p artifacts
            cp -rf "$(npm config get cache)/_logs/"* artifacts/ 2>/dev/null || true
            '''
            archiveArtifacts artifacts: 'artifacts/**', fingerprint: true, onlyIfSuccessful: false
          }
          cleanup {
            cleanWs()
            sh 'rm -rf "$(npm config get cache)/_logs"'
          }
        }
      }
      stage ('Sonarqube check') {
        agent {
          docker { image 'registry.nexus.asatnet.net/base/sonar-scanner-cli:11.4-b16' }
        }
        steps {
          unstash 'test-results'
          launchSonarQube()
        }
      }
      stage ('Keep snapshot of the build') {
        when {
          changeRequest()
        }
        agent any
        options { skipDefaultCheckout() }
        steps {
          unstash 'dist'
          unstash 'package'
          unstash 'reports'
          unstash 'test-results'
          archiveArtifacts(
            artifacts: "artifacts/**,coverage/**,documentation/**,dist/**,checkstyle*.xml",
            fingerprint: true,
            onlyIfSuccessful: false
          )
        }
        post {
          cleanup {
            cleanWs()
          }
        }
      }
      stage ('Push deb package to the repository') {
        when {
          allOf {
            branch 'master'
            not {
              changeRequest()
            }
            expression { currentBuild.result == null || currentBuild.result == 'SUCCESS' }
          }
        }
        agent {
          label 'local-deb-repo'
        }
        options { skipDefaultCheckout() }
        steps {
          unstash 'package'
          script {
            def debPackage = resolveDebPackage()
            echo "Publishing ${debPackage}"
            sh(
              label: "reprepro includedeb bullseye",
              script: "sudo reprepro -C main -b /data/debian/ includedeb bullseye '${debPackage}'"
            )
          }
        }
        post {
          cleanup {
            cleanWs()
          }
        }
      }
    }
  }
}
