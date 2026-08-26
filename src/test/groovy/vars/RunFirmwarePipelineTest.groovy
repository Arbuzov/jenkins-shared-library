package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class RunFirmwarePipelineTest extends BasePipelineTest {

    def script
    Map<String, String> files
    List echoMessages
    List buildChangesCalls
    List readTrustedCalls
    List repArtifactCalls
    String readYamlText
    List declaredProperties
    boolean pipelineDefined

    static final String YAML_TEXT = 'project: estella-hd\n'

    /** A config with everything runFirmwarePipeline requires. */
    static Map validConfig() {
        return [
            project          : 'estella-hd',
            jiraProject      : 'EH',
            mavenGroup       : 'com.spacebridge.sbc-firmware',
            releaseNotesSpace: 'PSIN',
            releaseNotesRoot : '575111187',
            installationSpace: 'PSIN',
            installationRoot : 'Host eStella-HD system installation',
            testSbcIp        : '192.168.65.188',
            keyComponents    : [
                'dataplane-phs-ng': [project: 'ASAT3-PHS/x/release_1.0.x', buildMask: '(?<=b)\\d+', pinned: true],
                'ncc'             : [project: 'NCC/y/master', buildMask: '(?<=b)\\d+', pinned: true],
                'watched-only'    : [project: 'OTHER/z/master', buildMask: '(?<=b)\\d+', pinned: false]
            ]
        ]
    }

    @Before
    void setUp() {
        super.setUp()
        files = [:]
        echoMessages = []
        buildChangesCalls = []
        readTrustedCalls = []
        repArtifactCalls = []
        readYamlText = null
        declaredProperties = null
        pipelineDefined = false
        binding.setVariable('env', [:])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('print', [Object], { Object o -> })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('readFile', [String], { String f ->
            if (!files.containsKey(f)) { throw new RuntimeException("no such file ${f}") }
            return files[f]
        })
        helper.registerAllowedMethod('findFiles', [Map], { Map args -> [[path: args.glob]] })
        helper.registerAllowedMethod('getJenkinsBuildChanges', [Map], { Map args ->
            buildChangesCalls << args
            return "<p>changes for ${args.PROJECT_ID} #${args.BUILD_NUMBER}</p>"
        })
        helper.registerAllowedMethod('readTrusted', [String], { String f ->
            readTrustedCalls << f
            return YAML_TEXT
        })
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            readYamlText = args.text
            return validConfig()
        })
        helper.registerAllowedMethod('getRepReproArtifacts', [Map], { Map args ->
            repArtifactCalls << args
            return args.packageNames.collectEntries { [(it): ['1.0.0-b1', '1.0.1-b2']] }
        })
        ['choice', 'separator', 'string', 'booleanParam'].each { name ->
            helper.registerAllowedMethod(name, [Map], { Map args -> args + [_kind: name] })
        }
        helper.registerAllowedMethod('parameters', [List], { List entries -> entries })
        helper.registerAllowedMethod('properties', [List], { List entries -> declaredProperties = entries })
        // stop call() at the pipeline block - Pipeline Unit cannot evaluate declarative gating
        helper.registerAllowedMethod('pipeline', [Closure], { Closure body -> pipelineDefined = true })
        script = loadScript('vars/runFirmwarePipeline.groovy')
    }

    /** The parameter entries call() handed to properties(), in render order. */
    List renderedParameters() {
        assert declaredProperties != null: 'properties() was never called'
        return declaredProperties[0]
    }

    // ---------- config validation ----------

    @Test
    void validateConfigAcceptsACompleteConfig() {
        assert script.validateConfig(validConfig()).project == 'estella-hd'
    }

    @Test
    void validateConfigRejectsAnEmptyFile() {
        try {
            script.validateConfig([:])
            assert false: 'expected an error'
        } catch (RuntimeException e) {
            assert e.message.contains('empty or could not be parsed')
        }
    }

    @Test
    void validateConfigNamesEveryMissingKeyAtOnce() {
        def config = validConfig()
        config.remove('jiraProject')
        config.remove('mavenGroup')
        try {
            script.validateConfig(config)
            assert false: 'expected an error'
        } catch (RuntimeException e) {
            // both, so a repo is not made to fix one key per build
            assert e.message.contains('jiraProject')
            assert e.message.contains('mavenGroup')
        }
    }

    @Test
    void validateConfigRejectsEmptyKeyComponents() {
        def config = validConfig()
        config.keyComponents = [:]
        try {
            script.validateConfig(config)
            assert false: 'expected an error'
        } catch (RuntimeException e) {
            assert e.message.contains('keyComponents must be a non-empty map')
        }
    }

    @Test
    void validateConfigRejectsAComponentWithoutProjectOrBuildMask() {
        def config = validConfig()
        config.keyComponents['broken'] = [pinned: true]
        try {
            script.validateConfig(config)
            assert false: 'expected an error'
        } catch (RuntimeException e) {
            assert e.message.contains('broken')
            assert e.message.contains('project and buildMask')
        }
    }

    // ---------- call(): config loading and generated parameters ----------

    @Test
    void callReadsTheConfigThroughReadTrustedAndParsesThatVeryText() {
        script.call()

        assert readTrustedCalls == ['jenkins.yml']
        assert readYamlText == YAML_TEXT
        assert pipelineDefined
    }

    @Test
    void callHonoursTheConfigOverride() {
        script.call(CONFIG: 'ci/firmware.yml')
        assert readTrustedCalls == ['ci/firmware.yml']
    }

    @Test
    void callFetchesThePackageIndexOnceForEveryPinnedComponent() {
        script.call()

        // one fetch, not one per component: the index is ~7 MB
        assert repArtifactCalls.size() == 1
        assert repArtifactCalls[0].packageNames == ['dataplane-phs-ng', 'ncc']
        assert repArtifactCalls[0].repository == 'bullseye'
    }

    @Test
    void callHonoursTheRepositoryOverride() {
        script.call(REPOSITORY: 'bookworm')
        assert repArtifactCalls[0].repository == 'bookworm'
    }

    @Test
    void callRendersTheParametersInOrderWithBothSections() {
        script.call()

        def rendered = renderedParameters()
        assert rendered*.name == [
            'separator-0',
            'DATAPLANE_PHS_NG_VERSION',
            'NCC_VERSION',
            'separator-1',
            'FIRMWARE_VERSION',
            'CREATE_GIT_TAG'
        ]
        assert rendered*._kind == [
            'separator', 'choice', 'choice', 'separator', 'string', 'booleanParam'
        ]
        assert rendered[0].sectionHeader == 'Software Versions'
        assert rendered[3].sectionHeader == 'Release Options'
    }

    @Test
    void aComponentChoiceLeadsWithAnEmptyOptionMeaningKeepTheManifestVersion() {
        script.call()

        def choice = renderedParameters().find { it.name == 'NCC_VERSION' }
        assert choice.choices == ['', '1.0.0-b1', '1.0.1-b2']
        assert choice.description.contains('Manifest')
    }

    @Test
    void releaseOptionsKeepTheirDefaults() {
        script.call()

        def rendered = renderedParameters()
        assert rendered.find { it.name == 'FIRMWARE_VERSION' }.defaultValue == ''
        assert rendered.find { it.name == 'CREATE_GIT_TAG' }.defaultValue == false
    }

    @Test
    void nothingPinnedRendersNoSoftwareVersionsSection() {
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            def config = validConfig()
            config.keyComponents.each { name, component -> component.pinned = false }
            return config
        })

        script.call()

        assert repArtifactCalls.isEmpty()
        assert renderedParameters()*.name == ['separator-1', 'FIRMWARE_VERSION', 'CREATE_GIT_TAG']
        assert !renderedParameters().any { it.sectionHeader == 'Software Versions' }
    }

    @Test
    void callRejectsAnInvalidConfigBeforeRenderingAnyParameter() {
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            def config = validConfig()
            config.remove('mavenGroup')
            return config
        })

        try {
            script.call()
            assert false: 'expected an error'
        } catch (RuntimeException e) {
            assert e.message.contains('mavenGroup')
        }
        assert declaredProperties == null
        assert !pipelineDefined
    }

    // ---------- parameter names ----------

    @Test
    void paramNameUppercasesAndReplacesDashes() {
        assert script.paramName('dataplane-phs-ng') == 'DATAPLANE_PHS_NG_VERSION'
        assert script.paramName('ncc') == 'NCC_VERSION'
        assert script.paramName('mcd4-lite') == 'MCD4_LITE_VERSION'
        assert script.paramName('ulpc') == 'ULPC_VERSION'
    }

    // ---------- pinned vs tracked ----------

    @Test
    void pinnedComponentsSelectsOnlyPinnedOnes() {
        script.@CFG = validConfig()
        assert script.pinnedComponents().keySet() as List == ['dataplane-phs-ng', 'ncc']
    }

    @Test
    void aComponentWithoutTheFlagIsTrackedOnly() {
        def config = validConfig()
        config.keyComponents['no-flag'] = [project: 'P/q/master', buildMask: '(?<=b)\\d+']
        script.@CFG = config
        // absent means tracked-only: no parameter, never written to Manifest
        assert !script.pinnedComponents().containsKey('no-flag')
        assert script.@CFG.keyComponents.containsKey('no-flag')
    }

    @Test
    void nothingPinnedYieldsNoParameters() {
        def config = validConfig()
        config.keyComponents.each { name, component -> component.pinned = false }
        script.@CFG = config
        assert script.pinnedComponents().isEmpty()
    }

    // ---------- build retention ----------

    @Test
    void aBranchBuildKeepsAnAuditableHistoryAndFewerArtifacts() {
        // SNG-538: three builds discarded the release history before it could be audited
        assert script.buildRetention() == [builds: '30', artifacts: '10']
    }

    @Test
    void aMergeRequestBuildKeepsTheShallowHistory() {
        binding.setVariable('env', [CHANGE_ID: '87'])
        assert script.buildRetention() == [builds: '3', artifacts: '3']
    }

    // ---------- nexus coordinates ----------

    @Test
    void artifactVersionStripsTheProjectPrefix() {
        script.@CFG = validConfig()
        script.@VERSION = 'estella-hd-1.1.2-b152'
        assert script.artifactVersion() == '1.1.2-b152'
    }

    @Test
    void releaseUrlUsesTheGroupPathNotTheDottedGroup() {
        script.@CFG = validConfig()
        script.@GROUP_PATH = 'com/spacebridge/sbc-firmware'
        script.@VERSION = 'estella-hd-1.1.2-b152'
        assert script.releaseUrl('iso') ==
            'https://nexus.asatnet.net/repository/maven-releases/com/spacebridge/sbc-firmware' +
            '/estella-hd/1.1.2-b152/estella-hd-1.1.2-b152.iso'
    }

    // ---------- changelog ----------

    @Test
    void keyComponentsInfoLooksUpBuildChangesForEveryMatchedComponent() {
        script.@CFG = validConfig()
        files['artifacts-sbc/syft_report.txt'] = [
            'dataplane-phs-ng   1.0.23-b12   deb',
            'ncc                5.2.20-b264  deb',
            'watched-only       9.9.9-b7     deb'
        ].join('\n')

        def info = script.getKeyComponentsInfo().join('')

        // tracked-only components are reported too - the changelog is what we report on
        assert buildChangesCalls*.BUILD_NUMBER == ['12', '264', '7']
        assert info.contains('dataplane-phs-ng&nbsp;1.0.23-b12')
        assert info.contains('watched-only&nbsp;9.9.9-b7')
    }

    @Test
    void keyComponentsInfoSurvivesASingleColumnSyftLine() {
        script.@CFG = validConfig()
        // a line with no second field used to throw ArrayIndexOutOfBounds here,
        // which killed the release notes after the artifacts were published
        files['artifacts-sbc/syft_report.txt'] = 'ncc\ndataplane-phs-ng 1.0.23-b12 deb\nwatched-only 1 deb'

        def info = script.getKeyComponentsInfo().join('')

        assert info.contains('Build for unknown not found')
        assert info.contains('dataplane-phs-ng&nbsp;1.0.23-b12')
    }

    @Test
    void keyComponentsInfoReportsAComponentMissingFromTheReport() {
        script.@CFG = validConfig()
        files['artifacts-sbc/syft_report.txt'] = 'something-else 1.0.0 deb'
        assert script.getKeyComponentsInfo().every { it.contains('Build for unknown not found') }
        assert buildChangesCalls.isEmpty()
    }

    // ---------- package list diffing ----------

    @Test
    void computeDifferencesSplitsAddedRemovedAndUpdated() {
        def previous = [keep: '1.0', drop: '2.0', bump: '3.0']
        def current = [keep: '1.0', bump: '3.1', fresh: '4.0']
        def diff = script.computeDifferences(previous, current)
        assert diff.added == [fresh: '4.0']
        assert diff.removed == [drop: '2.0']
        assert diff.updated == [bump: '3.1']
    }

    @Test
    void parsePackageListFromFileIgnoresShortLines() {
        files['artifacts-sbc/syft_report.txt'] = 'ncc 5.2.20 deb\nbroken\nulpc 5.2.11 deb'
        def packages = script.parsePackageListFromFile('artifacts-sbc/syft_report.txt')
        assert packages == [ncc: '5.2.20', ulpc: '5.2.11']
    }

    @Test
    void parsePackageListFromFileDegradesToEmptyWhenTheReportIsMissing() {
        // loadReport throws for a missing glob; the step must not take the build down
        assert script.parsePackageListFromFile('artifacts-sbc/nope.txt') == [:]
        assert echoMessages.any { it.contains('Failed to read or parse artifact') }
    }

    // ---------- misc helpers ----------

    @Test
    void secureStringEscapesEveryNewlineFlavour() {
        assert script.secureString('a\r\nb\rc\nd') == 'a\\nb\\nc\\nd'
    }

    @Test
    void readCurrentVersionTrimsAndSurvivesAMissingFile() {
        files['VERSION'] = "1.1.2\n"
        assert script.readCurrentVersion() == '1.1.2'
        files.remove('VERSION')
        assert script.readCurrentVersion() == ''
    }
}
