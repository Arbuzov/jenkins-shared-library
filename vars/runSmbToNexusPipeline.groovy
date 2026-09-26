import groovy.transform.Field
import java.util.regex.Pattern

/**
 * Publish archives from an SMB share to a Maven repository in Nexus.
 *
 * Scans a share directory, analyzes every archive that is not in Nexus yet and
 * uploads the ones that pass, one parallel stage per archive. Everything that
 * differs between repositories and branches lives in a per-branch config file
 * (jenkins.yml by default), so the Jenkinsfile is a two-line stub:
 *
 *     @Library('asatnet@master')_
 *     runSmbToNexusPipeline()
 *
 * The config is read with readTrusted, so it only works in multibranch jobs.
 *
 * See vars/runSmbToNexusPipeline.txt for the config format and parameters.
 */

// A vars script is a singleton per build, so these hold state for the whole run.
@Field Map CFG = [:]
@Field String NEXUS_CREDENTIALS_ID = ''
@Field List<Map> newArchives = []
@Field List<Map> readyArchives = []

/** Config keys every repository has to provide. */
@Field List REQUIRED_CONFIG = ['smbDirectory', 'nexusRepository', 'groupId', 'requiredFiles']

/** Defaults for the optional keys. The patterns follow the MCD FPGA naming. */
@Field Map DEFAULT_CONFIG = [
    smbShare         : '//asatvault.asatnet.net/SATNET_DATA_1',
    versionSuffix    : '',
    archivePattern   : '(mcd|MCD).*\\.zip',
    artifactIdPattern: '^\\w*?(?=_20\\d{2,2})',
    versionPattern   : '\\d{8}(_\\d{6})?_([a-f]|\\d){7,8}'
]

/**
 * Validate the config and return it with the defaults filled in. Reports every
 * missing key at once rather than failing on the first one, because these
 * errors surface at runtime.
 */
Map validateConfig(Map config) {
    if (!config) {
        error('runSmbToNexusPipeline: the config file is empty or could not be parsed.')
    }
    def missing = REQUIRED_CONFIG.findAll { !config.containsKey(it) }
    if (missing) {
        error("runSmbToNexusPipeline: the config is missing ${missing.join(', ')}.")
    }
    if (!(config.requiredFiles instanceof List)) {
        error('runSmbToNexusPipeline: requiredFiles must be a list of file name globs, [] for none.')
    }
    return DEFAULT_CONFIG + config
}

/** Every match of pattern in the share listing, one per archive. */
@NonCPS
List<String> listArchives(String listing, String pattern) {
    def matcher = listing =~ pattern
    def names = []
    while (matcher.find()) {
        names << matcher.group()
    }
    return names
}

/** The first match of pattern in text, or '' when there is none. */
@NonCPS
String firstMatch(String text, String pattern) {
    def matcher = text =~ pattern
    return matcher.find() ? matcher.group() : ''
}

@NonCPS
Boolean hasReleaseNote(String releaseNotes, String fileName) {
    def name = Pattern.quote(fileName[0..-5].trim())
    return (releaseNotes =~ /(?m)^\s*\d+\.\s+${name}(?!\S)/).find()
}

Boolean checkIfUploaded(String artifactId, String version) {
    def response = httpRequest(
        url: "https://nexus.asatnet.net/service/rest/v1/search/assets?maven.artifactId=${artifactId}&maven.baseVersion=${version}&repository=${CFG.nexusRepository}&maven.extension=zip",
        httpMode: 'GET',
        consoleLogResponseBody: false,
        contentType: 'APPLICATION_JSON',
        authentication: NEXUS_CREDENTIALS_ID,
        quiet: true
    )
    def found = readJSON(
        text: response?.content,
        returnPojo: true
    )
    return found.items.size() > 0
}

void scanShare() {
    def listing = sh(
        label: "List ${CFG.smbDirectory}",
        script: 'smbclient $SMB_OPTS --directory "$SMB_DIR" "$SMB_SHARE" -c dir',
        returnStdout: true
    )
    def fileNames = listArchives(listing, CFG.archivePattern)
    for (fileName in fileNames) {
        def artifactId = firstMatch(fileName, CFG.artifactIdPattern)
        def version = firstMatch(fileName, CFG.versionPattern)
        def nexusVersion = version + (CFG.versionSuffix ?: '')
        if (artifactId != '' && version != '' && !checkIfUploaded(artifactId, nexusVersion)) {
            newArchives << [file: fileName, artifactId: artifactId, version: nexusVersion]
        }
    }
    echo("${newArchives.size()} of ${fileNames.size()} archives are not in ${CFG.nexusRepository} yet")
}

List<String> analyze(Map archive, String releaseNotes) {
    withEnv(["ARCHIVE=${archive.file}"]) {
        sh(
            label: "Download ${archive.file}",
            script: 'smbclient $SMB_OPTS --directory "$SMB_DIR" "$SMB_SHARE" -c "get \\"$ARCHIVE\\""'
        )
    }
    def problems = []
    def content = []
    // A zip still being copied to the share fails this test
    def intact = unzip(
        zipFile: archive.file,
        test: true
    )
    if (intact) {
        unzip(
            zipFile: archive.file,
            dir: 'content',
            quiet: true
        )
        content = findFiles(glob: 'content/**')
        for (pattern in CFG.requiredFiles) {
            if (!findFiles(glob: "content/**/${pattern}")) {
                problems << "no ${pattern}"
            }
        }
        dir('content') {
            deleteDir()
        }
    } else {
        problems << 'the zip is corrupt or incomplete'
    }
    if (!hasReleaseNote(releaseNotes, archive.file)) {
        problems << 'no entry in release_notes.txt'
    }
    def summary = inflateTemplate(
        TEMPLATE: libraryResource('com/spacebridge/nexus/archive-summary.html.gtpl'),
        archive: archive,
        problems: problems,
        checksum: sha256(file: archive.file),
        content: content
    )
    publishSummary(summary)
    return problems
}

void analyzeArchives() {
    def releaseNotes = readFile('release_notes.txt')
    for (archive in newArchives) {
        catchError(
            buildResult: 'FAILURE',
            stageResult: 'FAILURE',
            catchInterruptions: false
        ) {
            def problems = analyze(archive, releaseNotes)
            if (problems) {
                unstable("${archive.file}: ${problems.join(', ')}")
            } else {
                readyArchives << archive
            }
        }
    }
}

void upload(Map archive) {
    nexusArtifactUploader(
        nexusVersion: 'nexus3',
        protocol: 'http',
        nexusUrl: 'nexus.asatnet.net',
        version: archive.version,
        groupId: CFG.groupId,
        repository: CFG.nexusRepository,
        credentialsId: NEXUS_CREDENTIALS_ID,
        artifacts: [[
            artifactId: archive.artifactId,
            file: archive.file,
            type: 'zip',
        ]]
    )
    addBadge(
        icon: 'symbol-cloud-upload-outline plugin-ionicons-api',
        text: "${archive.artifactId}:${archive.version}",
        link: "https://nexus.asatnet.net/service/rest/repository/browse/${CFG.nexusRepository}/${CFG.groupId.replace('.', '/')}/${archive.artifactId}/${archive.version}/"
    )
    // Rotation keeps 10 builds, a build every 5 min: keep the ones that uploaded
    currentBuild.keepLog = true
}

// Declarative parallel takes only stages known in advance, so the per-archive stages are built here
void uploadArchives() {
    def stages = [:]
    for (archive in readyArchives) {
        def current = archive
        stages[current.file] = {
            stage("Upload ${current.file}") {
                catchError(
                    buildResult: 'FAILURE',
                    stageResult: 'FAILURE',
                    catchInterruptions: false
                ) {
                    upload(current)
                }
            }
        }
    }
    parallel(stages)
}

def call(Map arg = [:]) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)

    String configFile = arg.CONFIG ?: 'jenkins.yml'
    String smbCredentialsId = arg.SMB_CREDENTIALS_ID ?: 'arbuzov-username-password'
    NEXUS_CREDENTIALS_ID = arg.NEXUS_CREDENTIALS_ID ?: 'jira-username-password'

    CFG = validateConfig(readYaml(text: readTrusted(configFile)))

    pipeline {
        agent {
            docker {
                image 'registry.nexus.asatnet.net/spacebridge/smb-client:latest'
            }
        }
        options {
            timestamps()
            timeout(
                time: 1,
                unit: 'HOURS'
            )
            buildDiscarder(logRotator(
                numToKeepStr: '10',
                artifactNumToKeepStr: '10'
            ))
            disableConcurrentBuilds()
            ansiColor('xterm')
        }
        triggers {
            cron(env.CHANGE_ID ? '' : 'H/5 * * * *')
        }
        environment {
            SMB_SHARE = "${CFG.smbShare}"
            SMB_DIR = "${CFG.smbDirectory}"
            SMB_OPTS = '--timeout 120 --authentication-file authfile'
        }
        stages {
            stage('Scan SMB share') {
                environment {
                    // declarative credentials() takes a string, not a variable
                    SMB = credentials("${smbCredentialsId}")
                }
                steps {
                    sh(
                        label: 'Write the SMB auth file',
                        script: 'umask 077 && printf "domain=asatnet\\nusername=%s\\npassword=%s\\n" "$SMB_USR" "$SMB_PSW" > authfile'
                    )
                    scanShare()
                }
            }
            stage('Analyze archives') {
                when {
                    expression { !newArchives.isEmpty() }
                }
                steps {
                    sh(
                        label: 'Download release_notes.txt',
                        script: 'smbclient $SMB_OPTS --directory "$SMB_DIR" "$SMB_SHARE" -c "get release_notes.txt"'
                    )
                    analyzeArchives()
                }
            }
            stage('Upload to Nexus') {
                when {
                    not { changeRequest() }
                    expression { !readyArchives.isEmpty() }
                }
                steps {
                    uploadArchives()
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
