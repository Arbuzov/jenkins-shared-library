package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import org.junit.Before
import org.junit.Test

class RunSmbToNexusPipelineTest extends BasePipelineTest {

    def script
    List echoMessages
    List readTrustedCalls
    List nexusQueries
    Set uploadedVersions
    String shareListing
    Boolean zipIntact
    List zipContent
    List summaries
    List unstableMessages
    boolean pipelineDefined

    /** smbclient 'dir' output, trimmed from FPGA_Releases. */
    static final String LISTING = '''\
  .                                   D        0  Fri Sep 25 22:18:48 2026
  ..                                  D        0  Mon Jun 10 12:00:00 2024
  mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip      A 58451032  Thu Sep 24 20:55:29 2026
  mcd4_fpga1_lite_20240424_cf9ddd97 .zip      A 54667058  Wed Apr 24 12:00:00 2024
  mcdma_pcie_data_test_agilex_25Oct2022_07_46.zip      A 1048576  Tue Oct 25 07:46:00 2022
  release_notes.txt                   A   129565  Fri May 22 16:08:12 2026

		1073741823 blocks of size 4096. 52428800 blocks available
'''

    /** release_notes.txt entries, as FPGA engineers write them. */
    static final String RELEASE_NOTES = '''\
146.\tmcd4_fpga1_lite_20240424_cf9ddd97 (Master branch)\r
\t\tGIT Commit Number: cf9ddd97 (git@gitlab.asatnet.net/mcd4/pl/fpga.git)\r
182.\tmcd4_fpga1_lite_4ch_20250415_2f602630 (Master branch) (Original folder is mcd4_fpga1_lite_4ch_20250415_130105_2f602630)\r
2.\tmcd4_fpga1_lite_4ch_20260224_261cf822_Digit_LPBK_Support (Branch MCD4_Lite_V1)\r
60.\tmcd4_fpga1_lite_4ch_20260924_105947_955a6270 (Branch FPGA_develop_Branch_1)\r
'''

    static Map validConfig() {
        return [
            smbDirectory   : 'ADVSAT/MCD/MCD_Rel40/FPGA_Releases',
            nexusRepository: 'maven-releases',
            groupId        : 'com.spacebridge.mcd.rel40.fpga',
            requiredFiles  : ['*.core.rbf', 'top.sof']
        ]
    }

    static Map archive(String file) {
        return [file: file, artifactId: 'mcd4_fpga1_lite_4ch', version: '20260924_105947_955a6270']
    }

    @Before
    void setUp() {
        super.setUp()
        echoMessages = []
        readTrustedCalls = []
        nexusQueries = []
        uploadedVersions = [] as Set
        shareListing = LISTING
        zipIntact = true
        zipContent = []
        summaries = []
        unstableMessages = []
        pipelineDefined = false
        binding.setVariable('env', [:])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('readTrusted', [String], { String file ->
            readTrustedCalls << file
            return 'smbDirectory: x\n'
        })
        helper.registerAllowedMethod('readYaml', [Map], { Map args -> validConfig() })
        helper.registerAllowedMethod('sh', [Map], { Map args -> args.returnStdout ? shareListing : null })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            nexusQueries << args.url
            def version = (args.url =~ /maven\.baseVersion=([^&]+)/)[0][1]
            def items = uploadedVersions.contains(version) ? [[path: version]] : []
            return [content: JsonOutput.toJson([items: items])]
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            assert args.returnPojo == true
            return new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('withEnv', [List, Closure], { List vars, Closure body -> body() })
        helper.registerAllowedMethod('unzip', [Map], { Map args -> args.test ? zipIntact : null })
        helper.registerAllowedMethod('findFiles', [Map], { Map args ->
            if (args.glob == 'content/**') {
                return zipContent
            }
            def fileGlob = args.glob - 'content/**/'
            def regex = fileGlob.replace('.', '\\.').replace('*', '.*')
            return zipContent.findAll { (it.path as String).tokenize('/').last() ==~ regex }
        })
        helper.registerAllowedMethod('dir', [String, Closure], { String path, Closure body -> body() })
        helper.registerAllowedMethod('deleteDir', [], { })
        helper.registerAllowedMethod('sha256', [Map], { Map args -> 'c0ffee' })
        helper.registerAllowedMethod('libraryResource', [String], { String path ->
            new File("resources/${path}").getText('UTF-8')
        })
        helper.registerAllowedMethod('inflateTemplate', [Map], { Map args ->
            new SimpleTemplateEngine().createTemplate(args.TEMPLATE).make(args).toString()
        })
        helper.registerAllowedMethod('publishSummary', [String], { String text -> summaries << text })
        helper.registerAllowedMethod('readFile', [String], { String file -> RELEASE_NOTES })
        helper.registerAllowedMethod('catchError', [Map, Closure], { Map args, Closure body -> body() })
        helper.registerAllowedMethod('unstable', [String], { String message -> unstableMessages << message })
        // stop call() at the pipeline block - Pipeline Unit cannot evaluate declarative gating
        helper.registerAllowedMethod('pipeline', [Closure], { Closure body -> pipelineDefined = true })
        script = loadScript('vars/runSmbToNexusPipeline.groovy')
    }

    // ---------- config ----------

    @Test
    void validateConfigFillsInTheDefaults() {
        def config = script.validateConfig(validConfig())
        assert config.smbShare == '//asatvault.asatnet.net/SATNET_DATA_1'
        assert config.versionSuffix == ''
        assert config.archivePattern == '(mcd|MCD).*\\.zip'
        assert config.groupId == 'com.spacebridge.mcd.rel40.fpga'
    }

    @Test
    void validateConfigKeepsAValueTheConfigSets() {
        def config = script.validateConfig(validConfig() + [versionSuffix: '-SNAPSHOT'])
        assert config.versionSuffix == '-SNAPSHOT'
    }

    @Test
    void validateConfigRejectsAnEmptyFile() {
        try {
            script.validateConfig([:])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('empty or could not be parsed')
        }
    }

    @Test
    void validateConfigNamesEveryMissingKeyAtOnce() {
        def config = validConfig()
        config.remove('groupId')
        config.remove('requiredFiles')
        try {
            script.validateConfig(config)
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('groupId')
            assert exception.message.contains('requiredFiles')
        }
    }

    @Test
    void validateConfigRejectsRequiredFilesThatIsNotAList() {
        try {
            script.validateConfig(validConfig() + [requiredFiles: '*.core.rbf'])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('requiredFiles must be a list')
        }
    }

    @Test
    void validateConfigAcceptsAnEmptyRequiredFilesList() {
        assert script.validateConfig(validConfig() + [requiredFiles: []]).requiredFiles == []
    }

    // ---------- archive names ----------

    @Test
    void listArchivesPicksTheArchivesOutOfTheListing() {
        assert script.listArchives(LISTING, '(mcd|MCD).*\\.zip') == [
            'mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip',
            'mcd4_fpga1_lite_20240424_cf9ddd97 .zip',
            'mcdma_pcie_data_test_agilex_25Oct2022_07_46.zip'
        ]
    }

    @Test
    void listArchivesTakesTheWholeMatchOfAPatternWithoutGroups() {
        assert script.listArchives(LISTING, '\\S+\\.txt') == ['release_notes.txt']
    }

    @Test
    void theDefaultPatternsSplitAnArchiveName() {
        def defaults = script.validateConfig(validConfig())
        def cases = [
            'mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip': ['mcd4_fpga1_lite_4ch', '20260924_105947_955a6270'],
            'mcd4_fpga1_lite_20240424_cf9ddd97 .zip'           : ['mcd4_fpga1_lite', '20240424_cf9ddd97'],
            'mcd4_fpga1_20220811_d0bf5601++_eval.zip'          : ['mcd4_fpga1', '20220811_d0bf5601'],
            'mcdma_pcie_data_test_agilex_25Oct2022_07_46.zip'  : ['', ''],
            'MCD4_FPGA1_20220610_2100_b0e94c6.zip'             : ['MCD4_FPGA1', '']
        ]
        cases.each { name, expected ->
            assert [script.firstMatch(name, defaults.artifactIdPattern), script.firstMatch(name, defaults.versionPattern)] == expected
        }
    }

    // ---------- release notes ----------

    @Test
    void hasReleaseNoteFindsANumberedEntry() {
        assert script.hasReleaseNote(RELEASE_NOTES, 'mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip')
    }

    @Test
    void hasReleaseNoteIgnoresASpaceBeforeTheExtension() {
        assert script.hasReleaseNote(RELEASE_NOTES, 'mcd4_fpga1_lite_20240424_cf9ddd97 .zip')
    }

    @Test
    void hasReleaseNoteDoesNotTakeAPrefixOfALongerName() {
        assert !script.hasReleaseNote(RELEASE_NOTES, 'mcd4_fpga1_lite_4ch_20260224_261cf822.zip')
    }

    @Test
    void hasReleaseNoteDoesNotTakeAMentionInsideAnotherEntry() {
        assert !script.hasReleaseNote(RELEASE_NOTES, 'mcd4_fpga1_lite_4ch_20250415_130105_2f602630.zip')
    }

    // ---------- scan ----------

    @Test
    void scanShareKeepsTheArchivesNotInNexusWithTheVersionSuffix() {
        script.@CFG = script.validateConfig(validConfig() + [nexusRepository: 'maven-snapshots', versionSuffix: '-SNAPSHOT'])
        script.@NEXUS_CREDENTIALS_ID = 'jira-username-password'
        uploadedVersions << '20260924_105947_955a6270-SNAPSHOT'

        script.scanShare()

        assert script.@newArchives == [
            [file: 'mcd4_fpga1_lite_20240424_cf9ddd97 .zip', artifactId: 'mcd4_fpga1_lite', version: '20240424_cf9ddd97-SNAPSHOT']
        ]
        // the unparsable name is never looked up
        assert nexusQueries.size() == 2
        assert nexusQueries.every { it.contains('repository=maven-snapshots') }
        assert echoMessages.contains('1 of 3 archives are not in maven-snapshots yet')
    }

    @Test
    void scanShareWithoutASuffixQueriesTheBareVersion() {
        script.@CFG = script.validateConfig(validConfig() + [versionSuffix: null])
        uploadedVersions << '20260924_105947_955a6270'

        script.scanShare()

        assert nexusQueries.any { it.contains('maven.baseVersion=20240424_cf9ddd97&') }
        assert !nexusQueries.any { it.contains('null') }
        assert script.@newArchives*.version == ['20240424_cf9ddd97']
    }

    // ---------- analysis ----------

    @Test
    void analyzePassesAnArchiveWithEveryFileAndANote() {
        script.@CFG = script.validateConfig(validConfig())
        zipContent = [
            [path: 'content/load/mcd4_fpga1_lite_4ch.core.rbf', length: 30121984],
            [path: 'content/load/top.sof', length: 25970149]
        ]

        def problems = script.analyze(archive('mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip'), RELEASE_NOTES)

        assert problems == []
        assert summaries.size() == 1
        assert summaries[0].contains('Ready to upload as mcd4_fpga1_lite_4ch:20260924_105947_955a6270')
        assert summaries[0].contains('<td>load/top.sof</td><td>25970149</td>')
        assert summaries[0].contains('sha256 c0ffee')
    }

    @Test
    void analyzeHoldsBackACorruptZip() {
        script.@CFG = script.validateConfig(validConfig())
        zipIntact = false

        def problems = script.analyze(archive('mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip'), RELEASE_NOTES)

        assert problems == ['the zip is corrupt or incomplete']
        assert summaries[0].contains('Not uploaded: the zip is corrupt or incomplete')
        assert !summaries[0].contains('<td>')
    }

    @Test
    void analyzeNamesEveryMissingFileAndAMissingNote() {
        script.@CFG = script.validateConfig(validConfig())
        zipContent = [[path: 'content/load/stp1_lite.stp', length: 1]]

        def problems = script.analyze(archive('mcd4_fpga1_lite_4ch_20260101_abcdef12.zip'), RELEASE_NOTES)

        assert problems == ['no *.core.rbf', 'no top.sof', 'no entry in release_notes.txt']
    }

    @Test
    void theSummaryEscapesNamesFromTheShare() {
        script.@CFG = script.validateConfig(validConfig() + [requiredFiles: []])
        zipContent = [[path: 'content/load/<b>&.stp', length: 1]]

        script.analyze(archive('mcd4_a&b_20260101_abcdef12.zip'), RELEASE_NOTES)

        assert summaries[0].contains('<h2>mcd4_a&amp;b_20260101_abcdef12.zip</h2>')
        assert summaries[0].contains('<td>load/&lt;b&gt;&amp;.stp</td>')
    }

    @Test
    void analyzeArchivesMarksTheBuildUnstableAndKeepsOnlyTheArchivesThatPass() {
        script.@CFG = script.validateConfig(validConfig())
        zipContent = [
            [path: 'content/load/a.core.rbf', length: 1],
            [path: 'content/load/top.sof', length: 1]
        ]
        script.@newArchives = [
            archive('mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip'),
            archive('mcd4_fpga1_lite_4ch_20260101_abcdef12.zip')
        ]

        script.analyzeArchives()

        assert script.@readyArchives*.file == ['mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip']
        assert unstableMessages == ['mcd4_fpga1_lite_4ch_20260101_abcdef12.zip: no entry in release_notes.txt']
    }

    // ---------- call ----------

    @Test
    void callReadsJenkinsYmlWithTheDefaultCredentials() {
        script.call()

        assert readTrustedCalls == ['jenkins.yml']
        assert script.@CFG.smbShare == '//asatvault.asatnet.net/SATNET_DATA_1'
        assert script.@NEXUS_CREDENTIALS_ID == 'jira-username-password'
        assert pipelineDefined
    }

    @Test
    void callTakesTheConfigFileAndCredentialsFromItsArguments() {
        script.call(CONFIG: 'ci/smb.yml', NEXUS_CREDENTIALS_ID: 'nexus-publisher')

        assert readTrustedCalls == ['ci/smb.yml']
        assert script.@NEXUS_CREDENTIALS_ID == 'nexus-publisher'
    }

    @Test
    void callFailsBeforeThePipelineOnABadConfig() {
        helper.registerAllowedMethod('readYaml', [Map], { Map args -> [smbDirectory: 'x'] })
        try {
            script.call()
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('nexusRepository')
        }
        assert !pipelineDefined
    }
}
