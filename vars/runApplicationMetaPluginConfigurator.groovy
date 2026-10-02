import groovy.transform.Field

/**
 * Pick the versions of the application plugins and Docker images of a meta plugin from drop-down lists and
 * commit them to the Manifest of the meta plugin repository, which runApplicationMetaPluginBuild builds.
 *
 * Everything that differs between configurators lives in a config file (jenkins.yml by default), so the
 * Jenkinsfile of a configurator repository is a two-line stub:
 *
 *     @Library('asatnet@master')_
 *
 *     runApplicationMetaPluginConfigurator()
 *
 * The config is read with readTrusted, so it only works in multibranch jobs.
 *
 * See vars/runApplicationMetaPluginConfigurator.txt for the config format, the parameters and the stages.
 */

// A vars script is a singleton per build, so these hold state for the whole run.
@Field String configFile = 'jenkins.yml'
@Field Map CFG = [:]
@Field Map manifest = [:]
@Field List<Map> fields = []
@Field List<Map> changes = []

@Field List<String> CONFIG_KEYS = ['pluginRepo', 'images']

/** Appended to the Manifest value in the first choice, which keeps whatever the Manifest holds when the build runs. */
@Field String KEEP = ' (current)'

/** Release builds only: X.Y.Z-bN, for plugins also X.Y.Z-bN-offline. */
@Field String RELEASE_VERSION = /\d+\.\d+\.\d+-b\d+(-offline)?/

/** Every problem of the config at once, because these errors surface at runtime. */
@NonCPS
List<String> configProblems(Map config) {
    def problems = []
    def unknown = config.keySet().findAll { !(it in CONFIG_KEYS) }
    if (unknown) {
        problems << "unknown keys ${unknown.join(', ')}, allowed: ${CONFIG_KEYS.join(', ')}"
    }
    if (!(config.pluginRepo instanceof String) || !config.pluginRepo) {
        problems << 'pluginRepo must be the URL of the meta plugin repository'
    }
    def images = config.images == null ? [:] : config.images
    if (!(images instanceof Map) || images.values().any { variables ->
        !(variables instanceof Map) || variables.values().any { image -> !(image instanceof String) || !image }
    }) {
        problems << 'images must map a plugin to {<Manifest environment variable>: <registry image>}'
    }
    return problems
}

/** Validate the config and return it with the defaults filled in. */
Map validateConfig(def config) {
    if (!(config instanceof Map) || !config) {
        error('runApplicationMetaPluginConfigurator: the config file is empty or could not be parsed.')
    }
    def problems = configProblems(config)
    if (problems) {
        error("runApplicationMetaPluginConfigurator: ${problems.join('; ')}.")
    }
    return [pluginRepo: config.pluginRepo, images: config.images ?: [:]]
}

/** Nexus search of application plugin `name` in maven-releases. */
Map pluginQuery(String name) {
    return [
        repository: 'maven-releases',
        'maven.groupId': 'com.spacebridge.application-plugin',
        'maven.artifactId': name,
        'maven.extension': 'tar.gz'
    ]
}

/** The first page of that search, newest version first: a new version changes it. Nexus lets the trigger read it anonymously. */
String pluginTriggerUrl(String name) {
    def query = pluginQuery(name) + [sort: 'version', direction: 'desc']
    return 'https://nexus.asatnet.net/service/rest/v1/search/assets?' + query.collect { key, value -> "${key}=${value}" }.join('&')
}

/** Tag list of Docker image `image` (e.g. spacebridge/nms-ng-backend) in registry.nexus.asatnet.net. */
String tagListUrl(String image) {
    return "https://registry.nexus.asatnet.net/v2/${image}/tags/list"
}

/** Versions of application plugin `name`, spelled as the Manifest spells them: with the classifier (-offline). */
List pluginVersions(String name) {
    def assets = nexusSearchAssets(query: pluginQuery(name))
    return sortVersions(
        versions: assets.collect { it.maven2.version + (it.maven2.classifier ? '-' + it.maven2.classifier : '') },
        pattern: RELEASE_VERSION
    )
}

List imageTags(String image) {
    return sortVersions(
        versions: dockerGetImageTags(image: image),
        pattern: RELEASE_VERSION
    )
}

String changeSummary() {
    return changes.collect { "${it.key}: ${it.current} -> ${params[it.param]}" }.join('\n')
}

String picked(value) {
    return value && !value.endsWith(KEEP) ? value : null
}

/**
 * The Jenkins build named by the first of `statuses` that names one, as linkUpstreamBuild arguments, or null.
 * GitLab Branch Source describes it as '<job full name> #<number>: <result>'; a queued build has no number.
 */
@NonCPS
Map statusBuild(List statuses) {
    for (status in statuses) {
        def match = (status.description ?: '') =~ /^(.+?) #(\d+): /
        if (match.find()) {
            return [project: match.group(1), build: match.group(2).toInteger()]
        }
    }
    return null
}

void refreshParameters() {
    manifest = gitlabGetFile(
        gitUrl: CFG.pluginRepo,
        filePath: 'Manifest',
        ref: 'master'
    )
    def parsed = readYaml(text: manifest.text)
    def plugins = parsed.plugins ?: [:]
    def environment = parsed.environment ?: [:]

    for (plugin in plugins.keySet().toList()) {
        fields << [
            section: 'plugins',
            key: plugin,
            param: plugin.toUpperCase().replace('-', '_') + '_VERSION',
            current: plugins[plugin].toString(),
            url: pluginTriggerUrl(plugin),
            versions: pluginVersions(plugin)
        ]
        def images = CFG.images[plugin] ?: [:]
        for (name in images.keySet().toList().findAll { environment.containsKey(it) }) {
            fields << [
                section: 'environment',
                key: name,
                param: name,
                current: environment[name].toString(),
                url: tagListUrl(images[name]),
                versions: imageTags(images[name])
            ]
        }
    }
    def grouped = fields.collect { it.key }
    def ungrouped = environment.keySet().findAll { !(it in grouped) }
    if (ungrouped) {
        echo("${ungrouped.join(', ')}: under no plugin in ${configFile} `images`, so no parameter.")
    }

    def definitions = []
    for (field in fields) {
        if (field.section == 'plugins') {
            definitions << separator(
                separatorStyle: 'border-width: 0',
                name: 'separator-' + field.key,
                sectionHeader: field.key,
                sectionHeaderStyle: '''
                    border-bottom: 2px solid #6fa3e1;
                    font-size: 16px;
                    font-weight: bold;
                    color: #6fa3e1;
                    margin-top: 10px;
                    margin-bottom: 10px;
                '''
            )
        }
        definitions << choice(
            name: field.param,
            choices: [field.current + KEEP] + field.versions,
            description: "'...${KEEP}' keeps whatever the Manifest holds when the build runs."
        )
    }
    // A new plugin version or image tag refreshes the lists. Only the versions of a search page and the
    // tags of a tag list are compared: a search page also holds lastDownloaded, which every download moves.
    // ponytail: properties() restarts the trigger, whose first poll only takes a baseline, so a version
    // published within 5 minutes after a build waits for the next change
    def triggers = []
    if (env.BRANCH_NAME == 'master') {
        def entries = []
        for (field in fields) {
            entries << URLTriggerEntry(
                url: field.url,
                timeout: 30,
                inspectingContent: true,
                contentTypes: [JsonContent(jsonPaths: [
                    JsonContentEntry(jsonPath: '$.items[*].maven2.version'),
                    JsonContentEntry(jsonPath: '$.tags')
                ])]
            )
        }
        triggers << URLTrigger(
            cronTabSpec: 'H/5 * * * *',
            entries: entries
        )
    }
    currentBuild.description = 'Update parameters'
    properties([
        parameters(definitions),
        pipelineTriggers(triggers)
    ])
}

void validateInputs() {
    changes = fields.findAll { picked(params[it.param]) && params[it.param] != it.current }
    def gone = changes.findAll { !(params[it.param] in it.versions) }
    if (gone) {
        error("No longer in Nexus: ${gone.collect { "${it.key} ${params[it.param]}" }.join(', ')}. The lists are refreshed now, choose again.")
    }
    echo(changes ? changeSummary() : 'Nothing to update: no picked version differs from the Manifest.')
}

void updateDeployment() {
    def chosen = [:]
    for (change in changes) {
        chosen[change.key] = params[change.param]
    }
    def result = applyManifestVersions(
        text: manifest.text,
        versions: chosen
    )

    def expected = readYaml(text: manifest.text)
    for (change in changes) {
        expected[change.section][change.key] = params[change.param]
    }
    if (readYaml(text: result.text) != expected) {
        error("Editing the Manifest would not give exactly ${chosen}. Nothing committed.")
    }

    String requester = currentBuild.getBuildCauses().find { it.userName }?.userName
    gitlabUpdateFile(
        gitUrl: CFG.pluginRepo,
        branch: 'master',
        filePath: 'Manifest',
        content: result.text,
        commitMessage: "Update versions from ${env.JOB_NAME} #${env.BUILD_NUMBER}\n\n${changeSummary()}\n\n" +
                       "Requested by ${requester}\n${env.BUILD_URL}\n\n" +
                       "Jenkins-Upstream-Project: ${env.JOB_NAME}\n" +
                       "Jenkins-Upstream-Build: ${env.BUILD_NUMBER}"
    )
    currentBuild.description = changeSummary().replace('\n', ', ')
}

void linkDeploymentBuild() {
    // Newest first, earlier builds of the commit too: a queued rebuild has no number yet.
    def statuses = gitlabApiGet(
        gitUrl: CFG.pluginRepo,
        path: "repository/commits/${manifest.commit_id}/statuses",
        query: [ref: 'master', all: true, sort: 'desc']
    )
    def meta = statusBuild(statuses)
    if (meta) {
        // reverse: the meta build gets this run as its upstream, so the graph reads configurator -> meta
        linkUpstreamBuild(meta + [reverse: true])
    } else {
        echo("${manifest.commit_id}: no Jenkins build in its GitLab statuses, so no link.")
    }
}

def call(Map arg = [:]) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)

    configFile = arg.CONFIG ?: 'jenkins.yml'
    CFG = validateConfig(readYaml(text: readTrusted(configFile)))

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
                steps {
                    refreshParameters()
                }
            }
            stage('Validate inputs') {
                when {
                    triggeredBy 'UserIdCause'
                }
                steps {
                    validateInputs()
                }
            }
            stage('Update deployment') {
                when {
                    allOf {
                        branch 'master'
                        triggeredBy 'UserIdCause'
                        expression { !changes.isEmpty() }
                    }
                }
                steps {
                    updateDeployment()
                }
            }
            stage('Link deployment build') {
                when {
                    allOf {
                        triggeredBy 'UserIdCause'
                        expression { changes.isEmpty() }
                    }
                }
                steps {
                    linkDeploymentBuild()
                }
            }
        }
    }
}
