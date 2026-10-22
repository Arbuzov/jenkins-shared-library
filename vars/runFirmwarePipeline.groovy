import groovy.transform.Field

/**
 * End-to-end SBC firmware pipeline: build the ISO and firmware, publish them to
 * Nexus, and write the release notes. It builds what is committed: component and
 * firmware versions are picked by runFirmwareConfigurator, whose commit to
 * Manifest/VERSION starts this build.
 *
 * Everything that differs between firmware repositories lives in a per-branch
 * config file (jenkins.yml by default), so the Jenkinsfile of each repository is
 * a two-line stub:
 *
 *     @Library('asatnet@master')_
 *     runFirmwarePipeline()
 *
 * The config is read with readTrusted, which resolves from SCM before a
 * workspace exists - that is what lets the values be used while the pipeline is
 * still being defined. It therefore only works in multibranch jobs.
 *
 * See vars/runFirmwarePipeline.txt for the config format and parameters.
 */

// A vars script is a singleton per build, so these hold state for the whole run.
@Field Map CFG = [:]
@Field String GROUP_PATH = ''
@Field String VERSION = ''

/** Config keys every repository has to provide. */
@Field List REQUIRED_CONFIG = [
    'project', 'jiraProject', 'mavenGroup',
    'releaseNotesSpace', 'releaseNotesRoot',
    'installationSpace', 'installationRoot',
    'keyComponents'
]

/**
 * Validate the config and return it. Reports every missing key at once rather
 * than failing on the first one, because these errors surface at runtime.
 */
Map validateConfig(Map config) {
    if (!config) {
        error('runFirmwarePipeline: the config file is empty or could not be parsed.')
    }
    def missing = REQUIRED_CONFIG.findAll { !config.containsKey(it) }
    if (missing) {
        error("runFirmwarePipeline: the config is missing ${missing.join(', ')}.")
    }
    if (!(config.keyComponents instanceof Map) || !config.keyComponents) {
        error('runFirmwarePipeline: keyComponents must be a non-empty map of component name to { project, buildMask }.')
    }
    def malformed = config.keyComponents.findAll { name, component ->
        !(component instanceof Map) || !component.project || !component.buildMask
    }
    if (malformed) {
        error("runFirmwarePipeline: keyComponents entries need project and buildMask: ${malformed.keySet().join(', ')}.")
    }
    return config
}

/**
 * Build records are cheap and are what a release audit reads; ISOs are not, and every
 * release build is published to Nexus anyway. Merge request builds keep neither.
 */
Map buildRetention() {
    return env.CHANGE_ID ? [builds: '3', artifacts: '3'] : [builds: '30', artifacts: '10']
}

def parsePackageListFromUrl(url) {
    def packages = [:]
    try {
        def content = new URL(url).text
        content.split('\n').each { line ->
            def parts = line.tokenize()
            if (parts.size() > 2) {
                packages[parts[0]] = parts[1]
            }
        }
    } catch (Exception e) {
        echo "Failed to retrieve or parse artifact from URL: ${url}"
    }
    return packages
}

def parsePackageListFromFile(filePath) {
    def packages = [:]
    try {
        def content = loadReport(filePath)
        content.split('\n').each { line ->
            def parts = line.tokenize()
            if (parts.size() > 2) {
                packages[parts[0]] = parts[1]
            }
        }
    } catch (Exception e) {
        echo "Failed to read or parse artifact from file: ${filePath}"
    }
    return packages
}

def computeDifferences(previousPackages, currentPackages) {
    def added = currentPackages.findAll { name, version ->
        !previousPackages.containsKey(name)
    }
    def removed = previousPackages.findAll { name, version ->
        !currentPackages.containsKey(name)
    }
    def updated = currentPackages.findAll { name, version ->
        previousPackages.containsKey(name) && previousPackages[name] != version
    }

    return [added: added, removed: removed, updated: updated]
}

String loadReport(mask) {
    return readFile(
        findFiles(glob: mask)[0].path
    )
}

String secureString(string) {
    return string.replaceAll(
        /(\r\n|\r|\n)/,
        '\\\\n'
    )
}

/** Per-component changelog for the release notes. */
List getKeyComponentsInfo() {
    def componentLines = readFile('artifacts-sbc/syft_report.txt').readLines()
    def result = []
    CFG.keyComponents.each { component, details ->
        def componentInfo = ''
        def lineWithVersion = componentLines.find { it.contains(component) }
        def changesInfo = false
        def version = 'unknown'
        if (lineWithVersion) {
            def versionData = lineWithVersion.split("\\s+")
            if (versionData.size() > 1) {
                version = versionData[1]
            }
            componentInfo += "<h3>Component: ${component}&nbsp;${version}</h3>"
            def matcher = (lineWithVersion =~ details['buildMask'])
            if (matcher.find()) {
                changesInfo = getJenkinsBuildChanges(
                    PROJECT_ID: details['project'],
                    HEADER: '4',
                    BUILD_NUMBER: matcher.group(),
                    DEBUG: false
                )
            }
        }
        componentInfo += changesInfo ?: "Build for ${version} not found"
        result.add(componentInfo)
    }
    return result
}

/** Version part of the Nexus coordinates, e.g. estella-hd-1.1.2-b152 -> 1.1.2-b152 */
String artifactVersion() { VERSION.replace(CFG.project + '-', '') }

String releaseUrl(String extension) {
    return "https://nexus.asatnet.net/repository/maven-releases/${GROUP_PATH}/${CFG.project}" +
           "/${artifactVersion()}/${CFG.project}-${artifactVersion()}.${extension}"
}

List firmwareArtifacts() {
    return [
        [artifactId: CFG.project, file: findFiles(glob: 'artifacts-sbc/iso/*.iso')[0].path, type: 'iso'],
        [artifactId: CFG.project, file: findFiles(glob: 'artifacts-sbc/firmware/*.fw')[0].path, type: 'fw'],
        [artifactId: CFG.project, file: 'artifacts-sbc/syft_report.txt', type: 'syft.txt']
    ]
}

def call(Map arg = [:]) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)

    String configFile = arg.CONFIG ?: 'jenkins.yml'
    String buildImage = arg.BUILD_IMAGE ?: 'registry.nexus.asatnet.net/spacebridge/firmware-build:bullseye-b1'
    String buildArgs = arg.BUILD_ARGS ?: '--privileged -v /srv/docker/dual-image:/home/dual-image/'
    String agentLabel = arg.AGENT_LABEL ?: 'farmer-docker-ubuntu'
    String nexusCredentialsId = arg.NEXUS_CREDENTIALS_ID ?: 'nexus-publisher'
    Map retention = buildRetention()

    CFG = validateConfig(readYaml(text: readTrusted(configFile)))
    GROUP_PATH = CFG.mavenGroup.replace('.', '/')

    pipeline {
        agent {
            label agentLabel
        }

        environment {
            currentFolder = sh(script: 'pwd', returnStdout: true)
            TEST_SBC_IP = "${CFG.testSbcIp ?: ''}"
            GREEN_COLOR = '\033[42m'
            RED_COLOR = '\033[41m'
            CLOSE_COLOR = '\033[0m'
        }

        options {
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: retention.builds, artifactNumToKeepStr: retention.artifacts))
            disableConcurrentBuilds(abortPrevious: true)
            newContainerPerStage()
            ansiColor('xterm')
        }

        stages {
            stage('Link upstream build') {
                when {
                    beforeAgent true
                    not { changeRequest() }
                }
                steps {
                    linkUpstreamFromCommit(gitFallback: true)
                }
            }

            stage('Build Pipeline') {
                agent {
                    docker {
                        image buildImage
                        args buildArgs
                        reuseNode true
                    }
                }
                stages {
                    stage('Setup') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                ulimit -n 1024
                                echo "${GREEN_COLOR}=== SETUP COMPLETED ===${CLOSE_COLOR}"
                            """
                        }
                    }

                    stage('Create Environment') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                echo "${GREEN_COLOR} CREATE ENVIRONMENT FOR FIRMWARE ${CLOSE_COLOR}"
                                sudo -E ./createEnvironment.sh
                            """
                        }
                    }

                    stage('Create Base OS') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                echo "${GREEN_COLOR} CREATE BASE OS ${CLOSE_COLOR}"
                                sudo -E ./createFirmware.sh
                            """
                        }
                    }

                    stage('Create Firmware') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                echo "${GREEN_COLOR} CREATE FIRMWARE ${CLOSE_COLOR}"
                                sudo -E ./installFirmware.sh
                            """
                        }
                    }

                    stage('Prepare Artifacts') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                sudo -E mkdir -p artifacts-sbc/firmware
                                sudo -E mv artifacts-sbc/*.fw artifacts-sbc/firmware/ 2>/dev/null || true
                                sudo -E mv artifacts-sbc/*.md5sum artifacts-sbc/firmware/ 2>/dev/null || true
                                sudo -E mv artifacts-sbc/*.version artifacts-sbc/firmware/ 2>/dev/null || true
                                sudo -E mv artifacts-sbc/*.log artifacts-sbc/firmware/ 2>/dev/null || true
                            """
                        }
                    }

                    stage('Configure Image') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                echo "${GREEN_COLOR} CONFIGURE IMAGE ${CLOSE_COLOR}"
                                sudo -E ./prepareImage.sh
                            """
                        }
                    }

                    stage('Build Image') {
                        steps {
                            sh """
                                #!/bin/bash
                                set -e
                                set +x
                                echo "${GREEN_COLOR} BUILD IMAGE ${CLOSE_COLOR}"
                                sudo -E ./createImage.sh
                                sudo -E mkdir -p artifacts-sbc/iso
                                sudo -E mv artifacts-sbc/*.iso artifacts-sbc/iso/ 2>/dev/null || true
                                echo "${GREEN_COLOR}=== BUILD PIPELINE COMPLETED ===${CLOSE_COLOR}"
                            """
                        }
                    }

                    stage('Publish Artifacts') {
                        steps {
                            script {
                                archiveArtifacts artifacts: 'artifacts-sbc/iso/*.iso, artifacts-sbc/firmware/*.fw, artifacts-sbc/firmware/*.md5sum, artifacts-sbc/firmware/*.version, artifacts-sbc/firmware/*.log, artifacts-sbc/*.xml, artifacts-sbc/*.txt, artifacts-sbc/*.json, artifacts-sbc/*.html', fingerprint: true, onlyIfSuccessful: true
                                currentBuild.displayName = findSBCVersion()
                                publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '', reportFiles: 'artifacts-sbc/grype_report.html', reportName: 'Grype report', reportTitles: 'Grype report', useWrapperFileDirectly: true])
                                stash includes: 'artifacts-sbc/firmware/*.log, artifacts-sbc/*.xml, artifacts-sbc/*.txt, artifacts-sbc/*.json, artifacts-sbc/*.html', name: 'artifacts-sbc'
                                VERSION = findSBCVersion()
                            }
                        }
                    }
                }
            }

            stage('Test firmware') {
                when {
                    beforeAgent true
                    expression { return false }
                }
                steps {
                    catchError(message: 'Test Firmware FAILED', stageResult: 'UNSTABLE', buildResult: 'UNSTABLE') {
                        sh """
                            echo -e "${GREEN_COLOR} START SMOKE TESTS ${CLOSE_COLOR}"
                            sudo -E ./testFirmware.sh ${TEST_SBC_IP} ${BUILD_ID}
                        """
                        archiveArtifacts artifacts: '*.log', fingerprint: true, onlyIfSuccessful: true
                    }
                }
            }

            stage('Run sonarqube check') {
                agent {
                    docker { image 'registry.nexus.asatnet.net/base/sonar-scanner-cli' }
                }
                steps {
                    unstash 'artifacts-sbc'
                    script {
                        if (env.CHANGE_ID) {
                            env.putAt('SONAR_PULLREQUEST_KEY', env.CHANGE_ID)
                            env.putAt('SONAR_PULLREQUEST_BRANCH', env.CHANGE_BRANCH)
                            env.putAt('SONAR_PULLREQUEST_BASE', env.GIT_BRANCH)
                        }
                    }
                    withSonarQubeEnv('SonarQube Server') {
                        sh 'sonar-scanner'
                    }
                }
            }

            stage('Deploy production firmware') {
                when {
                    beforeAgent true
                    not { changeRequest() }
                }
                steps {
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: 'nexus.asatnet.net',
                        version: artifactVersion(),
                        groupId: CFG.mavenGroup,
                        repository: 'maven-releases',
                        credentialsId: nexusCredentialsId,
                        artifacts: firmwareArtifacts()
                    )
                    publishSummary(
                        arrayToHrefList([releaseUrl('iso'), releaseUrl('fw')]),
                        'Build results',
                        'info.gif'
                    )
                }
            }

            stage('Deploy test firmware') {
                when {
                    beforeAgent true
                    changeRequest()
                }
                steps {
                    echo 'Deploying test firmware'
                    nexusArtifactUploader(
                        nexusVersion: 'nexus3',
                        protocol: 'http',
                        nexusUrl: 'nexus.asatnet.net',
                        version: "${artifactVersion()}-SNAPSHOT",
                        groupId: CFG.mavenGroup,
                        repository: 'maven-snapshots',
                        credentialsId: nexusCredentialsId,
                        artifacts: firmwareArtifacts()
                    )
                    publishSummary(
                        arrayToHrefList([
                            "https://nexus.asatnet.net/#browse/browse:maven-snapshots:${GROUP_PATH.replace('/', '%2F')}%2F${CFG.project}%2F${artifactVersion()}-SNAPSHOT"
                        ]),
                        'Build results',
                        'info.gif'
                    )
                }
            }

            stage('Create Jira Version') {
                agent {
                    docker { image buildImage }
                }
                when {
                    beforeAgent true
                    not { changeRequest() }
                }
                steps {
                    jiraCreateVersion(version: VERSION, projectKey: CFG.jiraProject)
                }
            }

            stage('Create release notes') {
                agent {
                    docker { image buildImage }
                }
                when {
                    beforeAgent true
                    not { changeRequest() }
                }
                steps {
                    unstash 'artifacts-sbc'
                    script {
                        def artifactRelativePath = 'artifacts-sbc/syft_report.txt'
                        def previousBuild = currentBuild.getPreviousSuccessfulBuild()
                        def differences = [:]
                        if (previousBuild) {
                            def previousArtifactUrl = "${previousBuild.getAbsoluteUrl()}artifact/${artifactRelativePath}"
                            differences = computeDifferences(
                                parsePackageListFromUrl(previousArtifactUrl),
                                parsePackageListFromFile(artifactRelativePath)
                            )
                            for (kind in ['updated', 'added', 'removed']) {
                                if (differences[kind].size() > 0) {
                                    publishSummary(
                                        arrayToString(
                                            new ArrayList<>(differences[kind].collect { key, value -> "$key - $value" }),
                                            ['<li>', '</li>'],
                                            ['<ul>', '</ul>']
                                        ),
                                        "${kind.capitalize()} Packages",
                                        'info.gif'
                                    )
                                }
                            }
                        }
                        publishSummary(
                            arrayToHrefList([
                                createConfluencePage(
                                    TITLE: "Release ${VERSION}",
                                    DEBUG: false,
                                    SPACE: CFG.releaseNotesSpace,
                                    ROOT: CFG.releaseNotesRoot,
                                    CONTENT: inflateTemplate(
                                        STRINGIFY: true,
                                        TEMPLATE: libraryResource('com/spacebridge/confluence/image.release.gtpl'),
                                        date: new Date().format('yyyy-MM-dd'),
                                        version: "${VERSION}",
                                        projectId: CFG.jiraProject,
                                        build: currentBuild,
                                        buildUrl: "${BUILD_URL}",
                                        keyComponents: getKeyComponentsInfo().join(''),
                                        installationPage: CFG.installationRoot,
                                        installationSpace: CFG.installationSpace,
                                        isoUrl: releaseUrl('iso'),
                                        fwUrl: releaseUrl('fw'),
                                        boms: secureString(loadReport('artifacts-sbc/syft_report.txt')),
                                        vulnerability: secureString(loadReport('artifacts-sbc/grype_report.txt')),
                                        bomChangeset: differences
                                    )
                                )
                            ]),
                            'Release information'
                        )
                    }
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
