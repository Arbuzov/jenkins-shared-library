def call(Map args) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)

    Map iminfo = [:]
    // Debug: Keep workspace in case of failed build, do not remove it
    boolean debugKeepWsWhenFailure = false

    pipeline {
        agent {
            label 'farmer-docker-ubuntu'
        }
        options {
            buildDiscarder(logRotator(daysToKeepStr: '183', artifactDaysToKeepStr: '183'))
            disableConcurrentBuilds()
            timestamps()
        }
        stages {
            stage('Prepare') {
                steps {
                    doCleanGitWorkTree()
                    script {
                        iminfo = getIDUDockerImageInfo(args)
                        currentBuild.displayName = iminfo['tag']
                    }
                    sh '''env -0 | LC_ALL=C.UTF-8 sort -z | tr '\\000' '\\012' '''
                }
            }
            stage('Run SonarScanner') {
                steps {
                    // FIXME: TODO: Add Sonar scanner step
                    echo "Add Run SonarScanner steps"
                }
            }
            stage('Create image') {
                steps {
                    script {
                        docker.withRegistry(iminfo['registryUrl'], iminfo['credentialsId']) {
                            String buildArgs = ""

                            if (args['baseVersion']) {
                                buildArgs = buildArgs + " --build-arg \"BASE_VERSION=${args['baseVersion']}\""
                            }
                            for (xTag in iminfo["prevTags"]) {
                                try {
                                    echo "Trying to pull image ${iminfo['imageID']}:${xTag}..."
                                    def oldImage = docker.image("${iminfo['imageID']}:${xTag}")
                                    oldImage.pull()
                                } catch (Exception e) {
                                    echo "Could not pull image (${e.message}), continuing execution..."
                                }
                            }

                            def image = docker.build("${iminfo['imageID']}:${iminfo['tag']}",
                                                     "${buildArgs} \"${WORKSPACE}\"")
                            image.push()
                            for (xTag in iminfo["extraTags"]) {
                                image.push(xTag)
                            }
                        }
                        for (xTag in iminfo["prevTags"]) {
                            sh """docker image remove "${iminfo['imageID']}:${xTag}" || echo 'This is not fatal' >&2 """
                        }
                        sh """docker image remove "${iminfo['imageID']}:latest" || echo 'This is not fatal' >&2 """
                    }
                }
            }
            stage('Inspect image') {
                steps {
                    // FIXME: TODO: Add steps
                    echo "Add Inspect image steps"
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
