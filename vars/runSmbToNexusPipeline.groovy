import groovy.transform.Field
import java.util.regex.Pattern

/**
 * Publish archives from an SMB share to Nexus.
 *
 * Scans a share directory, analyzes every archive that is not in Nexus yet and
 * uploads the ones that pass, one parallel stage per archive. An archive is a
 * zip, uploaded to a Maven repository (archiveType zip, the default), or a
 * directory, zipped and uploaded to a raw repository (archiveType directory).
 * Everything that differs between repositories and branches lives in a
 * per-branch config file (jenkins.yml by default), so the Jenkinsfile is a
 * two-line stub:
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

/** Config keys every repository has to provide, by archiveType. */
@Field Map REQUIRED_CONFIG = [
    zip      : ['smbDirectory', 'nexusRepository', 'groupId', 'requiredFiles'],
    directory: ['smbDirectory', 'nexusRepository', 'nexusPath', 'archivePattern', 'requiredFiles']
]

/** Defaults for the optional keys. The patterns follow the MCD FPGA naming. */
@Field Map DEFAULT_CONFIG = [
    archiveType      : 'zip',
    smbShare         : '//asatvault.asatnet.net/SATNET_DATA_1',
    versionSuffix    : '',
    archivePattern   : '(mcd|MCD).*\\.zip',
    artifactIdPattern: '^\\w*?(?=_20\\d{2,2})',
    versionPattern   : '\\d{8}(_\\d{6})?_([a-f]|\\d){7,8}'
]

/** A directory is taken once nothing in it changed for this long: engineers copy a load in several steps. */
@Field int QUIET_MINUTES = 30

/**
 * Validate the config and return it with the defaults filled in. Reports every
 * missing key at once rather than failing on the first one, because these
 * errors surface at runtime.
 */
Map validateConfig(Map config) {
    if (!config) {
        error('runSmbToNexusPipeline: the config file is empty or could not be parsed.')
    }
    def merged = DEFAULT_CONFIG + config
    def required = REQUIRED_CONFIG[merged.archiveType]
    if (required == null) {
        error("runSmbToNexusPipeline: archiveType must be zip or directory, not ${merged.archiveType}.")
    }
    def missing = required.findAll { !config.containsKey(it) }
    if (missing) {
        error("runSmbToNexusPipeline: the config is missing ${missing.join(', ')}.")
    }
    if (!(config.requiredFiles instanceof List)) {
        error('runSmbToNexusPipeline: requiredFiles must be a list of file name globs, [] for none.')
    }
    if (merged.archiveType == 'directory') {
        // listDirectories embeds the pattern in a line match, where ^ and $ can never match
        if (config.archivePattern.startsWith('^') || config.archivePattern.endsWith('$')) {
            error("runSmbToNexusPipeline: archivePattern is matched against the whole directory name, drop ^ and \$ from ${config.archivePattern}.")
        }
        if (config.nexusPath.startsWith('/') || config.nexusPath.endsWith('/')) {
            error("runSmbToNexusPipeline: nexusPath must not start or end with /, for example mesh/mesh-demod-fpga, not ${config.nexusPath}.")
        }
    }
    return merged
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

/** Every directory in the share listing whose whole name matches pattern. */
@NonCPS
List<String> listDirectories(String listing, String pattern) {
    // "  fpga_mesh_a7_20260923_1      D        0  Wed Sep 23 20:58:10 2026"
    def matcher = listing =~ /(?m)^\s+(${pattern})\s+D\w*\s/
    def names = []
    while (matcher.find()) {
        names << matcher.group(1)
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

/** Whether url exists in Nexus. Any answer but 200 or 404 fails the build. */
Boolean isPublished(String url) {
    def response = httpRequest(
        url: url,
        httpMode: 'HEAD',
        authentication: NEXUS_CREDENTIALS_ID,
        validResponseCodes: '200,404',
        quiet: true
    )
    return response.status == 200
}

String rawDirectory() {
    return "https://nexus.asatnet.net/repository/${CFG.nexusRepository}/${CFG.nexusPath}/"
}

void scanShare() {
    def listing = sh(
        label: "List ${CFG.smbDirectory}",
        script: 'smbclient $SMB_OPTS --directory "$SMB_DIR" "$SMB_SHARE" -c dir',
        returnStdout: true
    )
    def directories = CFG.archiveType == 'directory'
    def fileNames = directories ? listDirectories(listing, CFG.archivePattern) : listArchives(listing, CFG.archivePattern)
    for (fileName in fileNames) {
        if (directories) {
            // Consumers pin a zip by URL and sha256, so a published one is never replaced
            def url = rawDirectory() + fileName + '.zip'
            if (!isPublished(url)) {
                newArchives << [file: fileName + '.zip', load: fileName, url: url]
            }
        } else {
            def artifactId = firstMatch(fileName, CFG.artifactIdPattern)
            def version = firstMatch(fileName, CFG.versionPattern)
            def nexusVersion = version + (CFG.versionSuffix ?: '')
            if (artifactId != '' && version != '' && !checkIfUploaded(artifactId, nexusVersion)) {
                newArchives << [file: fileName, artifactId: artifactId, version: nexusVersion]
            }
        }
    }
    echo("${newArchives.size()} of ${fileNames.size()} archives are not in ${CFG.nexusRepository} yet")
}

/** Download a zip and unpack it into content/. Returns the problems found. */
List<String> fetchZip(Map archive) {
    withEnv(["ARCHIVE=${archive.file}"]) {
        sh(
            label: "Download ${archive.file}",
            script: 'smbclient $SMB_OPTS --directory "$SMB_DIR" "$SMB_SHARE" -c "get \\"$ARCHIVE\\""'
        )
    }
    // A zip still being copied to the share fails this test
    def intact = unzip(
        zipFile: archive.file,
        test: true
    )
    if (!intact) {
        return ['the zip is corrupt or incomplete']
    }
    unzip(
        zipFile: archive.file,
        dir: 'content',
        quiet: true
    )
    return []
}

/**
 * Download a directory into content/ and zip it as <name>/<files>. Returns null,
 * having downloaded nothing, while the directory is empty or anything in it
 * changed less than QUIET_MINUTES ago.
 */
List<String> fetchDirectory(Map archive) {
    def share
    withEnv(["LOAD=${archive.load}"]) {
        // The copy to the share keeps each file's mtime, so a file that just landed shows
        // only in the mtime of its directory, the "." entry; "..", the parent, does not count.
        // The age is taken on the agent, in the timezone smbclient prints the mtimes in.
        share = sh(
            label: "Inspect ${archive.load}",
            returnStdout: true,
            script: '''
                listing=$(smbclient $SMB_OPTS --directory "$SMB_DIR/$LOAD" "$SMB_SHARE" -c "recurse ON; ls") || { echo "$LOAD: cannot list it on the share" >&2; exit 1; }
                # size and mtime of every entry: "  top.sof      A 62238574  Wed Sep  2 10:00:00 2026"
                entries=$(printf '%s\\n' "$listing" | grep -vE '^ +[.][.] ' \\
                    | sed -nE 's/.* ([0-9]+)  ([A-Z][a-z]{2} [A-Z][a-z]{2} +[0-9]{1,2} [0-9:]{8} [0-9]{4})$/\\1 \\2/p')
                [ -n "$entries" ] || { echo "$LOAD: no sizes and mtimes in its listing" >&2; exit 1; }
                bytes=$(printf '%s\\n' "$entries" | awk '{ s += $1 } END { print s }')
                newest=$(printf '%s\\n' "$entries" | while read -r size t; do date -d "$t" +%s; done | sort -n | tail -1)
                echo "$bytes $(( $(date +%s) - newest ))"
            '''
        ).trim().tokenize(' ')
    }
    long bytes = share[0] as long
    long age = share[1] as long
    if (bytes == 0 || age < QUIET_MINUTES * 60) {
        echo("${archive.load}: empty or changed less than ${QUIET_MINUTES} min ago, skipped")
        return null
    }
    withEnv([
        "LOAD=${archive.load}",
        "BYTES=${bytes}"
    ]) {
        // mget exits 0 even when a file fails, so the downloaded bytes have to add up to the listed ones
        sh(
            label: "Download ${archive.load}",
            script: '''
                mkdir -p "content/$LOAD"
                smbclient $SMB_OPTS --directory "$SMB_DIR/$LOAD" "$SMB_SHARE" -c "lcd \\"content/$LOAD\\"; recurse ON; prompt OFF; mget *"
                downloaded=$(find "content/$LOAD" -type f -printf '%s\\n' | awk '{ s += $1 } END { print s + 0 }')
                echo "$LOAD: downloaded $downloaded of $BYTES bytes"
                [ "$downloaded" -eq "$BYTES" ]
            '''
        )
    }
    // The agent image has no zip binary. defaultExcludes would drop files such as .gitignore
    // that the byte check counted, from a zip that is never replaced
    zip(
        zipFile: archive.file,
        dir: 'content',
        glob: "${archive.load}/**",
        defaultExcludes: false
    )
    return []
}

/** Analyze an archive and publish its summary. Returns the problems found, or null for a directory that is not ready yet. */
List<String> analyze(Map archive, String releaseNotes) {
    // The archive before this one left its files here, passed or failed
    dir('content') {
        deleteDir()
    }
    def problems = CFG.archiveType == 'directory' ? fetchDirectory(archive) : fetchZip(archive)
    if (problems == null) {
        return null
    }
    def content = []
    if (!problems) {
        content = findFiles(glob: 'content/**')
        for (pattern in CFG.requiredFiles) {
            if (!findFiles(glob: "content/**/${pattern}")) {
                problems << "no ${pattern}"
            }
        }
    }
    if (CFG.archiveType == 'zip' && !hasReleaseNote(releaseNotes, archive.file)) {
        problems << 'no entry in release_notes.txt'
    }
    def summary = inflateTemplate(
        TEMPLATE: libraryResource('com/spacebridge/nexus/archive-summary.html.gtpl'),
        archive: archive,
        target: CFG.archiveType == 'directory' ? archive.url : "${archive.artifactId}:${archive.version}",
        problems: problems,
        checksum: sha256(file: archive.file),
        content: content
    )
    publishSummary(summary)
    return problems
}

void analyzeArchives() {
    def releaseNotes = ''
    if (CFG.archiveType == 'zip') {
        sh(
            label: 'Download release_notes.txt',
            script: 'smbclient $SMB_OPTS --directory "$SMB_DIR" "$SMB_SHARE" -c "get release_notes.txt"'
        )
        releaseNotes = readFile('release_notes.txt')
    }
    for (archive in newArchives) {
        catchError(
            buildResult: 'FAILURE',
            stageResult: 'FAILURE',
            catchInterruptions: false
        ) {
            def problems = analyze(archive, releaseNotes)
            if (problems) {
                unstable("${archive.file}: ${problems.join(', ')}")
            } else if (problems != null) {
                readyArchives << archive
            }
        }
    }
}

void upload(Map archive) {
    if (CFG.archiveType == 'directory') {
        // The scan looked before Analyze, and a published zip is never replaced
        if (isPublished(archive.url)) {
            error("runSmbToNexusPipeline: ${archive.url} was published while this build ran, it is not replaced.")
        }
        publishNexus(
            archive.file,
            rawDirectory(),
            false,
            NEXUS_CREDENTIALS_ID
        )
        addBadge(
            icon: 'symbol-cloud-upload-outline plugin-ionicons-api',
            text: archive.file,
            link: "https://nexus.asatnet.net/service/rest/repository/browse/${CFG.nexusRepository}/${CFG.nexusPath}/"
        )
    } else {
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
    }
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
