// runIDUDockerBuild(): return a pipeline to build a Docker image in the current project
//
// Used inside the Jenkinsfile to return a pipeline to build a Docker
// image in the current project.
//
// Arguments in argm:
//
// namespace: top-level namespace (required, lowercase)
// packageName: package name (required, lowercase)
// baseVersion: version of the top level image (optional)
//              Forwarded as --build-arg "BASE_VERSION=${baseVersion}"
//              when building the Docker image from Dockerfile
// branchForBV: branch which builds this baseVersion (optional).
//              If argm.branchForBV is specified, and if the source
//              or target branch name matches ${argm.branchForBV},
//              the tag prefix is set to ${iminfo.baseVersion} and
//              not the current branch name
// runGrype:    run grype on image (optional, default: true)
//              Disable on very large images (> 1 GB)
//
// Returns: a pipeline to build a Docker image in the current project
//
// Usage example in Jenkinsfile:
//
//      @Library('asatnet@master')_
//
//      runIDUDockerBuild(
//          namespace: 'my-ns',
//          packageName: 'my-project',
//          baseVersion: 'bver-20220531',
//          branchForBV: 'master',
//      )
//
// See also: getIDUDockerImageInfo()
//

def call(Map argm) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)

    Map iminfo = [:]
    String usageCmds = ""
    String commentSignatureStr = ""
    // Debug: Keep workspace in case of failed build, do not remove it
    boolean debugKeepWsWhenFailure = false
    String reportsAntPatterns = '' +
        "report-*-grype.txt, " +
        "report-*-syft.txt, " +
        ""
    boolean runGrype = false

    pipeline {
        agent {
            label 'farmer-docker-ubuntu'
        }
        options {
            buildDiscarder(logRotator(artifactDaysToKeepStr: '183', artifactNumToKeepStr: '10',
                                      daysToKeepStr: '3653', numToKeepStr: '100000'))
            disableConcurrentBuilds()
            disableResume()
            timestamps()
        }
        stages {
            stage('Prepare') {
                steps {
                    sh '''env -0 | LC_ALL=C.UTF-8 sort -z | tr '\\000' '\\012' '''
                    // Clean the work space before running SonarScanner
                    doCleanGitWorkTree()
                    script {
                        iminfo = getIDUDockerImageInfo(argm)
                        currentBuild.displayName = iminfo.tag
                        commentSignatureStr = getJenkinsCommentSignatureStr()
                        runGrype = ((argm.containsKey('runGrype')) ? argm.runGrype : false)
                    }
                }
            }
            stage('Run SonarScanner') {
                agent {
                    docker {
                        image 'sonarsource/sonar-scanner-cli'
                        // Use the same node and workspace and do not create workspace@1 directory
                        reuseNode true
                    }
                }
                steps {
                    script {
                        if (env.CHANGE_ID) {
                            env.putAt('SONAR_PULLREQUEST_KEY', env.CHANGE_ID)
                            env.putAt('SONAR_PULLREQUEST_BRANCH', env.CHANGE_BRANCH)
                            env.putAt('SONAR_PULLREQUEST_BASE', env.GIT_BRANCH)
                        }
                    }
                    sh '''env -0 | LC_ALL=C.UTF-8 sort -z | tr '\\000' '\\012' '''
                    withSonarQubeEnv('SonarQube Server') {
                        sh """sonar-scanner"""
                    }
                    sh '''rm -f report-task.txt report-*.txt'''
                }
            }
            stage('Create image') {
                steps {
                    // Clean the work space after running SonarScanner
                    doCleanGitWorkTree()
                    script {
                        docker.withRegistry(iminfo.registryUrl, iminfo.credentialsId) {
                            String buildArgs = ""

                            if (argm.baseVersion) {
                                buildArgs = buildArgs + " --build-arg \"BASE_VERSION=${argm.baseVersion}\""
                            }
                            for (xTag in iminfo.prevTags) {
                                try {
                                    echo "Trying to pull image ${iminfo.imageID}:${xTag}..."
                                    def oldImage = docker.image("${iminfo.imageID}:${xTag}")
                                    oldImage.pull()
                                } catch (Exception e) {
                                    echo "Could not pull image (${e.message}), continuing execution..."
                                }
                            }

                            def image = docker.build("${iminfo.imageID}:${iminfo.tag}",
                                                     "${buildArgs} \"${env.WORKSPACE}\"")
                            image.push()
                            usageCmds = usageCmds + "docker pull ${iminfo.registryHost}/${iminfo.imageID}:${iminfo.tag}\n"
                            if (iminfo.extraTags) {
                                usageCmds = usageCmds + "\n"
                                for (xTag in iminfo.extraTags) {
                                    image.push(xTag)
                                    usageCmds = usageCmds + "docker pull ${iminfo.registryHost}/${iminfo.imageID}:${xTag}\n"
                                }
                            }
                        }

                        publishSummary('<pre>' + usageCmds + '</pre>\n',
                                       'Image usage')

                        if (env.CHANGE_ID) {
                            withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
                                noteToMrGitlab("${env.GITLAB_OA_TARGET_GIT_SSH_URL}", "${env.CHANGE_ID}",
                                               "Built the image.\n" +
                                               "\n" +
                                               "Image usage:\n" +
                                               "\n" +
                                               "```\n" +
                                               usageCmds +
                                               "```\n" +
                                               "\n" +
                                               commentSignatureStr +
                                               "\n")
                            }
                        }

                        // for (xTag in iminfo.prevTags) {
                        //     sh """docker image remove "${iminfo.imageID}:${xTag}" || echo 'This is not fatal' >&2 """
                        // }
                        // sh """docker image remove "${iminfo.imageID}:latest" || echo 'This is not fatal' >&2 """
                    }
                }
                post {
                    cleanup {
                        // Clean the work space before Inspect image stage
                        doCleanGitWorkTree()
                    }
                }
            }
            stage('Inspect image') {
                agent {
                    docker {
                        image 'registry.nexus.asatnet.net/spacebridge/dind-anchore:1.0.0-b2'
                        args '--privileged'
                        // Use the same node and workspace and do not create workspace@1 directory
                        reuseNode true
                    }
                }
                steps {
                    script {
                        sh """syft ${iminfo.registryHost}/${iminfo.imageID}:${iminfo.tag} >"report-${iminfo.packageName}-${iminfo.tag}-syft.txt" """
                        if (runGrype) {
                            sh """grype ${iminfo.registryHost}/${iminfo.imageID}:${iminfo.tag} >"report-${iminfo.packageName}-${iminfo.tag}-grype.txt" """
                        }
                        archiveArtifacts artifacts: reportsAntPatterns
                        sh """rm -f "report-${iminfo.packageName}-${iminfo.tag}-grype.txt" "report-${iminfo.packageName}-${iminfo.tag}-syft.txt" """
                    }
                }
            }
        }
        post {
            cleanup {
                echo "Deleting all workspace contents after completing the pipeline..."
                doCleanWorkspaceAfterStage(true, debugKeepWsWhenFailure)
            }
        }
    }
}
