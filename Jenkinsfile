pipeline {
  agent {
    label 'master'
  }
  options {
    timestamps()
    buildDiscarder(
      logRotator(
        daysToKeepStr: "183",
        artifactDaysToKeepStr: "183"
      )
    )
    ansiColor('xterm')
    preserveStashes(buildCount: 5)
    disableConcurrentBuilds()
    skipStagesAfterUnstable()
    timeout(time: 24, unit: 'HOURS')
    newContainerPerStage()
  }
  stages {
    stage('Unit Tests') {
      agent {
        docker {
          image 'docker.nexus.asatnet.net/maven:3.9.6-eclipse-temurin-11'
          args '-v /tmp:/tmp'
          registryUrl 'https://docker.nexus.asatnet.net/'
        }
      }
      steps {
        withMaven(
          options: [
            artifactsPublisher(),
            dependenciesFingerprintPublisher(includeReleaseVersions: true),
            pipelineGraphPublisher(),
            mavenLinkerPublisher()
          ],
          traceability: true
        ) {
            sh '$MVN_CMD clean test site'
        }
        junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
        publishHTML(
          allowMissing: true,
          alwaysLinkToLastBuild: true,
          keepAll: false,
          reportDir: 'target/site',
          reportFiles: 'index.html',
          reportName: 'Site'
        )
      }
    }
    stage('Test steps') {
      steps {
        script {
        def lib = library(
          identifier: "custom-lib@${env.GIT_BRANCH}",
          retriever: modernSCM([
            $class: 'GitSCMSource',
            remote: "${env.GITLAB_OA_SOURCE_GIT_SSH_URL?env.GITLAB_OA_SOURCE_GIT_SSH_URL:env.GIT_URL}",
            credentialsId: 'efedb983-6b76-42ee-ad7f-ed211b08657a'
          ])
        )

          // NOTE: publish under directory debug/firmware in Nexus
          env.putAt('PIPELINE_BIN_REPO_FULL_PATH', getIDURepoPath(namespace: 'debug',
                                                                  project: 'firmware',
                                                                  use_commit_id: false))
        print(getNetboxTenants())
        echo "==="
        // print getConfluenceComment(COMMENT_ID: '717062832', DEBUG: true)
        // print addConfluenceComment(
        //   PAGE_ID: '717062826',
        //   DEBUG: true,
        //   COMMENT: "AI review started for PRD. Attachments have been downloaded and will be included in the review context."
        // )
        print(lib.com.spacebridge.NetBox.getTenants())
        // print(
        //   createConfluencePageChain(
        //     SPACE: 'DEVOPS',
        //     ROOT_NAME: 'Debug',
        //     CHILDREN: [
        //       [title: "Child Page 1 from chain ${env.BUILD_NUMBER}", content: "<h2>Content for Child Page 1</h2>", label: "automation"],
        //       [title: "Child Page 2 from chain ${env.BUILD_NUMBER}", content: "<h2>Content for Child Page 2</h2>", label: "automation"],
        //       [title: "Child Page 3 from chain ${env.BUILD_NUMBER}", content: "<h2>Content for Child Page 3</h2>", label: "automation"]
        //     ],
        //     DEBUG: true,
        //     FLATTEN: false
        //   )
        // )
        println(getJenkinsBuildChanges(
          PROJECT_ID:'MCD4_Software/MCD4%2FSoftware%2FMCD4_Lite/master', HEADER:'4', BUILD_NUMBER:'25', DEBUG:true
        ))
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
        //     TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
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
        }

        print("PIPELINE_BIN_REPO_FULL_PATH = " + env.getAt('PIPELINE_BIN_REPO_FULL_PATH'))
        echo (getBuildCause().toString())

        print (getBuildCause().findAll{it.equals("io.jenkins.plugins.gitlabbranchsource.GitLabWebHookCause")})

        // This call to "requireCleanGitWorkTree()" is at this point since 2022-03-11.
        requireCleanGitWorkTree()
        // Run "doCleanGitWorkTree()" after "requireCleanGitWorkTree()" to make sure
        // that "doCleanGitWorkTree()" runs correctly.
        doCleanGitWorkTree()
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
        tagToGitlab(GIT_URL, "rolling", env.GITLAB_OA_LAST_COMMIT_ID ?: env.GIT_COMMIT, "rolling tag", "deprecated", false);
        withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
          tagToGitlab(GIT_URL, "1", env.GITLAB_OA_LAST_COMMIT_ID ?: env.GIT_COMMIT);
          tagToGitlab(
            GIT_URL,
            "${BUILD_NUMBER}",
            env.GITLAB_OA_LAST_COMMIT_ID ?: env.GIT_COMMIT,
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

        //print mergeRequestsChangeLog('gitlab-text-secret')

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

        script {
          echo "Testing Confluence page creation..."
          print(inflateTemplate(
            TEMPLATE: readFile(file: "resources/com/spacebridge/confluence/table.gtpl"),
            values: [
              ['Header 1', 'Header 2', 'Header 3'],
              ['Row 1 Col 1', 'Row 1 Col 2', 'Row 1 Col 3'],
              ['Row 2 Col 1', 'Row 2 Col 2', 'Row 2 Col 3']
            ],
            renderHeader: true
          ))
          print(inflateTemplate(
            TEMPLATE: readFile(file: "resources/com/spacebridge/confluence/table.gtpl"),
            values: [
              ['Header 1', 'Header 2', 'Header 3'],
              ['Row 1 Col 1', 'Row 1 Col 2', 'Row 1 Col 3'],
              ['Row 2 Col 1', 'Row 2 Col 2', 'Row 2 Col 3']
            ]
          ))
          // Creating a page with a numeric ID (existing behavior)
          def urlById = createConfluencePage(
            TITLE     : "Numeric ID Test 1",
            SPACE     : 'DEVOPS',
            ROOT      : '592773125',
            CONTENT   : '<h2>Numeric ID test</h2>',
            DEBUG     : true
          )
          echo "Created Confluence page by ID at: ${urlById}"

          // Creating a page with a root name
          def urlByName = createConfluencePage(
            TITLE     : "Named Root Test 1",
            SPACE     : 'DEVOPS',
            ROOT_NAME : 'Debug',
            CONTENT   : '<h2>Named root test</h2>',
            DEBUG     : true
          )
          echo "Created Confluence page by name at: ${urlByName}"

          // Test updating of the page
          def pageId = getConfluencePageId(url: urlByName)
          updateConfluencePage(
            PAGE_ID : pageId,
            CONTENT : '<h2>Named root test, updated</h2>',
            DEBUG   : true
          )

          // Missing root (should fail)
          try {
            createConfluencePage(
              TITLE   : "No Root Test 1",
              SPACE   : 'DEVOPS',
              CONTENT : '<h2>No root provided</h2>',
              DEBUG   : true
            )
          } catch (Exception e) {
            echo "Correctly failed with no root: ${e.message}"
          }
        }

        script {
          echo "attaching files to confluence..."

          writeFile file: 'test-attachment.txt', text: 'This is a test file for attachment'
          writeFile file: 'test-attachment-1.txt', text: 'This is the first test file'
          writeFile file: 'test-attachment-2.txt', text: 'This is the second test file'
          writeFile file: 'test-attachment-3.txt', text: 'This is the third test file'


          // Attach a single file (normal case)
          try {
              attachFilesToConfluencePage(
                  PAGE_NAME    : 'Numeric ID Test',
                  FILES : 'test-attachment-1.txt',
                  DEBUG : true
              )
              echo "Attached single file successfully"
          } catch (Exception e) {
              echo "Failed to attach single file: ${e.message}"
          }

          // Attach multiple files using a mask
          try {
              def files = findFiles(glob: 'test-*.txt')
              if (files) {
                  attachFilesToConfluencePage(
                      SPACE : 'DEVOPS',
                      PAGE_NAME    : 'Named Root Test',
                      FILES : files.collect { it.path },
                      DEBUG : true
                  )
                  echo "Attached multiple files successfully"
              } else {
                  echo "No matching files found for mask"
              }
          } catch (Exception e) {
              echo "Failed to attach multiple files: ${e.message}"
          }

          // Non-existent file case
          try {
            attachFilesToConfluencePage(
              ID    : '668434500',
              FILES : 'nonexistent-file.txt',
              DEBUG : true
            )
          } catch (Exception e) {
            echo "Correctly failed for missing file: ${e.message}"
          }
        }

        script {
          echo "\nCreating Report with files..."

          // Prepare attachment files
          writeFile file: 'attach-one.txt', text: 'Attach one file'
          writeFile file: 'attach-a.txt',   text: 'Attach A'
          writeFile file: 'attach-b.txt',   text: 'Attach B'

          // Create page with a single attachment
          def urlSingle = createConfluencePage(
            TITLE   : "Single Attachment Test",
            SPACE   : 'DEVOPS',
            ROOT_NAME    : 'Debug',
            CONTENT : '<h2>Single attach test</h2>',
            FILES   : 'attach-one.txt',
            DEBUG   : true
          )
          echo "Created page with single file at: ${urlSingle}\n"

          // Create page with multiple attachments via mask
          def multiFiles = findFiles(glob: 'attach-*.txt').collect{it.toString()}
          def urlMulti = createConfluencePage(
            TITLE   : "Multiple Attachment Test",
            SPACE   : 'DEVOPS',
            ROOT_NAME    : 'Debug',
            CONTENT : '<h2>Multiple attach test</h2>',
            FILES   : multiFiles,
            DEBUG   : true
          )
          echo "Created page with multiple files at: ${urlMulti}\n"
        }

        script {
          echo "Test report attachment"

          writeFile file: "test.syft.txt", text: "package-a 1.0.0\npackage-b 2.0.0"
          writeFile file: "test.grype.txt", text: "Critical vuln CVE-1234\nHigh vuln CVE-5678"


          def syft  = findFiles(glob: '**/*.syft.txt')
          def grype = findFiles(glob: '**/*.grype.txt')

          def filesToAttach = (syft + grype).collect { it.path }

          echo "Files to attach: ${filesToAttach}"

          def url = createConfluencePage(
            TITLE: "Release test1",
                SPACE    : 'DEVOPS',
                ROOT_NAME: 'Debug',
                CONTENT: inflateTemplate(
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>([]), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "1",
                  projectId: 'DEMO',
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/D}/.tar.gz",
                  build: null
                ),
                FILES: (findFiles(glob: '**/*.syft.txt') + findFiles(glob: '**/*.grype.txt')).collect { it.path }
              )
          echo "Confluence page created: ${url}"


          def url2 = createConfluencePage(
                TITLE: "Release test22",
                SPACE    : 'DEVOPS',
                ROOT_NAME: 'Debug',
                CONTENT: inflateTemplate(
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>([]), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "1",
                  projectId: 'DEMO',
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/D}/.tar.gz",
                  boms: loadReports (mask: '*.syft.txt'),
                  vulnerability: loadReports (mask: "*.grype.txt"),
                  build: null
                )
          )
          echo "Confluence page created: ${url2}"

          sh 'rm -f test.syft.txt test.grype.txt'

        }
    
        script {
          echo "Testing getConfluencePageContent()..."
          def pageId = '404193285'
          def page = getConfluencePageContent(PAGE_ID: pageId)
          // trim to 128 symbols
          def content = page.content.substring(0, 128) + '...'
          def title = page.title
          echo "Page ID ${pageId}, title: ${title}, content: ${content}"
        }

        script {
          echo "Testing getConfluencePageChildren()..."
          def pageId = '653164558'
          def children = getConfluencePageChildren(PAGE_ID: pageId)
          def childrenStr = children.collect { it.toString() }.join(', ')
          echo "Page ID ${pageId}, children: ${childrenStr}"
        }
      }

      post {
        cleanup {
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
        gitlabSetMrApproval(approve: false)

        saveEnvVarsForChangeRequest()
        noteToMrGitlab(
          comment: "**This is a test note from Jenkins new notation.**" + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
        )
        // Find a related branch for this merge request - BEGIN
        sh "env"
        getRelatedBranch('git@gitlab.asatnet.net:vsat/satnet-dev-sit2.git', 'gitlab-text-secret')
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
        getRelatedBranch('git@gitlab.asatnet.net:vsat/satnet-dev-sit2.git', 'gitlab-text-secret')
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

        script {
          def mr = gitlabFindMR()
          if (mr != null) {
            echo "gitlabFindMR(): MR found, IID: ${mr.iid}, title: ${mr.title}"
          } else {
            echo "gitlabFindMR(): MR not found"
          }
        }

        script {
          def jiraIssue = guessJiraIssueInfo()
          if (jiraIssue) {
            echo "guessJiraIssueInfo(): Jira issue found, key: ${jiraIssue.key}, summary: ${jiraIssue.fields.summary}"
          } else {
            echo "guessJiraIssueInfo(): Jira issue not found"
          }
        }
      }
      post {
        success {
          gitlabSetMrApproval()
        }
      }
    }
    stage ('Run SonarQube check') {
      agent {
        docker {
          image 'registry.nexus.asatnet.net/base/sonar-scanner-cli:latest'
        }
      }
      steps {
        launchSonarQube(DEBUG: true)
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
      noteToMrGitlab(
        comment: "All stages in the pipeline have been completed." + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
      )
    }
    cleanup {
      echo "Deleting all workspace contents after completing the pipeline..."
      doCleanWorkspaceAfterStage(true, false)
    }
  }
}
