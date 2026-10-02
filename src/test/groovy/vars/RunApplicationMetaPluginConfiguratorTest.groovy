package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class RunApplicationMetaPluginConfiguratorTest extends BasePipelineTest {

    static final String REPO = 'https://gitlab.asatnet.net/vsat/nms/virtual-platform/nms-single-meta-deployment'
    static final String META_JOB = 'VSAT-NMS-VirtualPlatform/vsat%2Fnms%2Fvirtual-platform%2Fnms-single-meta-deployment/master'
    static final String MANIFEST_TEXT = 'name: nms-single-meta\r\nplugins:\r\n  nms-ng: 2.0.3-b69-offline\r\n' +
        '  nms-monitoring: 2.5.3-b90\r\nenvironment:\r\n  NMS_BACKEND_VERSION: 1.0.0-b694\r\n' +
        '  NMS_FRONTEND_VERSION: 1.1.1-b603\r\n  STRAY_VERSION: 1.0.0-b1\r\n'
    static final String EDITED_TEXT = 'edited manifest'

    def script
    List echoMessages
    List readTrustedCalls
    List gitlabGetFileCalls
    List nexusQueries
    List imageCalls
    List sortCalls
    List applyCalls
    List updateCalls
    List apiGetCalls
    List linkCalls
    List declaredProperties
    boolean pipelineDefined
    Map yamlByText
    List statuses

    static Map validConfig() {
        return [
            pluginRepo: REPO,
            images    : [
                'nms-ng': [NMS_BACKEND_VERSION: 'spacebridge/nms-ng-backend', NMS_FRONTEND_VERSION: 'spacebridge/nms-ng-frontend']
            ]
        ]
    }

    static Map parsedManifest() {
        return [
            name       : 'nms-single-meta',
            plugins    : ['nms-ng': '2.0.3-b69-offline', 'nms-monitoring': '2.5.3-b90'],
            environment: [NMS_BACKEND_VERSION: '1.0.0-b694', NMS_FRONTEND_VERSION: '1.1.1-b603', STRAY_VERSION: '1.0.0-b1']
        ]
    }

    static Map asset(String version, String classifier = null) {
        return [path: "x/${version}", maven2: [version: version, classifier: classifier]]
    }

    @Before
    void setUp() {
        super.setUp()
        echoMessages = []
        readTrustedCalls = []
        gitlabGetFileCalls = []
        nexusQueries = []
        imageCalls = []
        sortCalls = []
        applyCalls = []
        updateCalls = []
        apiGetCalls = []
        linkCalls = []
        declaredProperties = null
        pipelineDefined = false
        yamlByText = [(MANIFEST_TEXT): parsedManifest()]
        statuses = []
        binding.setVariable('env', [BRANCH_NAME: 'master', JOB_NAME: 'configurator/master', BUILD_NUMBER: '12',
                                    BUILD_URL: 'https://jenkins.example/job/configurator/12/'])
        binding.setVariable('params', [:])
        binding.setVariable('currentBuild', [description: null, getBuildCauses: { -> [[shortDescription: 'x'], [userName: 'Serge']] }])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('readTrusted', [String], { String file ->
            readTrustedCalls << file
            return 'pluginRepo: x\n'
        })
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            if (args.text == 'pluginRepo: x\n') {
                return validConfig()
            }
            assert yamlByText.containsKey(args.text): "no YAML fixture for ${args.text}"
            // a fresh copy per read, as the real step parses the text again
            return yamlByText[args.text].collectEntries { key, value -> [(key): value instanceof Map ? new LinkedHashMap(value) : value] }
        })
        helper.registerAllowedMethod('gitlabGetFile', [Map], { Map args ->
            gitlabGetFileCalls << args
            return [text: MANIFEST_TEXT, commit_id: '18a106ea', last_commit_id: '18a106ea']
        })
        helper.registerAllowedMethod('nexusSearchAssets', [Map], { Map args ->
            nexusQueries << args.query
            if (args.query['maven.artifactId'] == 'nms-ng') {
                return [asset('2.0.3-b70', 'offline'), asset('2.0.3-b70'), asset('2.0.3-b69', 'offline')]
            }
            return [asset('2.5.3-b91'), asset('2.5.3-b90')]
        })
        helper.registerAllowedMethod('dockerGetImageTags', [Map], { Map args ->
            imageCalls << args
            return args.image.endsWith('backend') ? ['1.0.0-b695', '1.0.0-b694'] : ['1.1.1-b604', '1.1.1-b603']
        })
        helper.registerAllowedMethod('sortVersions', [Map], { Map args ->
            sortCalls << args
            return args.versions.reverse()
        })
        ['choice', 'separator'].each { name ->
            helper.registerAllowedMethod(name, [Map], { Map args -> args + [_kind: name] })
        }
        ['URLTrigger', 'URLTriggerEntry', 'JsonContent', 'JsonContentEntry'].each { name ->
            helper.registerAllowedMethod(name, [Map], { Map args -> args + [_kind: name] })
        }
        helper.registerAllowedMethod('parameters', [List], { List entries -> [parameters: entries] })
        helper.registerAllowedMethod('pipelineTriggers', [List], { List entries -> [pipelineTriggers: entries] })
        helper.registerAllowedMethod('properties', [List], { List entries -> declaredProperties = entries })
        helper.registerAllowedMethod('applyManifestVersions', [Map], { Map args ->
            applyCalls << args
            return [text: EDITED_TEXT, changed: args.versions]
        })
        helper.registerAllowedMethod('gitlabUpdateFile', [Map], { Map args ->
            updateCalls << args
            return [file_path: 'Manifest', branch: 'master']
        })
        helper.registerAllowedMethod('gitlabApiGet', [Map], { Map args ->
            apiGetCalls << args
            return statuses
        })
        helper.registerAllowedMethod('linkUpstreamBuild', [Map], { Map args ->
            linkCalls << args
            return true
        })
        // stop call() at the pipeline block - Pipeline Unit cannot evaluate triggeredBy
        helper.registerAllowedMethod('pipeline', [Closure], { Closure body -> pipelineDefined = true })
        script = loadScript('vars/runApplicationMetaPluginConfigurator.groovy')
    }

    /** Runs the first stage the way call() would leave the script for it. */
    void refresh() {
        script.@CFG = script.validateConfig(validConfig())
        script.refreshParameters()
    }

    List renderedParameters() {
        assert declaredProperties != null: 'properties() was never called'
        return declaredProperties[0].parameters
    }

    List renderedTriggers() {
        return declaredProperties[1].pipelineTriggers
    }

    // ---------- config ----------

    @Test
    void validateConfigReturnsTheRepositoryAndTheImages() {
        def config = script.validateConfig(validConfig())
        assert config.pluginRepo == REPO
        assert config.images['nms-ng'].NMS_BACKEND_VERSION == 'spacebridge/nms-ng-backend'
    }

    @Test
    void validateConfigDefaultsImagesToNone() {
        assert script.validateConfig([pluginRepo: REPO]).images == [:]
    }

    @Test
    void validateConfigRejectsAnEmptyFile() {
        try {
            script.validateConfig(null)
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('empty or could not be parsed')
        }
    }

    @Test
    void validateConfigRejectsAMissingRepository() {
        try {
            script.validateConfig([images: [:]])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('pluginRepo must be the URL of the meta plugin repository')
        }
    }

    @Test
    void validateConfigRejectsImagesThatAreNotAMapOfVariables() {
        try {
            script.validateConfig([pluginRepo: REPO, images: ['nms-ng': 'spacebridge/nms-ng-backend']])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('images must map a plugin')
        }
    }

    @Test
    void validateConfigRejectsAnImageWithoutAName() {
        try {
            script.validateConfig([pluginRepo: REPO, images: ['nms-ng': [NMS_BACKEND_VERSION: null]]])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('images must map a plugin')
        }
    }

    @Test
    void validateConfigNamesAnUnknownKeyAndEveryOtherProblemAtOnce() {
        try {
            script.validateConfig([upstream: ['some/job/master'], images: 'nms-ng'])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('unknown keys upstream, allowed: pluginRepo, images')
            assert exception.message.contains('pluginRepo must be')
            assert exception.message.contains('images must map')
        }
    }

    // ---------- call ----------

    @Test
    void callReadsTheTrustedConfigBeforeThePipeline() {
        script.call()

        assert readTrustedCalls == ['jenkins.yml']
        assert script.@CFG.pluginRepo == REPO
        assert pipelineDefined
    }

    @Test
    void callReadsAnotherConfigFile() {
        script.call(CONFIG: 'configurator.yml')

        assert readTrustedCalls == ['configurator.yml']
    }

    @Test
    void callFailsOnABadConfigBeforeThePipeline() {
        helper.registerAllowedMethod('readYaml', [Map], { Map args -> [pluginRepo: REPO, upstream: []] })
        try {
            script.call()
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('unknown keys upstream')
        }
        assert !pipelineDefined
    }

    // ---------- Update configuration ----------

    @Test
    void refreshReadsTheManifestOfMasterAndKeepsTheCommit() {
        refresh()

        assert gitlabGetFileCalls == [[gitUrl: REPO, filePath: 'Manifest', ref: 'master']]
        assert script.@manifest.commit_id == '18a106ea'
    }

    @Test
    void refreshListsPluginVersionsWithTheirClassifier() {
        refresh()

        assert nexusQueries == [
            [repository: 'maven-releases', 'maven.groupId': 'com.spacebridge.application-plugin',
             'maven.artifactId': 'nms-ng', 'maven.extension': 'tar.gz'],
            [repository: 'maven-releases', 'maven.groupId': 'com.spacebridge.application-plugin',
             'maven.artifactId': 'nms-monitoring', 'maven.extension': 'tar.gz']
        ]
        assert sortCalls[0].versions == ['2.0.3-b70-offline', '2.0.3-b70', '2.0.3-b69-offline']
        assert sortCalls*.pattern.unique() == [/\d+\.\d+\.\d+-b\d+(-offline)?/]
    }

    @Test
    void refreshListsOnlyTheImagesWhoseVariableIsInTheManifest() {
        script.@CFG = script.validateConfig([pluginRepo: REPO, images: ['nms-ng': [
            NMS_BACKEND_VERSION: 'spacebridge/nms-ng-backend', NMS_GONE_VERSION: 'spacebridge/gone']]])

        script.refreshParameters()

        assert imageCalls*.image == ['spacebridge/nms-ng-backend']
        assert !('NMS_GONE_VERSION' in renderedParameters()*.name)
        assert !renderedTriggers()[0].entries*.url.any { it.contains('spacebridge/gone') }
    }

    @Test
    void refreshListsTheTagsOfTheImagesOfAPlugin() {
        refresh()

        assert imageCalls*.image == ['spacebridge/nms-ng-backend', 'spacebridge/nms-ng-frontend']
    }

    @Test
    void refreshRendersEachPluginWithItsImagesKeepFirst() {
        refresh()

        def rendered = renderedParameters()
        assert rendered*.name == ['separator-nms-ng', 'NMS_NG_VERSION', 'NMS_BACKEND_VERSION', 'NMS_FRONTEND_VERSION',
                                  'separator-nms-monitoring', 'NMS_MONITORING_VERSION']
        assert rendered*._kind == ['separator', 'choice', 'choice', 'choice', 'separator', 'choice']
        assert rendered[0].sectionHeader == 'nms-ng'
        assert rendered[1].choices == ['2.0.3-b69-offline (current)', '2.0.3-b69-offline', '2.0.3-b70', '2.0.3-b70-offline']
        assert rendered[2].choices == ['1.0.0-b694 (current)', '1.0.0-b694', '1.0.0-b695']
        assert rendered[5].choices == ['2.5.3-b90 (current)', '2.5.3-b90', '2.5.3-b91']
    }

    @Test
    void refreshNamesAnEnvironmentVariableUnderNoPlugin() {
        refresh()

        assert echoMessages == ['STRAY_VERSION: under no plugin in jenkins.yml `images`, so no parameter.']
    }

    @Test
    void refreshNamesTheConfigFileTheBuildRead() {
        script.call(CONFIG: 'configurator.yml')

        script.refreshParameters()

        assert echoMessages == ['STRAY_VERSION: under no plugin in configurator.yml `images`, so no parameter.']
    }

    @Test
    void refreshPollsNexusAndTheRegistryOnMaster() {
        refresh()

        def triggers = renderedTriggers()
        assert triggers.size() == 1
        assert triggers[0]._kind == 'URLTrigger'
        assert triggers[0].cronTabSpec == 'H/5 * * * *'
        assert triggers[0].entries*.url == [
            'https://nexus.asatnet.net/service/rest/v1/search/assets?repository=maven-releases' +
                '&maven.groupId=com.spacebridge.application-plugin&maven.artifactId=nms-ng&maven.extension=tar.gz' +
                '&sort=version&direction=desc',
            'https://registry.nexus.asatnet.net/v2/spacebridge/nms-ng-backend/tags/list',
            'https://registry.nexus.asatnet.net/v2/spacebridge/nms-ng-frontend/tags/list',
            'https://nexus.asatnet.net/service/rest/v1/search/assets?repository=maven-releases' +
                '&maven.groupId=com.spacebridge.application-plugin&maven.artifactId=nms-monitoring&maven.extension=tar.gz' +
                '&sort=version&direction=desc'
        ]
        assert triggers[0].entries.every { it.timeout == 30 && it.inspectingContent }
        assert triggers[0].entries[0].contentTypes[0].jsonPaths*.jsonPath == ['$.items[*].maven2.version', '$.tags']
        assert binding.getVariable('currentBuild').description == 'Update parameters'
    }

    @Test
    void refreshSetsNoTriggerOnOtherBranches() {
        binding.setVariable('env', [BRANCH_NAME: 'MR-6'])

        refresh()

        assert renderedTriggers() == []
    }

    // ---------- Validate inputs ----------

    @Test
    void validateInputsKeepsOnlyPicksThatDifferFromTheManifest() {
        refresh()
        binding.setVariable('params', [
            NMS_NG_VERSION        : '2.0.3-b70-offline',
            NMS_BACKEND_VERSION   : '1.0.0-b694',
            NMS_FRONTEND_VERSION  : '1.1.1-b603 (current)',
            NMS_MONITORING_VERSION: '2.5.3-b91'
        ])

        script.validateInputs()

        assert script.@changes*.key == ['nms-ng', 'nms-monitoring']
        assert echoMessages.last() == 'nms-ng: 2.0.3-b69-offline -> 2.0.3-b70-offline\nnms-monitoring: 2.5.3-b90 -> 2.5.3-b91'
    }

    @Test
    void validateInputsSaysWhenNothingChanges() {
        refresh()
        binding.setVariable('params', [NMS_NG_VERSION: '2.0.3-b69-offline (current)'])

        script.validateInputs()

        assert script.@changes == []
        assert echoMessages.last() == 'Nothing to update: no picked version differs from the Manifest.'
    }

    @Test
    void validateInputsFailsForAVersionNoLongerInNexus() {
        refresh()
        binding.setVariable('params', [NMS_NG_VERSION: '2.0.3-b1'])

        try {
            script.validateInputs()
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message == 'No longer in Nexus: nms-ng 2.0.3-b1. The lists are refreshed now, choose again.'
        }
    }

    // ---------- Update deployment ----------

    void pickNewBackend() {
        refresh()
        binding.setVariable('params', [NMS_NG_VERSION: '2.0.3-b70', NMS_BACKEND_VERSION: '1.0.0-b695'])
        script.validateInputs()
    }

    @Test
    void updateDeploymentCommitsTheEditedManifest() {
        pickNewBackend()
        def expected = parsedManifest()
        expected.plugins['nms-ng'] = '2.0.3-b70'
        expected.environment.NMS_BACKEND_VERSION = '1.0.0-b695'
        yamlByText[EDITED_TEXT] = expected

        script.updateDeployment()

        assert applyCalls == [[text: MANIFEST_TEXT, versions: ['nms-ng': '2.0.3-b70', NMS_BACKEND_VERSION: '1.0.0-b695']]]
        assert updateCalls.size() == 1
        def update = updateCalls[0]
        assert update.gitUrl == REPO
        assert update.branch == 'master'
        assert update.filePath == 'Manifest'
        assert update.content == EDITED_TEXT
        assert update.commitMessage.toString() == 'Update versions from configurator/master #12\n\n' +
            'nms-ng: 2.0.3-b69-offline -> 2.0.3-b70\nNMS_BACKEND_VERSION: 1.0.0-b694 -> 1.0.0-b695\n\n' +
            'Requested by Serge\nhttps://jenkins.example/job/configurator/12/\n\n' +
            'Jenkins-Upstream-Project: configurator/master\nJenkins-Upstream-Build: 12'
        assert binding.getVariable('currentBuild').description ==
            'nms-ng: 2.0.3-b69-offline -> 2.0.3-b70, NMS_BACKEND_VERSION: 1.0.0-b694 -> 1.0.0-b695'
    }

    @Test
    void updateDeploymentCommitsNothingWhenTheEditMissesAValue() {
        pickNewBackend()
        def edited = parsedManifest()
        edited.plugins['nms-ng'] = '2.0.3-b70'
        yamlByText[EDITED_TEXT] = edited

        try {
            script.updateDeployment()
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.startsWith('Editing the Manifest would not give exactly')
        }
        assert updateCalls == []
    }

    // ---------- Link deployment build ----------

    @Test
    void linkDeploymentBuildLinksTheBuildOfTheManifestCommitAsDownstream() {
        refresh()
        statuses = [[description: null], [description: "${META_JOB}: Build queued..."],
                    [description: "${META_JOB} #34: This commit looks good."]]

        script.linkDeploymentBuild()

        assert apiGetCalls == [[gitUrl: REPO, path: 'repository/commits/18a106ea/statuses',
                                query: [ref: 'master', all: true, sort: 'desc']]]
        assert linkCalls == [[project: META_JOB, build: 34, reverse: true]]
    }

    @Test
    void linkDeploymentBuildLinksNothingWithoutANumberedBuild() {
        refresh()
        statuses = [[description: "${META_JOB}: Build queued..."]]

        script.linkDeploymentBuild()

        assert linkCalls == []
        assert echoMessages.last() == '18a106ea: no Jenkins build in its GitLab statuses, so no link.'
    }

    @Test
    void statusBuildTakesTheFirstStatusThatNamesABuild() {
        assert script.statusBuild([[description: null], [description: "${META_JOB}: Build queued..."],
                                   [description: "${META_JOB} #34: This commit looks good."]]) == [project: META_JOB, build: 34]
        assert script.statusBuild([[description: "${META_JOB}: Build queued..."]]) == null
        assert script.statusBuild([]) == null
    }
}
