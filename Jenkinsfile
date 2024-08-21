pipeline {
  agent {label 'master'}
  options {
    disableConcurrentBuilds()
  }
  stages {
    stage('Test steps')
    {
      steps {
        library(
          identifier: "custom-lib@${env.GIT_BRANCH}",
          retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "${env.GITLAB_OA_SOURCE_GIT_SSH_URL?env.GITLAB_OA_SOURCE_GIT_SSH_URL:env.GIT_URL}",
            credentialsId: 'efedb983-6b76-42ee-ad7f-ed211b08657a'
          ])
        )
        script {
          // NOTE: publish under directory debug/firmware in Nexus
          env.putAt('PIPELINE_BIN_REPO_FULL_PATH', getIDURepoPath(namespace: 'debug',
                                                                  project: 'firmware',
                                                                  use_commit_id: false))
        }

        // script {
        //   // This is a test for confluence publishing please keep it commented
        //   String loadReports (mask) {
        //     def filesContent = ''
        //     def fileList = findFiles(glob: mask)
        //     fileList.each { file ->
        //         def content = readFile(file.path)
        //           content = content.replaceAll(/(\r\n|\r|\n)/, '\\\\n')
        //           filesContent += content + '\\n'
        //     }
        //     return filesContent
        //   }
        //   def bind = [
        //     TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.template.html'),
        //     STRINGIFY: true,
        //     date: new Date().format('yyyy-MM-dd'),
        //     images: arrayToString(["adasdas", "asdasd"], ["<li>", "</li>"], ["<ul>", "</ul>"]),
        //     version: '1.0.0',
        //     projectId: 'EH',
        //     pluginUrl: 'https://confluence.atlassian.com/display/CONF/Confluence+Documentation',
        //     boms: loadReports ("resources/com/spacebridge/test/*.syft.txt"),
        //     vulnerability: loadReports ("resources/com/spacebridge/test/*.grype.txt")
        //   ]
        //   createConfluencePage(
        //     TITLE: "Test page ${env.BUILD_NUMBER}",
        //     SPACE: '~arbuzov',
        //     ROOT: '181239839',
        //     CONTENT: inflateTemplate(bind)
        //   )
        // }

        print("PIPELINE_BIN_REPO_FULL_PATH = " + env.getAt('PIPELINE_BIN_REPO_FULL_PATH'))
        echo (getBuildCause().toString())

        print (getBuildCause().findAll{it.equals("io.jenkins.plugins.gitlabbranchsource.GitLabWebHookCause")})

        requireCleanGitWorkTree()
        // doCleanGitWorkTree()
        requireNoGeneratedGlobalFiles()

        echo "Commit ID that triggered the build: '" + getGitBuildTriggerCommitID() + "'"
        echo "CHANGE_REQUEST_BUILD_NUMBER='${getChangeRequestBuildNumber()}'"

        sh "install -d versioned && install -d latest && env>debug.txt"

        sh "install -d -m 0755 scripts/jenkins"
        loadSharedLibraryFile("pipelinelib_functions.sh", "scripts/jenkins/tmp_pipelinelib_functions.sh")

        print(getNPMPackageVersion())
        print(makeYMD())
        print(makeYMD('-'))
        createNewJiraVersion("1.1.0", 'DEVOPS')

        // assignJiraFixVersion("1.1.0", 'DEVOPS', "In Progress")

        // iterateBuilds()

        echo getLastTag()

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/', 'artifactory', 'resources/.*','', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list"
        )

        script {
          String repo_full_path = env.getAt('PIPELINE_BIN_REPO_FULL_PATH')
          print("repo_full_path = " + repo_full_path)
          def pub_arr = publishNexus('resources/com/spacebridge/**/pipelinelib_functions.sh', "${repo_full_path}/", true)
          String href_list = arrayToHrefList(pub_arr)
          publishSummary(href_list, 'Files in Nexus')
        }

        script {
          String repo_full_path = env.getAt('PIPELINE_BIN_REPO_FULL_PATH')
          print("repo_full_path = " + repo_full_path)
          def pub_arr = publishNexus('resources/com/spacebridge/**/pipelinelib_functions.sh', "${repo_full_path}/", false)
          String href_list = arrayToHrefList(pub_arr)
          publishSummary(href_list, 'Files in Nexus (not flattened)')
        }

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new/test/', 'artifactory', 'resources/.*','1/2/3', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep with the deep version"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new/test/', 'artifactory', 'resources/.*','/4/5/6/', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep with the deep version"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new/test/', 'artifactory', 'resources/.*','', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep"
        )

        publishSummary(
          arrayToString(
            publishArtifactory('FileExchange/debug/new', 'artifactory', 'resources/.*','', false),
            ["\t<li>","</li>\n"],
            ["<ul>\n","</ul>\n"]
          ),
          "Unversioned list in the deep without trailing slashes"
        )
        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/tree1/2020/11', 'artifactory', 'resources/.*', 'b' + env.BUILD_NUMBER, false)
          ),
          "Build with the flat number deep folders"
        )

        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/tree2/2020/11', 'artifactory', 'resources/.*', 'b' + env.BUILD_NUMBER + '/1/2/3', false)
          ),
          "Build with the deep number flat folders"
        )

        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/flat1/2020/11', 'artifactory', 'resources/.*', 'b' + env.BUILD_NUMBER, true)
          ),
          "Build with the flat number deep folders"
        )

        publishSummary(
          arrayToHrefList(
            publishArtifactory('FileExchange/debug/flat2/2020/11', 'artifactory', 'resources/.*', 'b' + env.BUILD_NUMBER + '/1/2/3', true)
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
        tagToGitlab(GIT_URL, "rolling", env.GIT_COMMIT, "rolling tag", "deprecated", false);
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
        sh """#!/bin/sh
              set -x
              set -e

              . scripts/jenkins/tmp_pipelinelib_functions.sh

              pipelinelib_do_debug_show_info
              pipelinelib_override_source_date_epoch
              pipelinelib_override_build_number
              pipelinelib_override_build_tag

              pipelinelib_generate_checksums_file . . sha256sum .tmp_00_sha256sum_shared_lib.txt

              cat .tmp_00_sha256sum_shared_lib.txt

              ( cd ./resources && find . ! -type d ! -type l -print | sed -e 's,^[.]/,,' | LC_ALL=C sort | pipelinelib_generate_checksums_bsd_for_list ; ) >.tmp_01_checksums.txt

              pipelinelib_do_debug_show_info

        """

        rtp(
            stableText: '<h3>Checksums for files</h3><pre>' +
                    readFile('.tmp_01_checksums.txt') +
                    '</pre>',
            unstableAsStable: true,
            parserName: 'HTML',
            failedAsStable: true,
            abortedAsStable: true,
            nullAction: '1'
        )

        copyFromArtifactory('FileExchange/debug/', 'artifactory', 'latest');

        archiveArtifacts(
          artifacts: "**/**",
          onlyIfSuccessful: false
        )

        echo getLatestApprover()

        //print mergeRequestsChangeLog('jenkins-violations-secret')

        script {
          String repo_full_path = getIDURepoPath()
          String repo_top_path = getIDURepoTopPath()
          String repo_version_path = getIDURepoVersionPath()

          publishSummary("repo_full_path = " + repo_full_path + ", <br />" +
                         "repo_top_path = " + repo_top_path + ", <br />" +
                         "repo_version_path = " + repo_version_path + ", <br />",
                         "IDU repository path example unknown")
        }
        script {
          String repo_full_path = getIDURepoPath(project: 'example_project')
          String repo_top_path = getIDURepoTopPath(project: 'example_project')
          String repo_version_path = getIDURepoVersionPath(project: 'example_project')

          publishSummary("repo_full_path = " + repo_full_path + ", <br />" +
                         "repo_top_path = " + repo_top_path + ", <br />" +
                         "repo_version_path = " + repo_version_path + ", <br />",
                         "IDU repository path example known")
        }
        script {
          String repo_full_path = getIDURepoPath(project: 'example_project',
                                                    use_commit_id: true)
          String repo_top_path = getIDURepoTopPath(project: 'example_project',
                                                    use_commit_id: true)
          String repo_version_path = getIDURepoVersionPath(project: 'example_project',
                                                            use_commit_id: true)

          publishSummary("repo_full_path = " + repo_full_path + ", <br />" +
                         "repo_top_path = " + repo_top_path + ", <br />" +
                         "repo_version_path = " + repo_version_path + ", <br />",
                         "IDU repository path example with commit IDs")
        }
        script {
          String repo_full_path = getIDURepoPath(project: 'example_project',
                                                    use_commit_id: false)
          String repo_top_path = getIDURepoTopPath(project: 'example_project',
                                                    use_commit_id: false)
          String repo_version_path = getIDURepoVersionPath(project: 'example_project',
                                                            use_commit_id: false)

          publishSummary("repo_full_path = " + repo_full_path + ", <br />" +
                         "repo_top_path = " + repo_top_path + ", <br />" +
                         "repo_version_path = " + repo_version_path + ", <br />",
                         "IDU repository path example without commit IDs")
        }
        script {
            Map envmap = [
                CI: "true",
                BRANCH_NAME: "MR-532",
                BUILD_NUMBER: "4",
                CHANGE_ID: "532",
                CHANGE_BRANCH: "my-change",
                CHANGE_TARGET: "long-term",
                GITLAB_OA_TARGET_BRANCH: "long-term",
                GITLAB_PROJECT_SSH_URL: "git@gitlab.asatnet.net:my-ns/my-project.git",
            ]
            Map iminfo
            iminfo = getIDUDockerImageInfo(
                _dbgComment: "Merge request to merge branch \"my-change\" into branch \"long-term\"",
                namespace: 'my-ns',
                packageName: 'my-project',
                _envP: envmap,
            )

            String str = IDUDockerImageInfoToString(iminfo)

            publishSummary('<pre>\n' + str + '</pre>\n',
                           "Get Docker image info")
        }
        script {
            Map envmap = [
                CI: "true",
                BRANCH_NAME: "long-term",
                BUILD_NUMBER: "210",
                GITLAB_PROJECT_SSH_URL: "git@gitlab.asatnet.net:my-ns/my-project.git",
            ]
            Map iminfo
            iminfo = getIDUDockerImageInfo(
                _dbgComment: "Long term branch \"long-term\"",
                namespace: 'my-ns',
                packageName: 'my-project',
                _envP: envmap,
            )

            String str = IDUDockerImageInfoToString(iminfo)

            publishSummary('<pre>\n' + str + '</pre>\n',
                           "Get Docker image info")
        }
        script {
            Map envmap = [
                CI: "true",
                BRANCH_NAME: "main",
                BRANCH_IS_PRIMARY: "true",
                BUILD_NUMBER: "321",
                GITLAB_PROJECT_SSH_URL: "git@gitlab.asatnet.net:my-ns/my-project.git",
            ]
            Map iminfo
            iminfo = getIDUDockerImageInfo(
                _dbgComment: "Main long-term branch called \"main\" (set as default branch in GitLab)",
                namespace: 'my-ns',
                packageName: 'my-project',
                _envP: envmap,
            )

            String str = IDUDockerImageInfoToString(iminfo)

            publishSummary('<pre>\n' + str + '</pre>\n',
                           "Get Docker image info")
        }
        script {
            Map iminfo
            iminfo = getIDUDockerImageInfo(
                _dbgComment: "This project",
                namespace: 'my-ns-2',
                packageName: 'my-project-2',
            )

            String str = IDUDockerImageInfoToString(iminfo)

            publishSummary('<pre>\n' + str + '</pre>\n',
                           "Get Docker image info")
        }
        script {
            Map envmap = [
                CI: "true",
                BRANCH_NAME: "MR-532",
                BUILD_NUMBER: "4",
                CHANGE_ID: "532",
                CHANGE_BRANCH: "my-change",
                CHANGE_TARGET: "long-term",
                GITLAB_OA_TARGET_BRANCH: "long-term",
                GITLAB_PROJECT_SSH_URL: "git@gitlab.asatnet.net:my-ns/my-project.git",
            ]
            Map iminfo
            iminfo = getIDUDockerImageInfo(
                _dbgComment: "Merge request to merge branch \"my-change\" into branch \"long-term\", with baseVersion",
                namespace: 'my-ns',
                packageName: 'my-project',
                baseVersion: 'bver-20220531',
                branchForBV: 'long-term',
                _envP: envmap,
            )

            String str = IDUDockerImageInfoToString(iminfo)

            publishSummary('<pre>\n' + str + '</pre>\n',
                           "Get Docker image info")
        }
        script {
            Map envmap = [
                CI: "true",
                BRANCH_NAME: "long-term",
                BUILD_NUMBER: "210",
                GITLAB_PROJECT_SSH_URL: "git@gitlab.asatnet.net:my-ns/my-project.git",
            ]
            Map iminfo
            iminfo = getIDUDockerImageInfo(
                _dbgComment: "Long term branch \"long-term\", with baseVersion",
                namespace: 'my-ns',
                packageName: 'my-project',
                baseVersion: 'bver-20220531',
                branchForBV: 'long-term',
                _envP: envmap,
            )

            String str = IDUDockerImageInfoToString(iminfo)

            publishSummary('<pre>\n' + str + '</pre>\n',
                           "Get Docker image info")
        }
        script {
            String commit_1_info_str = getGitCommitInfoStr(commitId: 'df080764493676d5cfd8fae55d336875647e59db')
            String commit_2_info_str = getGitCommitInfoStr()
            String sum_text = ''

            sum_text = sum_text +
                        "<p>Some commit information:</p>\n" +
                        "<pre>" + commit_1_info_str + "</pre>\n"

            sum_text = sum_text +
                        "<p>Last commit information:</p>\n" +
                        "<pre>" + commit_2_info_str + "</pre>\n"

            publishSummary(sum_text, "Last commit information")
        }
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
        library(
          identifier: "custom-lib@${env.GIT_BRANCH}",
          retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "${env.GITLAB_OA_SOURCE_GIT_SSH_URL?env.GITLAB_OA_SOURCE_GIT_SSH_URL:env.GIT_URL}",
            credentialsId: 'efedb983-6b76-42ee-ad7f-ed211b08657a'
          ])
        )
        saveEnvVarsForChangeRequest()

        // Find a related branch for this merge request - BEGIN
        sh "env"
        getRelatedBranch('git@gitlab.asatnet.net:vsat/satnet-dev-sit2.git', 'jenkins-violations-secret')
        sh "env"
        // Find a related branch for this merge request - END

        // Find a related branch for hardcoded inputs - BEGIN
        script {
          if (env.GITLAB_OA_SOURCE_NAMESPACE != null) {
            env.putAt('COPY_OF_SOURCE_NAMESPACE', env.GITLAB_OA_SOURCE_NAMESPACE)
          }
          if (env.GITLAB_OA_SOURCE_BRANCH != null) {
            env.putAt('COPY_OF_SOURCE_BRANCH', env.GITLAB_OA_SOURCE_BRANCH)
          }

          env.putAt('GITLAB_OA_SOURCE_NAMESPACE', 'Evgueni Souleimanov')
          env.putAt('GITLAB_OA_SOURCE_BRANCH', 'z_branch_240')
        }
        sh "env"
        getRelatedBranch('git@gitlab.asatnet.net:vsat/satnet-dev-sit2.git', 'jenkins-violations-secret')
        sh "env"

        script {
          if (env.COPY_OF_SOURCE_NAMESPACE != null) {
            env.putAt('GITLAB_OA_SOURCE_NAMESPACE', env.COPY_OF_SOURCE_NAMESPACE)
          }
          if (env.COPY_OF_SOURCE_BRANCH != null) {
            env.putAt('GITLAB_OA_SOURCE_BRANCH', env.COPY_OF_SOURCE_BRANCH)
          }
        }
        // Find a related branch for hardcoded inputs - END
      }
    }
  }
  post {
    failure {
      echo "======== **Pipeline execution FAILED.** ========"
      withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
        noteToMrGitlab(
          "${env.GITLAB_OA_TARGET_GIT_SSH_URL}",
          "${env.CHANGE_ID}",
          "**Pipeline execution FAILED.**" + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
        )
      }
    }
    unstable {
      echo "======== **Pipeline execution result is UNSTABLE.** ========"
      withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
        noteToMrGitlab(
          "${env.GITLAB_OA_TARGET_GIT_SSH_URL}",
          "${env.CHANGE_ID}",
          "**Pipeline execution result is UNSTABLE.**" + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
        )
      }
    }
    success {
      echo "======== All stages in the pipeline have been completed. ========"
      withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
        noteToMrGitlab(
          "${env.GITLAB_OA_TARGET_GIT_SSH_URL}",
          "${env.CHANGE_ID}",
          "All stages in the pipeline have been completed." + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
        )
      }
    }
    cleanup {
      echo "Deleting all workspace contents after completing the pipeline..."
      doCleanWorkspaceAfterStage(true, false)
    }
  }
}
