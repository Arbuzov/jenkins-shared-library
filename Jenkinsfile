@Library('souleimanov_jenkins_shared_lib@souleimanov_debug')_
//@Library('debug@debug_library')_
//@Library('asatnet@master')_
pipeline {
  agent {label 'master'}
  options {
    disableConcurrentBuilds()
  }
  stages {
    stage('Test steps')
    {
      steps {
        sh "install -d versioned && install -d latest && env>debug.txt"

        print(getNPMPackageVersion())

//      createNewJiraVersion("1.1.${BUILD_NUMBER}", 'DEVOPS')

        createNewJiraVersion("1.1.0", 'DEVOPS')

        iterateBuilds()

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/', 'artifactory', 'firmware/.*','', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new/test/', 'artifactory', 'firmware/.*','1/2/3', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep with the deep version"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new/test/', 'artifactory', 'firmware/.*','/4/5/6/', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep with the deep version"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new/test/', 'artifactory', 'firmware/.*','', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new', 'artifactory', 'firmware/.*','', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep without trailing slashes"
        )
        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/tree1/2020/11', 'artifactory', 'firmware/.*', 'b' + env.BUILD_NUMBER, false)
          ),
          "Build with the flat number deep folders"
        )

        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/tree2/2020/11', 'artifactory', 'firmware/.*', 'b' + env.BUILD_NUMBER + '/1/2/3', false)
          ),
          "Build with the deep number flat folders"
        )

        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/flat1/2020/11', 'artifactory', 'firmware/.*', 'b' + env.BUILD_NUMBER, true)
          ),
          "Build with the flat number deep folders"
        )

        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/flat2/2020/11', 'artifactory', 'firmware/.*', 'b' + env.BUILD_NUMBER + '/1/2/3', true)
          ),
          "Build with the deep number deep folders"
        )
        sayHello 'User'
        script {
          ARTIFACTORY_UPLOADS = publishArtifactory('FileExchange/debug/', 'artifactory', 'debug.txt', BUILD_NUMBER);
        }
        publishSummary(
          arrayToString(
            ARTIFACTORY_UPLOADS,
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          )
        )
        script {
          if (env.GIT_URL == null) {
            GIT_URL = env.GIT_URL_1;
          } else {
            GIT_URL = env.GIT_URL;
          }
        }
        withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
          tagToGitlab(GIT_URL, "1", env.GIT_COMMIT);
          tagToGitlab(
            GIT_URL,
            "${BUILD_NUMBER}",
            env.GIT_COMMIT,
            "This is a test tag from build ${BUILD_NUMBER}",
            "## Release ${BUILD_NUMBER}\n\n__Build reference:__ ${BUILD_URL}\n\n__Artifactory:__ ${ARTIFACTORY_UPLOADS[0]}"
          );
        }

        copyFromArtifactory('FileExchange/debug/', 'artifactory', 'latest');

        archiveArtifacts(
          artifacts: "**/**",
          onlyIfSuccessful: false
        )
        publishArtifactsInGitlabComment()
        script {
          try {
            timeout(time: 20, unit: 'SECONDS') {
              input (
                message: "Message"
              );
            }
          } catch (err) {
            def user = err.getCauses()[0].getUser()
            if('SYSTEM' == user.toString()) {
              currentBuild.result = "SUCCESS"
            }
          }
        }
        echo getLatestApprover()

        //print mergeRequestsChangeLog('jenkins-violations-secret')
      }
      post {
        always {
          cleanWs()
        }
      }
    }
    stage ('Tests for MR') {
      when {
        changeRequest()
      }
      steps {
        getRelatedBranch(env.GITLAB_OA_TARGET_GIT_SSH_URL, 'jenkins-violations-secret')
        sh "env"
      }
    }
  }
}
