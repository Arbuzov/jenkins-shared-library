import groovy.transform.Field

/** Version picker for a firmware repository; see runFirmwareConfigurator.txt. */
@Field Map CFG = [:]
@Field Map manifest = [:]
@Field Map versionFile = [:]
@Field Map packages = [:]
@Field List<Map> fields = []
@Field Map chosen = [:]
@Field String chosenVersion = ''
@Field String KEEP = ' (current)'

Map validateConfig(def config) {
    if (!(config instanceof Map) || !config) {
        error('runFirmwareConfigurator: the config must be a non-empty map.')
    }
    def unknown = config.keySet().findAll { !(it in ['firmwareRepo', 'branch', 'repository']) }
    if (unknown) { error("runFirmwareConfigurator: unknown keys ${unknown.join(', ')}.") }
    if (!(config.firmwareRepo instanceof String) || !(config.firmwareRepo ==~ /https?:\/\/[^\s]+/)) {
        error('runFirmwareConfigurator: firmwareRepo must be an HTTP(S) repository URL.')
    }
    URL repo = new URL(config.firmwareRepo)
    if (repo.userInfo || repo.query || repo.ref || repo.path in ['', '/']) {
        error('runFirmwareConfigurator: firmwareRepo must have a project path and no credentials, query or fragment.')
    }
    if (config.branch != null && (!(config.branch instanceof String) || !config.branch.trim())) {
        error('runFirmwareConfigurator: branch must be a non-empty string.')
    }
    if (config.repository != null && (!(config.repository instanceof String) || !(config.repository ==~ /[A-Za-z0-9._-]+/))) {
        error('runFirmwareConfigurator: repository must be a Debian distribution name.')
    }
    // An MR job is named MR-<n>; preview its target, never that synthetic name or its source branch.
    String branch = env.CHANGE_ID ? env.CHANGE_TARGET : (config.branch ?: env.BRANCH_NAME)
    if (!branch?.trim()) {
        error('runFirmwareConfigurator: cannot resolve the firmware branch; BRANCH_NAME or the MR CHANGE_TARGET is required.')
    }
    return [firmwareRepo: config.firmwareRepo, branch: branch, repository: config.repository ?: 'bullseye']
}

@NonCPS
String paramName(String key) { key.toUpperCase().replaceAll(/[-+.]/, '_') + '_VERSION' }

/** Accept only the firmware's plain package=version format; never silently drop a key. */
@NonCPS
Map parseManifest(String text) {
    Map parsed = [:]
    int number = 0
    for (line in text.readLines()) {
        number++
        if (!line.trim() || line.trim().startsWith('#') || line.trim().startsWith('!')) { continue }
        def match = line =~ /^[ \t]*([a-z0-9][a-z0-9+.-]*)[ \t]*=[ \t]*(\S+)[ \t]*$/
        if (!match.matches() || parsed.containsKey(match[0][1])) {
            throw new IllegalArgumentException("Manifest line ${number}: expected a unique package=version entry.")
        }
        parsed[match[0][1]] = match[0][2]
    }
    if (!parsed) { throw new IllegalArgumentException('Manifest has no pinned packages.') }
    return parsed
}

void refreshParameters() {
    fields = []
    manifest = gitlabGetFile(gitUrl: CFG.firmwareRepo, filePath: 'Manifest', ref: CFG.branch)
    if (!manifest.commit_id || !manifest.last_commit_id) { error('Manifest read returned no commit IDs.') }
    // VERSION must come from the same snapshot even if the target branch moves between reads.
    versionFile = gitlabGetFile(gitUrl: CFG.firmwareRepo, filePath: 'VERSION', ref: manifest.commit_id)
    if (!versionFile.last_commit_id) { error('VERSION read returned no last commit ID.') }
    packages = parseManifest(manifest.text)
    if (packages.keySet().collect { paramName(it) }.unique().size() != packages.size()) {
        error('Manifest package names collide after conversion to build parameter names.')
    }
    def available = getRepReproArtifacts(repository: CFG.repository, packageNames: packages.keySet().toList(), failOnError: true)
    def definitions = [separator(name: 'software-versions', sectionHeader: 'Software Versions', separatorStyle: 'border-width: 0')]
    for (key in packages.keySet().toList()) {
        def versions = sortDebianVersions(versions: available[key] ?: [])
        fields << [key: key, param: paramName(key), current: packages[key], versions: versions]
        definitions << choice(name: paramName(key), choices: [packages[key] + KEEP] + versions,
            description: "'...${KEEP}' keeps the version read from ${CFG.branch} when the build runs.")
        if (!versions) { echo("${key}: no versions in ${CFG.repository}; only keep current is available.") }
    }
    definitions << separator(name: 'release-options', sectionHeader: 'Release Options', separatorStyle: 'border-width: 0')
    definitions << string(name: 'FIRMWARE_VERSION', defaultValue: '',
        description: "Firmware X.Y.Z for VERSION (empty = keep ${versionFile.text.trim()}).")
    currentBuild.description = 'Update parameters'
    // Scheduled runs refresh the lists only; they never commit to the firmware repository.
    properties([parameters(definitions), pipelineTriggers(
        canWrite() ? [cron('H/5 * * * *')] : []
    )])
}

String changeSummary() {
    def lines = chosen.collect { key, value -> "${key}: ${packages[key]} -> ${value}" }
    if (chosenVersion) { lines << "VERSION: ${versionFile.text.trim()} -> ${chosenVersion}" }
    return lines.join('\n')
}

void validateInputs() {
    chosen = [:]
    chosenVersion = ''
    for (field in fields) {
        String value = params[field.param]?.toString()
        if (!value || value.endsWith(KEEP) || value == field.current) { continue }
        if (!(value in field.versions)) {
            error("${field.key} ${value}: no longer in ${CFG.repository}. Lists refreshed; choose again.")
        }
        chosen[field.key] = value
    }
    String firmwareVersion = (params.FIRMWARE_VERSION ?: '').toString().trim()
    if (firmwareVersion && !(firmwareVersion ==~ /\d+\.\d+\.\d+/)) {
        error('FIRMWARE_VERSION must be X.Y.Z, or empty to keep the current version.')
    }
    if (firmwareVersion && firmwareVersion != versionFile.text.trim()) { chosenVersion = firmwareVersion }
    echo(hasChanges() ? changeSummary() : 'Nothing to update: Manifest and VERSION already hold the requested versions.')
    currentBuild.description = hasChanges() ? changeSummary().replace('\n', ', ') : 'No changes'
    if (hasChanges() && !canWrite()) { echo('Preview only: this branch or merge request cannot update firmware.') }
}

boolean hasChanges() { !chosen.isEmpty() || !chosenVersion.isEmpty() }

boolean canWrite() { !env.CHANGE_ID && env.BRANCH_NAME && env.BRANCH_NAME == CFG.branch }

void updateFirmware() {
    // Also protect direct calls, not just the declarative stage gate.
    if (!canWrite() || !currentBuild.getBuildCauses('hudson.model.Cause$UserIdCause')) {
        error('Firmware updates require a user-started branch build outside a merge request, targeting the same firmware branch.')
    }
    if (!hasChanges()) { error('Nothing to commit.') }
    def files = []
    if (chosen) {
        def edited = applyManifestVersions(text: manifest.text, versions: chosen)
        if (parseManifest(edited.text) != packages + chosen || edited.changed != chosen) {
            error('Editing Manifest did not produce exactly the requested package versions. Nothing committed.')
        }
        files << [filePath: 'Manifest', content: edited.text, lastCommitId: manifest.last_commit_id]
    }
    if (chosenVersion) {
        String ending = versionFile.text.contains('\r\n') ? '\r\n' : '\n'
        files << [filePath: 'VERSION', content: chosenVersion + ending, lastCommitId: versionFile.last_commit_id]
    }
    String requester = currentBuild.getBuildCauses().find { it.userName }?.userName ?: 'unknown'
    def committed = gitlabCommitFiles(gitUrl: CFG.firmwareRepo, branch: CFG.branch, files: files,
        commitMessage: "Update firmware versions from ${env.JOB_NAME} #${env.BUILD_NUMBER}\n\n${changeSummary()}\n\n" +
            "Requested by ${requester}\n${env.BUILD_URL}\n\n" +
            "Jenkins-Upstream-Project: ${env.JOB_NAME}\nJenkins-Upstream-Build: ${env.BUILD_NUMBER}")
    echo("Committed ${committed.id}. GitLab webhook starts the ISO/firmware build on ${CFG.branch}.")
}

@NonCPS
Map statusBuild(List statuses) {
    for (status in statuses) {
        def match = (status.description ?: '') =~ /^(.+?) #(\d+): /
        if (match.find()) { return [project: match.group(1), build: match.group(2).toInteger()] }
    }
    return null
}

void linkFirmwareBuild() {
    def statuses = gitlabApiGet(gitUrl: CFG.firmwareRepo,
        path: "repository/commits/${manifest.commit_id}/statuses",
        query: [ref: CFG.branch, all: true, sort: 'desc'])
    def build = statusBuild(statuses)
    if (build) { linkUpstreamBuild(build + [reverse: true]) }
    else { echo("${manifest.commit_id}: no numbered Jenkins build in GitLab statuses, so no link.") }
}

def call(Map args = [:]) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)
    CFG = validateConfig(readYaml(text: readTrusted(args.CONFIG ?: 'jenkins.yml')))
    chosen = [:]
    chosenVersion = ''
    pipeline {
        agent any
        options {
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: '10'))
            ansiColor('xterm')
            disableRestartFromStage()
            disableConcurrentBuilds()
        }
        stages {
            stage('Update configuration') {
                steps { refreshParameters() }
            }
            stage('Validate inputs') {
                when { triggeredBy 'UserIdCause' }
                steps { validateInputs() }
            }
            stage('Update firmware') {
                when {
                    allOf {
                        triggeredBy 'UserIdCause'
                        expression { canWrite() && hasChanges() }
                    }
                }
                steps { updateFirmware() }
            }
            stage('Link firmware build') {
                when {
                    allOf {
                        triggeredBy 'UserIdCause'
                        expression { !hasChanges() }
                    }
                }
                steps { linkFirmwareBuild() }
            }
        }
    }
}
