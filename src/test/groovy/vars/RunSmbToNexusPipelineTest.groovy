package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import java.text.SimpleDateFormat
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
    List events
    Set publishedUrls
    String loadListing
    List caughtErrors
    List uploads
    List badges
    List catchErrorArgs
    List dirStack

    /** smbclient 'dir' output, trimmed from FPGA_Releases. */
    static final String LISTING = '''\
  .                                   D        0  Fri Sep 25 22:18:48 2026
  ..                                  D        0  Mon Jun 10 12:00:00 2024
  mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip      A 58451032  Thu Sep 24 20:55:29 2026
  mcd4_fpga1_lite_20240424_cf9ddd97 .zip      A 54667058  Wed Apr 24 12:00:00 2024
  mcdma_pcie_data_test_agilex_25Oct2022_07_46.zip      A 1048576  Tue Oct 25 07:46:00 2022
  MCD4_FPGA1_20220610_2100_b0e94c6.zip      A 1048576  Fri Jun 10 21:00:00 2022
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

    /** smbclient 'dir' output of FPGA_Test_Load, with the junk the scan must ignore. */
    static final String MESH_LISTING = '''\
  .                                   D        0  Fri Sep 25 22:18:48 2026
  ..                                  D        0  Mon Jun 10 12:00:00 2024
  fpga_mesh_a5_20260825_f565cc7e      D        0  Tue Aug 25 17:00:00 2026
  fpga_mesh_a7_20260923_1             D        0  Wed Sep 23 20:58:10 2026
  fpga_mesh_a7_20260924_x            DA        0  Thu Sep 24 20:58:10 2026
  stp_test_20260818                   D        0  Tue Aug 18 12:00:00 2026
  old_fpga_mesh_a5                    D        0  Tue Aug 18 12:00:00 2026
  copy of fpga_mesh_a5                D        0  Tue Aug 18 12:00:00 2026
  fpga_mesh_a5.bak                    D        0  Tue Aug 18 12:00:00 2026
  fpga_mesh_notes.txt                 A      120  Tue Aug 18 12:00:00 2026
  fpga_mesh_a7_20260926_1             A      120  Sat Sep 26 12:00:00 2026

\t\t1073741823 blocks of size 4096. 52428800 blocks available
'''

    /** `recurse ON; ls` of fpga_mesh_a5_20260902_650ae40b with TZ=UTC, from the agent on 2026-09-28 06:59:57 UTC. */
    static final String REAL_LISTING = '''\
  .                                  Dn        0  Wed Sep  2 14:44:48 2026
  ..                                 Dn        0  Wed Sep  2 14:44:48 2026
  fpga_mesh_a5_MESH_MitySTND.core.rbf     An 10330112  Wed Sep  2 14:42:29 2026
  fpga_mesh_a5_MESH_MitySTND.hps.jic     An 33554678  Wed Sep  2 14:42:29 2026
  fpga_mesh_a5_MESH_MitySTND_jic.map     An      382  Wed Sep  2 14:42:29 2026
  stp1_mesh.stp                      An  8883170  Wed Sep  2 13:41:46 2026
  timestamp_pkg.sv                   An      302  Wed Sep  2 13:43:18 2026
  top.sof                            An  9444175  Wed Sep  2 14:27:57 2026
  top.sta.summary                    An    25755  Wed Sep  2 14:27:03 2026

\t\t3827199871 blocks of size 16384. 3129170441 blocks available
'''

    static long utc(String date) {
        def format = new SimpleDateFormat('EEE MMM d HH:mm:ss yyyy', Locale.US)
        format.timeZone = TimeZone.getTimeZone('UTC')
        return format.parse(date).time
    }

    /** "Wed Sep  2 14:27:57 2026", the way smbclient prints an mtime, in UTC. */
    static String smbDate(long millis) {
        def formatted = { String pattern ->
            def format = new SimpleDateFormat(pattern, Locale.US)
            format.timeZone = TimeZone.getTimeZone('UTC')
            format.format(new Date(millis))
        }
        return "${formatted('EEE MMM')} ${formatted('d').padLeft(2)} ${formatted('HH:mm:ss yyyy')}"
    }

    /** A `recurse ON; ls` listing, entries as [name, attribute, size, seconds ago]; '\\sub' starts a subdirectory. */
    static String listing(List entries) {
        long now = System.currentTimeMillis()
        return entries.collect { entry ->
            if (entry == '\\sub') {
                return '\n\\sub'
            }
            def (name, attribute, size, age) = entry
            return "  ${name.padRight(36)} ${attribute} ${size.toString().padLeft(8)}  ${smbDate(now - age * 1000L)}"
        }.join('\n') + '\n\n\t\t3827199871 blocks of size 16384. 3129170441 blocks available\n'
    }

    /** A load of 62238574 bytes in which nothing changed for age seconds. */
    static String quietLoad(long age) {
        return listing([
            ['.', 'Dn', 0, age],
            ['..', 'Dn', 0, age],
            ['fpga_mesh_a7.core.rbf', 'An', 62238000, age],
            ['stp1.stp', 'An', 574, age]
        ])
    }

    static final String MESH_DIR = 'https://nexus.asatnet.net/repository/store-DEV/mesh/mesh-demod-fpga/'

    static Map meshConfig() {
        return [
            archiveType    : 'directory',
            smbDirectory   : 'ADVSAT/Mesh/Demod/FPGA_Test_Load',
            archivePattern : 'fpga_mesh_\\w+',
            nexusRepository: 'store-DEV',
            nexusPath      : 'mesh/mesh-demod-fpga',
            requiredFiles  : ['*.core.rbf']
        ]
    }

    static Map load(String name) {
        return [file: name + '.zip', load: name, url: MESH_DIR + name + '.zip']
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
        events = []
        publishedUrls = [] as Set
        loadListing = quietLoad(5400)
        caughtErrors = []
        uploads = []
        badges = []
        catchErrorArgs = []
        dirStack = []
        binding.setVariable('env', [:])
        binding.setVariable('currentBuild', [:])
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
        helper.registerAllowedMethod('sh', [Map], { Map args ->
            events << args.label
            if (args.label.startsWith('Inspect ')) {
                return loadListing
            }
            return args.returnStdout ? shareListing : null
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            nexusQueries << args.url
            assert args.authentication == script.@NEXUS_CREDENTIALS_ID
            if (args.httpMode == 'HEAD') {
                assert args.validResponseCodes == '200,404'
                if (args.url.contains('broken')) {
                    throw new RuntimeException('Fail: the returned code 500 is not in the accepted range')
                }
                return [status: publishedUrls.contains(args.url) ? 200 : 404]
            }
            def version = (args.url =~ /maven\.baseVersion=([^&]+)/)[0][1]
            def items = uploadedVersions.contains(version) ? [[path: version]] : []
            return [content: JsonOutput.toJson([items: items])]
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            assert args.returnPojo == true
            return new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('withEnv', [List, Closure], { List vars, Closure body ->
            events << "env ${vars.join(' ')}".toString()
            body()
        })
        helper.registerAllowedMethod('unzip', [Map], { Map args ->
            assert args.test || args.dir == 'content'
            return args.test ? zipIntact : null
        })
        helper.registerAllowedMethod('findFiles', [Map], { Map args ->
            if (args.glob == 'content/**') {
                return zipContent
            }
            if (args.glob ==~ /content\/[^*]+\/\*\*/) {
                return zipContent.findAll { (it.path as String).startsWith(args.glob - '**') }
            }
            def fileGlob = args.glob - 'content/**/'
            def regex = fileGlob.replace('.', '\\.').replace('*', '.*')
            return zipContent.findAll { (it.path as String).tokenize('/').last() ==~ regex }
        })
        helper.registerAllowedMethod('dir', [String, Closure], { String path, Closure body ->
            dirStack << path
            try {
                body()
            } finally {
                dirStack.remove(dirStack.size() - 1)
            }
        })
        helper.registerAllowedMethod('deleteDir', [], { events << "deleteDir ${dirStack ? dirStack.last() : '.'}".toString() })
        helper.registerAllowedMethod('zip', [Map], { Map args ->
            events << "zip ${args.zipFile} ${args.dir} ${args.glob}".toString()
            assert args.defaultExcludes == false
        })
        helper.registerAllowedMethod('publishNexus', [String, String, Boolean, String], { String glob, String repoPath, Boolean flatten, String credentialsId ->
            uploads << [publishNexus: glob, to: repoPath, flatten: flatten, credentialsId: credentialsId]
        })
        helper.registerAllowedMethod('nexusArtifactUploader', [Map], { Map args -> uploads << args })
        helper.registerAllowedMethod('addBadge', [Map], { Map args -> badges << args })
        helper.registerAllowedMethod('sha256', [Map], { Map args ->
            events << "sha256 ${args.file}".toString()
            return 'c0ffee'
        })
        helper.registerAllowedMethod('libraryResource', [String], { String path ->
            new File("resources/${path}").getText('UTF-8')
        })
        helper.registerAllowedMethod('inflateTemplate', [Map], { Map args ->
            new SimpleTemplateEngine().createTemplate(args.TEMPLATE).make(args).toString()
        })
        helper.registerAllowedMethod('publishSummary', [String], { String text -> summaries << text })
        helper.registerAllowedMethod('readFile', [String], { String file -> RELEASE_NOTES })
        helper.registerAllowedMethod('catchError', [Map, Closure], { Map args, Closure body ->
            catchErrorArgs << args
            try {
                body()
            } catch (RuntimeException exception) {
                caughtErrors << exception.message
            }
        })
        helper.registerAllowedMethod('unstable', [String], { String message -> unstableMessages << message })
        // stop call() at the pipeline block; RunSmbToNexusPipelineGateTest runs the declarative gating
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
            'mcdma_pcie_data_test_agilex_25Oct2022_07_46.zip',
            'MCD4_FPGA1_20220610_2100_b0e94c6.zip'
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
        // a name without an artifactId or without a version is never looked up
        assert nexusQueries.size() == 2
        assert nexusQueries.every { it.contains('repository=maven-snapshots') }
        assert echoMessages.contains('1 of 4 archives are not in maven-snapshots yet')
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

    // ---------- MCD behaviour pinned before the generalization ----------

    @Test
    void theMcdConfigsOfBothBranchesValidateAsBefore() {
        def master = [
            smbDirectory   : 'ADVSAT/MCD/MCD_Rel40/FPGA_Releases',
            nexusRepository: 'maven-releases',
            groupId        : 'com.spacebridge.mcd.rel40.fpga',
            requiredFiles  : ['*.core.rbf', '*.periph.jic', '*_jic.map', 'top.sof', 'top.sta.summary', '*.stp']
        ]
        def develop = master + [
            smbDirectory   : 'ADVSAT/MCD/MCD_Rel40/FPGA-SNAPSHOTS',
            nexusRepository: 'maven-snapshots',
            versionSuffix  : '-SNAPSHOT'
        ]
        def defaults = [
            smbShare         : '//asatvault.asatnet.net/SATNET_DATA_1',
            versionSuffix    : '',
            archivePattern   : '(mcd|MCD).*\\.zip',
            artifactIdPattern: '^\\w*?(?=_20\\d{2,2})',
            versionPattern   : '\\d{8}(_\\d{6})?_([a-f]|\\d){7,8}'
        ]
        assert script.validateConfig(master) == defaults + master + [archiveType: 'zip']
        assert script.validateConfig(develop) == defaults + develop + [archiveType: 'zip']
    }

    @Test
    void scanShareQueriesTheSameMavenSearch() {
        script.@CFG = script.validateConfig(validConfig() + [nexusRepository: 'maven-snapshots', versionSuffix: '-SNAPSHOT'])

        script.scanShare()

        assert nexusQueries[0] == 'https://nexus.asatnet.net/service/rest/v1/search/assets?maven.artifactId=mcd4_fpga1_lite_4ch&maven.baseVersion=20260924_105947_955a6270-SNAPSHOT&repository=maven-snapshots&maven.extension=zip'
    }

    @Test
    void uploadSendsAZipToMavenWithTheSameCoordinates() {
        script.@CFG = script.validateConfig(validConfig())
        script.@NEXUS_CREDENTIALS_ID = 'jira-username-password'

        script.upload(archive('mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip'))

        assert uploads == [[
            nexusVersion : 'nexus3',
            protocol     : 'http',
            nexusUrl     : 'nexus.asatnet.net',
            version      : '20260924_105947_955a6270',
            groupId      : 'com.spacebridge.mcd.rel40.fpga',
            repository   : 'maven-releases',
            credentialsId: 'jira-username-password',
            artifacts    : [[artifactId: 'mcd4_fpga1_lite_4ch', file: 'mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip', type: 'zip']]
        ]]
        assert badges*.text*.toString() == ['mcd4_fpga1_lite_4ch:20260924_105947_955a6270']
        assert badges[0].link.toString() == 'https://nexus.asatnet.net/service/rest/repository/browse/maven-releases/com/spacebridge/mcd/rel40/fpga/mcd4_fpga1_lite_4ch/20260924_105947_955a6270/'
        assert binding.getVariable('currentBuild').keepLog == true
    }

    @Test
    void analyzeArchivesReadsTheReleaseNotesForZips() {
        script.@CFG = script.validateConfig(validConfig())

        script.analyzeArchives()

        assert events == ['Download release_notes.txt']
    }

    @Test
    void analyzeStartsEveryZipFromAnEmptyContentDirectory() {
        script.@CFG = script.validateConfig(validConfig())

        script.analyze(archive('mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip'), RELEASE_NOTES)

        assert events == [
            'deleteDir content',
            'env ARCHIVE=mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip',
            'Download mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip',
            'sha256 mcd4_fpga1_lite_4ch_20260924_105947_955a6270.zip'
        ]
    }

    // ---------- directory config ----------

    @Test
    void validateConfigNamesTheKeysADirectoryNeeds() {
        try {
            script.validateConfig([archiveType: 'directory'])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            ['smbDirectory', 'nexusRepository', 'nexusPath', 'archivePattern', 'requiredFiles'].each {
                assert exception.message.contains(it)
            }
            assert !exception.message.contains('groupId')
        }
    }

    @Test
    void validateConfigRejectsAnAnchoredDirectoryPattern() {
        ['^fpga_mesh_\\w+', 'fpga_mesh_\\w+$'].each { pattern ->
            try {
                script.validateConfig(meshConfig() + [archivePattern: pattern])
                assert false: "expected an error for ${pattern}"
            } catch (RuntimeException exception) {
                assert exception.message.contains('drop ^ and $')
            }
        }
    }

    @Test
    void validateConfigRejectsANexusPathWithASlashAtEitherEnd() {
        ['/mesh/mesh-demod-fpga', 'mesh/mesh-demod-fpga/'].each { path ->
            try {
                script.validateConfig(meshConfig() + [nexusPath: path])
                assert false: "expected an error for ${path}"
            } catch (RuntimeException exception) {
                assert exception.message.contains('nexusPath must not start or end with /')
            }
        }
    }

    @Test
    void validateConfigRejectsAnUnknownArchiveType() {
        try {
            script.validateConfig(meshConfig() + [archiveType: 'directories'])
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('archiveType must be zip or directory, not directories')
        }
    }

    // ---------- directory scan ----------

    @Test
    void listDirectoriesTakesOnlyDirectoriesWhoseWholeNameMatches() {
        assert script.listDirectories(MESH_LISTING, 'fpga_mesh_\\w+') == [
            'fpga_mesh_a5_20260825_f565cc7e',
            'fpga_mesh_a7_20260923_1',
            'fpga_mesh_a7_20260924_x'
        ]
    }

    @Test
    void scanShareSkipsEveryLoadWhoseZipExists() {
        script.@CFG = script.validateConfig(meshConfig())
        publishedUrls << MESH_DIR + 'fpga_mesh_a5_20260825_f565cc7e.zip'
        shareListing = MESH_LISTING

        script.scanShare()

        assert nexusQueries == [
            MESH_DIR + 'fpga_mesh_a5_20260825_f565cc7e.zip',
            MESH_DIR + 'fpga_mesh_a7_20260923_1.zip',
            MESH_DIR + 'fpga_mesh_a7_20260924_x.zip'
        ]
        assert script.@newArchives == [load('fpga_mesh_a7_20260923_1'), load('fpga_mesh_a7_20260924_x')]
        assert echoMessages.contains('2 of 3 archives are not in store-DEV yet')
    }

    @Test
    void scanShareFailsWhenNexusAnswersNeither200Nor404() {
        script.@CFG = script.validateConfig(meshConfig() + [nexusPath: 'broken'])
        shareListing = MESH_LISTING
        try {
            script.scanShare()
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('500')
        }
        assert script.@newArchives == []
    }

    // ---------- directory listing ----------

    @Test
    void inspectListingReadsARealListing() {
        def share = script.inspectListing(REAL_LISTING, utc('Mon Sep 28 06:59:57 2026'))

        assert share.bytes == 62238574
        // the newest entry is the directory's own ".", 14:44:48
        assert share.age == utc('Mon Sep 28 06:59:57 2026').intdiv(1000) - utc('Wed Sep 2 14:44:48 2026').intdiv(1000)
    }

    @Test
    void inspectListingTakesTheAgeFromTheDotEntry() {
        // the copy keeps each file's mtime, so the new file shows only in the directory's "."
        def share = script.inspectListing(listing([
            ['.', 'Dn', 0, 300],
            ['..', 'Dn', 0, 99999],
            ['top.sof', 'An', 62238000, 864000]
        ]), System.currentTimeMillis())

        assert share.bytes == 62238000
        assert share.age >= 300 && share.age < 310
    }

    @Test
    void inspectListingIgnoresTheParentDirectory() {
        // ".." changes whenever another load lands next to this one
        def share = script.inspectListing(listing([
            ['.', 'Dn', 0, 7200],
            ['..', 'Dn', 0, 60],
            ['top.sof', 'An', 62238000, 7200]
        ]), System.currentTimeMillis())

        assert share.age >= 7200 && share.age < 7210
    }

    @Test
    void inspectListingLooksIntoSubdirectories() {
        def share = script.inspectListing(listing([
            ['.', 'Dn', 0, 7200],
            ['..', 'Dn', 0, 7200],
            ['sub', 'Dn', 0, 7200],
            '\\sub',
            ['.', 'Dn', 0, 7200],
            ['..', 'Dn', 0, 7200],
            ['late.stp', 'An', 1000, 120]
        ]), System.currentTimeMillis())

        assert share.bytes == 1000
        assert share.age >= 120 && share.age < 130
    }

    @Test
    void inspectListingReportsAnEmptyDirectoryAsZeroBytes() {
        def share = script.inspectListing(listing([['.', 'Dn', 0, 7200], ['..', 'Dn', 0, 7200]]), System.currentTimeMillis())

        assert share.bytes == 0
    }

    @Test
    void inspectListingReturnsNullWhenNoEntryCanBeRead() {
        assert script.inspectListing('NT_STATUS_OK\n\n\t\t3827199871 blocks of size 16384.\n', System.currentTimeMillis()) == null
    }

    @Test
    void analyzeFailsALoadWhoseListingCannotBeRead() {
        script.@CFG = script.validateConfig(meshConfig())
        loadListing = 'something else\n'

        try {
            script.analyze(load('fpga_mesh_a7_20260923_1'), '')
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message == 'runSmbToNexusPipeline: no sizes and mtimes in the listing of fpga_mesh_a7_20260923_1.'
        }
        assert !events.contains('Download fpga_mesh_a7_20260923_1')
    }

    // ---------- directory analysis ----------

    @Test
    void analyzeWaitsUntilNothingChangedForThirtyMinutes() {
        script.@CFG = script.validateConfig(meshConfig())
        loadListing = quietLoad(1790)

        assert script.analyze(load('fpga_mesh_a7_20260923_1'), '') == null
        assert !events.contains('Download fpga_mesh_a7_20260923_1')
        assert summaries == []
        assert echoMessages.contains('fpga_mesh_a7_20260923_1: empty or changed less than 30 min ago, skipped')
    }

    @Test
    void analyzeWaitsForAnEmptyDirectory() {
        script.@CFG = script.validateConfig(meshConfig())
        loadListing = listing([['.', 'Dn', 0, 99999], ['..', 'Dn', 0, 99999]])

        assert script.analyze(load('fpga_mesh_a7_20260923_1'), '') == null
        assert !events.contains('Download fpga_mesh_a7_20260923_1')
    }

    @Test
    void analyzeZipsACompleteLoadAsLoadSlashFiles() {
        script.@CFG = script.validateConfig(meshConfig())
        loadListing = quietLoad(1800)
        zipContent = [
            [path: 'content/fpga_mesh_a7_20260923_1/fpga_mesh_a7.core.rbf', length: 62238000],
            [path: 'content/fpga_mesh_a7_20260923_1/stp1.stp', length: 574]
        ]

        def problems = script.analyze(load('fpga_mesh_a7_20260923_1'), '')

        assert problems == []
        assert events == [
            'deleteDir content',
            'env LOAD=fpga_mesh_a7_20260923_1',
            'Inspect fpga_mesh_a7_20260923_1',
            'env LOAD=fpga_mesh_a7_20260923_1',
            'Download fpga_mesh_a7_20260923_1',
            'zip fpga_mesh_a7_20260923_1.zip content fpga_mesh_a7_20260923_1/**',
            'sha256 fpga_mesh_a7_20260923_1.zip'
        ]
        assert summaries[0].contains('Ready to upload as ' + MESH_DIR + 'fpga_mesh_a7_20260923_1.zip')
        assert summaries[0].contains('<td>fpga_mesh_a7_20260923_1/stp1.stp</td><td>574</td>')
    }

    @Test
    void analyzeFailsALoadThatWasNotFullyDownloaded() {
        script.@CFG = script.validateConfig(meshConfig())
        // mget exits 0 even when a file fails
        zipContent = [[path: 'content/fpga_mesh_a7_20260923_1/fpga_mesh_a7.core.rbf', length: 62238000]]
        try {
            script.analyze(load('fpga_mesh_a7_20260923_1'), '')
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message == 'runSmbToNexusPipeline: downloaded 62238000 of 62238574 bytes of fpga_mesh_a7_20260923_1.'
        }
        assert !events.any { it.startsWith('zip ') }
        assert summaries == []
    }

    @Test
    void analyzeHoldsBackALoadWithoutACoreRbfAndIgnoresReleaseNotes() {
        script.@CFG = script.validateConfig(meshConfig())
        zipContent = [[path: 'content/fpga_mesh_a7_20260923_1/stp1.stp', length: 62238574]]

        assert script.analyze(load('fpga_mesh_a7_20260923_1'), '') == ['no *.core.rbf']
    }

    @Test
    void analyzeArchivesKeepsReadyLoadsSkipsBusyOnesAndIsolatesFailures() {
        script.@CFG = script.validateConfig(meshConfig())
        zipContent = [[path: 'content/fpga_mesh_a7_20260923_1/a.core.rbf', length: 62238574]]
        script.@newArchives = [load('fpga_mesh_a7_20260923_1'), load('fpga_mesh_busy'), load('fpga_mesh_short')]
        helper.registerAllowedMethod('sh', [Map], { Map args ->
            events << args.label
            if (args.label == 'Inspect fpga_mesh_busy') {
                return quietLoad(60)
            }
            return args.label.startsWith('Inspect ') ? quietLoad(5400) : null
        })

        script.analyzeArchives()

        assert script.@readyArchives == [load('fpga_mesh_a7_20260923_1')]
        assert unstableMessages == []
        // nothing of fpga_mesh_short arrived
        assert caughtErrors == ['runSmbToNexusPipeline: downloaded 0 of 62238574 bytes of fpga_mesh_short.']
        // an incomplete download fails the build, and Abort or a timeout still stops it
        assert catchErrorArgs.every { it == [buildResult: 'FAILURE', stageResult: 'FAILURE', catchInterruptions: false] }
        assert !events.contains('Download release_notes.txt')
        // every load starts from an empty content/, so a failed one cannot pass its files to the next
        assert events.findAll { it == 'deleteDir content' }.size() == 3
    }

    @Test
    void analyzeArchivesHoldsBackALoadWithoutACoreRbf() {
        script.@CFG = script.validateConfig(meshConfig())
        zipContent = [[path: 'content/fpga_mesh_a7_20260923_1/stp1.stp', length: 62238574]]
        script.@newArchives = [load('fpga_mesh_a7_20260923_1')]

        script.analyzeArchives()

        assert unstableMessages == ['fpga_mesh_a7_20260923_1.zip: no *.core.rbf']
        assert script.@readyArchives == []
    }

    // ---------- directory upload ----------

    @Test
    void uploadPutsTheZipIntoTheRawDirectory() {
        script.@CFG = script.validateConfig(meshConfig())
        script.@NEXUS_CREDENTIALS_ID = 'nexus-publisher'

        script.upload(load('fpga_mesh_a7_20260923_1'))

        assert nexusQueries == [MESH_DIR + 'fpga_mesh_a7_20260923_1.zip']
        assert uploads == [[publishNexus: 'fpga_mesh_a7_20260923_1.zip', to: MESH_DIR, flatten: false, credentialsId: 'nexus-publisher']]
        assert badges*.text == ['fpga_mesh_a7_20260923_1.zip']
        assert badges[0].link.toString() == 'https://nexus.asatnet.net/service/rest/repository/browse/store-DEV/mesh/mesh-demod-fpga/'
        assert binding.getVariable('currentBuild').keepLog == true
    }

    @Test
    void uploadRefusesAZipPublishedWhileTheBuildRan() {
        script.@CFG = script.validateConfig(meshConfig())
        script.@NEXUS_CREDENTIALS_ID = 'nexus-publisher'
        publishedUrls << MESH_DIR + 'fpga_mesh_a7_20260923_1.zip'

        try {
            script.upload(load('fpga_mesh_a7_20260923_1'))
            assert false: 'expected an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('was published while this build ran, it is not replaced')
        }
        assert uploads == []
        assert badges == []
        assert binding.getVariable('currentBuild').keepLog != true
    }

    @Test
    void uploadArchivesUploadsEveryReadyArchiveInItsOwnStage() {
        script.@CFG = script.validateConfig(meshConfig())
        script.@readyArchives = [load('fpga_mesh_a7_20260923_1'), load('fpga_mesh_a7_20260923_2')]
        def stages = []
        helper.registerAllowedMethod('stage', [String, Closure], { String name, Closure body ->
            stages << name
            body()
        })
        // Jenkins starts the branches after the map is built, so each one must keep its own archive
        helper.registerAllowedMethod('parallel', [Map], { Map branches -> branches.values()*.call() })

        script.uploadArchives()

        assert stages == ['Upload fpga_mesh_a7_20260923_1.zip', 'Upload fpga_mesh_a7_20260923_2.zip']
        assert uploads*.publishNexus == ['fpga_mesh_a7_20260923_1.zip', 'fpga_mesh_a7_20260923_2.zip']
    }
}
