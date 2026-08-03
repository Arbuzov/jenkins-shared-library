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
  environment {
    CUSTOM_LIB_CREDENTIALS_ID         = 'efedb983-6b76-42ee-ad7f-ed211b08657a'
    GITLAB_API_CREDENTIALS_ID         = 'gitlab-text-secret'
    TEST_GITLAB_REPO_URL              = 'git@gitlab.asatnet.net:vsat/satnet-dev-sit2.git'
    TEST_CONFLUENCE_SPACE             = 'DEVOPS'
    TEST_CONFLUENCE_ROOT_ID           = '592773125'
    TEST_CONFLUENCE_ROOT_NAME         = 'Sandbox'
    TEST_CONFLUENCE_PAGE_FOR_CONTENT  = '404193285'
    TEST_CONFLUENCE_PAGE_FOR_CHILDREN = '653164558'
    TEST_CONFLUENCE_PAGE_FOR_ATTACH   = '668434500'
    TEST_GIT_COMMIT_SHA               = 'df080764493676d5cfd8fae55d336875647e59db'
    // Dev HUB for the nmsGlobalNotice test (the step swallows failures by design,
    // so an unreachable HUB does not fail this pipeline)
    TEST_NMS_URL                      = 'https://192.168.71.120/nms-ng/'
    // Scratch Jira ticket for the jiraTransitionIssue no-op test ('' = skip the test)
    TEST_JIRA_ISSUE                   = ''
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
            remote: "${env.GITLAB_OA_SOURCE_GIT_SSH_URL ?: env.GIT_URL}",
            credentialsId: env.CUSTOM_LIB_CREDENTIALS_ID
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
          GIT_URL = env.GIT_URL ?: env.GIT_URL_1
        }
        tagToGitlab(GIT_URL, "rolling", env.GITLAB_OA_LAST_COMMIT_ID ?: env.GIT_COMMIT, "rolling tag", "deprecated", false);
        withCredentials([string(credentialsId: env.GITLAB_API_CREDENTIALS_ID, variable: 'gitLabAPISecret')]) {
          tagToGitlab(GIT_URL, "1", env.GITLAB_OA_LAST_COMMIT_ID ?: env.GIT_COMMIT);
          tagToGitlab(
            GIT_URL,
            "${BUILD_NUMBER}",
            env.GITLAB_OA_LAST_COMMIT_ID ?: env.GIT_COMMIT,
            "This is a test tag from build ${BUILD_NUMBER}",
            "## Release ${BUILD_NUMBER}\n\n__Build reference:__ ${BUILD_URL}\n\n__Artifactory:__ ${ARTIFACTORY_UPLOADS[0]}"
          );
        }

        // Housekeeping: the steps above create a fresh `${BUILD_NUMBER}` tag on
        // every run of this test pipeline. Keep only the 3 most recent tags in
        // the current repo to prevent unbounded growth. INTERNAL use only.
        script {
          def cleanup = gitlabCleanupOldTags(keep: 3, gitUrl: GIT_URL)
          echo "gitlabCleanupOldTags: kept=${cleanup.kept}, deleted=${cleanup.deleted}, skipped=${cleanup.skipped}"
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
            final String MR_GITLAB_URL = "git@gitlab.asatnet.net:my-ns/my-project.git"
            final Map ENV_MR = [
                CI: "true",
                BRANCH_NAME: "MR-532",
                BUILD_NUMBER: "4",
                CHANGE_ID: "532",
                CHANGE_BRANCH: "my-change",
                CHANGE_TARGET: "long-term",
                GITLAB_OA_TARGET_BRANCH: "long-term",
                GITLAB_PROJECT_SSH_URL: MR_GITLAB_URL,
            ]
            final Map ENV_LONG_TERM = [
                CI: "true",
                BRANCH_NAME: "long-term",
                BUILD_NUMBER: "210",
                GITLAB_PROJECT_SSH_URL: MR_GITLAB_URL,
            ]
            final Map ENV_MAIN = [
                CI: "true",
                BRANCH_NAME: "main",
                BRANCH_IS_PRIMARY: "true",
                BUILD_NUMBER: "321",
                GITLAB_PROJECT_SSH_URL: MR_GITLAB_URL,
            ]

            List scenarios = [
                [
                    title: 'MR to long-term',
                    args : [
                        _dbgComment: 'Merge request to merge branch "my-change" into branch "long-term"',
                        namespace  : 'my-ns',
                        packageName: 'my-project',
                        _envP      : ENV_MR,
                    ],
                ],
                [
                    title: 'Long-term branch',
                    args : [
                        _dbgComment: 'Long term branch "long-term"',
                        namespace  : 'my-ns',
                        packageName: 'my-project',
                        _envP      : ENV_LONG_TERM,
                    ],
                ],
                [
                    title: 'Main primary branch',
                    args : [
                        _dbgComment: 'Main long-term branch called "main" (set as default branch in GitLab)',
                        namespace  : 'my-ns',
                        packageName: 'my-project',
                        _envP      : ENV_MAIN,
                    ],
                ],
                [
                    title: 'This project (no envmap)',
                    args : [
                        _dbgComment: 'This project',
                        namespace  : 'my-ns-2',
                        packageName: 'my-project-2',
                    ],
                ],
                [
                    title: 'MR to long-term with baseVersion',
                    args : [
                        _dbgComment: 'Merge request to merge branch "my-change" into branch "long-term", with baseVersion',
                        namespace  : 'my-ns',
                        packageName: 'my-project',
                        baseVersion: 'bver-20220531',
                        branchForBV: 'long-term',
                        _envP      : ENV_MR,
                    ],
                ],
                [
                    title: 'Long-term branch with baseVersion',
                    args : [
                        _dbgComment: 'Long term branch "long-term", with baseVersion',
                        namespace  : 'my-ns',
                        packageName: 'my-project',
                        baseVersion: 'bver-20220531',
                        branchForBV: 'long-term',
                        _envP      : ENV_LONG_TERM,
                    ],
                ],
            ]

            Map branches = scenarios.collectEntries { scenario ->
                [(scenario.title): {
                    Map iminfo = getIDUDockerImageInfo(scenario.args)
                    String str = IDUDockerImageInfoToString(iminfo)
                    publishSummary('<pre>\n' + str + '</pre>\n',
                                   "IDU Docker image info: ${scenario.title}")
                }]
            }
            parallel branches
        }
        script {
            String commit_1_info_str = getGitCommitInfoStr(commitId: env.TEST_GIT_COMMIT_SHA)
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
            SPACE     : env.TEST_CONFLUENCE_SPACE,
            ROOT      : env.TEST_CONFLUENCE_ROOT_ID,
            CONTENT   : '<h2>Numeric ID test</h2>',
            DEBUG     : true
          )
          echo "Created Confluence page by ID at: ${urlById}"

          // Creating a page with a root name
          def urlByName = createConfluencePage(
            TITLE     : "Named Root Test 1",
            SPACE     : env.TEST_CONFLUENCE_SPACE,
            ROOT_NAME : env.TEST_CONFLUENCE_ROOT_NAME,
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
              SPACE   : env.TEST_CONFLUENCE_SPACE,
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
          catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE', message: 'Failed to attach single file to Confluence') {
              attachFilesToConfluencePage(
                  PAGE_NAME : 'Numeric ID Test',
                  FILES     : 'test-attachment-1.txt',
                  DEBUG     : true
              )
              echo "Attached single file successfully"
          }

          // Attach multiple files using a mask
          catchError(buildResult: 'UNSTABLE', stageResult: 'UNSTABLE', message: 'Failed to attach multiple files to Confluence') {
              def files = findFiles(glob: 'test-*.txt')
              if (files) {
                  attachFilesToConfluencePage(
                      SPACE     : env.TEST_CONFLUENCE_SPACE,
                      PAGE_NAME : 'Named Root Test',
                      FILES     : files.collect { it.path },
                      DEBUG     : true
                  )
                  echo "Attached multiple files successfully"
              } else {
                  echo "No matching files found for mask"
              }
          }

          // Non-existent file case (expected to fail — keep try/catch to assert the failure)
          try {
            attachFilesToConfluencePage(
              ID    : env.TEST_CONFLUENCE_PAGE_FOR_ATTACH,
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
            TITLE     : "Single Attachment Test",
            SPACE     : env.TEST_CONFLUENCE_SPACE,
            ROOT_NAME : env.TEST_CONFLUENCE_ROOT_NAME,
            CONTENT   : '<h2>Single attach test</h2>',
            FILES     : 'attach-one.txt',
            DEBUG     : true
          )
          echo "Created page with single file at: ${urlSingle}\n"

          // Create page with multiple attachments via mask
          def multiFiles = findFiles(glob: 'attach-*.txt').collect{it.toString()}
          def urlMulti = createConfluencePage(
            TITLE     : "Multiple Attachment Test",
            SPACE     : env.TEST_CONFLUENCE_SPACE,
            ROOT_NAME : env.TEST_CONFLUENCE_ROOT_NAME,
            CONTENT   : '<h2>Multiple attach test</h2>',
            FILES     : multiFiles,
            DEBUG     : true
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
            TITLE    : "Release test1",
            SPACE    : env.TEST_CONFLUENCE_SPACE,
            ROOT_NAME: env.TEST_CONFLUENCE_ROOT_NAME,
                CONTENT: inflateTemplate(
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>([]), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "1",
                  projectId: 'DEMO',
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/1/application-plugin-1.tar.gz",
                  build: null
                ),
                FILES: (findFiles(glob: '**/*.syft.txt') + findFiles(glob: '**/*.grype.txt')).collect { it.path }
              )
          echo "Confluence page created: ${url}"


          def url2 = createConfluencePage(
                TITLE    : "Release test22",
                SPACE    : env.TEST_CONFLUENCE_SPACE,
                ROOT_NAME: env.TEST_CONFLUENCE_ROOT_NAME,
                CONTENT: inflateTemplate(
                  TEMPLATE: libraryResource('com/spacebridge/confluence/application-plugin.release.gtpl'),
                  date: new Date().format('yyyy-MM-dd'),
                  images: arrayToString(new ArrayList<>([]), ["<li>", "</li>"], ["<ul>", "</ul>"]),
                  version: "1",
                  projectId: 'DEMO',
                  pluginUrl: "https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/application-plugin/1/application-plugin-1.tar.gz",
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
          def pageId = env.TEST_CONFLUENCE_PAGE_FOR_CONTENT
          def page = getConfluencePageContent(PAGE_ID: pageId)
          // trim to 128 symbols
          def content = page.content.substring(0, 128) + '...'
          def title = page.title
          echo "Page ID ${pageId}, title: ${title}, content: ${content}"
        }

        script {
          echo "Testing getConfluencePageChildren()..."
          def pageId = env.TEST_CONFLUENCE_PAGE_FOR_CHILDREN
          def children = getConfluencePageChildren(PAGE_ID: pageId)
          def childrenStr = children.collect { it.toString() }.join(', ')
          echo "Page ID ${pageId}, children: ${childrenStr}"
        }

        script {
          echo "Testing nmsGlobalNotice() (set -> remove on the dev HUB)..."
          def setOk = nmsGlobalNotice(
            nmsUrl: env.TEST_NMS_URL,
            action: 'set',
            text: "Shared-library test (build ${env.BUILD_NUMBER}) — ignore, removed automatically.",
            debug: true
          )
          def removeOk = nmsGlobalNotice(nmsUrl: env.TEST_NMS_URL, action: 'remove', debug: true)
          echo "nmsGlobalNotice: set=${setOk}, remove=${removeOk}"
          if (setOk && !removeOk) {
            error("nmsGlobalNotice: set succeeded but remove failed — a test notice may be left on ${env.TEST_NMS_URL}")
          }
          if (!setOk) {
            echo "WARNING: could not set the notice on ${env.TEST_NMS_URL} — HUB unreachable from this agent?"
          }
        }

        script {
          // No-op transition: target the issue's CURRENT status — exercises the
          // fetch/transition-lookup/idempotency paths without changing the ticket
          if (env.TEST_JIRA_ISSUE) {
            echo "Testing jiraTransitionIssue() (no-op transition on ${env.TEST_JIRA_ISSUE})..."
            def currentStatus = getJiraIssueInfo(env.TEST_JIRA_ISSUE).fields.status.name
            def result = jiraTransitionIssue(
              issueKey: env.TEST_JIRA_ISSUE,
              targetStatus: currentStatus,
              DEBUG: true
            )
            echo "jiraTransitionIssue: ${result}"
            if (result.transitioned) {
              error("jiraTransitionIssue must be a no-op when the issue is already in '${currentStatus}'")
            }
          } else {
            echo 'TEST_JIRA_ISSUE is not set — skipping the jiraTransitionIssue test'
          }
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
            remote: "${env.GITLAB_OA_SOURCE_GIT_SSH_URL ?: env.GIT_URL}",
            credentialsId: env.CUSTOM_LIB_CREDENTIALS_ID
          ])
        )
        gitlabSetMrApproval(approve: false)

        saveEnvVarsForChangeRequest()
        noteToMrGitlab(
          comment: "**This is a test note from Jenkins new notation.**" + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
        )
        // Find a related branch for this merge request - BEGIN
        sh "env"
        getRelatedBranch(env.TEST_GITLAB_REPO_URL, env.GITLAB_API_CREDENTIALS_ID)
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
        getRelatedBranch(env.TEST_GITLAB_REPO_URL, env.GITLAB_API_CREDENTIALS_ID)
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

        script {
          echo "Testing upsertNoteToMrGitlab() (create -> recreate -> onlyUpdate)..."
          def marker = '<!-- shared-lib-test:upsert -->'
          def firstId = upsertNoteToMrGitlab(
            marker: marker,
            comment: "**Upsert test** — written by build ${env.BUILD_NUMBER}.",
            debug: true
          )
          def secondId = upsertNoteToMrGitlab(
            marker: marker,
            comment: "**Upsert test** — recreated by build ${env.BUILD_NUMBER}.",
            debug: true
          )
          echo "upsertNoteToMrGitlab: first=${firstId}, second=${secondId}"
          if (firstId == null || secondId == null || secondId == firstId) {
            error('upsertNoteToMrGitlab must delete the old note and post a NEW one (fresh id) on the second call')
          }
          // onlyUpdate with an unknown marker must not create anything
          def ghostId = upsertNoteToMrGitlab(
            marker: "<!-- shared-lib-test:no-such-marker-${env.BUILD_NUMBER} -->",
            onlyUpdate: true,
            comment: 'must not be posted',
            debug: true
          )
          if (ghostId != null) {
            error('upsertNoteToMrGitlab(onlyUpdate: true) must not create a new note')
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
      withCredentials([string(credentialsId: env.GITLAB_API_CREDENTIALS_ID, variable: 'gitLabAPISecret')]) {
        noteToMrGitlab(
          "${env.GITLAB_OA_TARGET_GIT_SSH_URL}",
          "${env.CHANGE_ID}",
          "**Pipeline execution FAILED.**" + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
        )
      }
    }
    unstable {
      echo "======== **Pipeline execution result is UNSTABLE.** ========"
      withCredentials([string(credentialsId: env.GITLAB_API_CREDENTIALS_ID, variable: 'gitLabAPISecret')]) {
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
