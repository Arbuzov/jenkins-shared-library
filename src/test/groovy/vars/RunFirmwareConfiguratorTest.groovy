package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class RunFirmwareConfiguratorTest extends BasePipelineTest {
    static final String REPO = 'https://gitlab.example/asat3/estella-hd/estella-hd-firmware'
    static final String MANIFEST = '# pinned packages\r\npython3-mcd4-confd=1.1.0-24\r\ndataplane-phs-ng=1.0.24-b13\r\n' +
        'mcd4-lite=1.5.13-b69\r\nulpc=5.3.24-b6\r\nncc=5.2.20-b267\r\n'
    def script
    def applyScript
    List reads
    List indexCalls
    List commits
    List links
    List logs
    List declaredProperties
    Map available
    boolean userStarted
    Map statusesArgs

    @Before
    void setUp() {
        super.setUp()
        reads = []; indexCalls = []; commits = []; links = []; logs = []
        userStarted = true
        available = ['python3-mcd4-confd': ['1.1.0-25'], 'dataplane-phs-ng': ['1.0.24-b14'],
            'mcd4-lite': ['1.5.13-b70'], 'ulpc': ['5.3.24-b7'], 'ncc': ['5.2.20-b268']]
        binding.setVariable('env', [BRANCH_NAME: 'master', JOB_NAME: 'configurator/master', BUILD_NUMBER: '12', BUILD_URL: 'https://jenkins.example/12/'])
        binding.setVariable('params', [:])
        binding.setVariable('currentBuild', [description: '', getBuildCauses: { Object... types ->
            userStarted ? [[userName: 'Serge']] : []
        }])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('error', [String], { throw new IllegalArgumentException(it) })
        helper.registerAllowedMethod('echo', [String], { logs << it })
        helper.registerAllowedMethod('readTrusted', [String], { 'firmwareRepo: x' })
        helper.registerAllowedMethod('readYaml', [Map], { [firmwareRepo: REPO] })
        helper.registerAllowedMethod('gitlabGetFile', [Map], { Map args ->
            reads << args
            args.filePath == 'Manifest' ? [text: MANIFEST, commit_id: 'snapshot', last_commit_id: 'manifest-sha'] :
                [text: '1.1.2\r\n', commit_id: 'snapshot', last_commit_id: 'version-sha']
        })
        helper.registerAllowedMethod('getRepReproArtifacts', [Map], { indexCalls << it; available })
        helper.registerAllowedMethod('sortDebianVersions', [Map], { it.versions })
        ['choice', 'separator', 'string'].each { name ->
            helper.registerAllowedMethod(name, [Map], { Map args -> args + [_kind: name] })
        }
        helper.registerAllowedMethod('cron', [String], { [cron: it] })
        helper.registerAllowedMethod('parameters', [List], { [parameters: it] })
        helper.registerAllowedMethod('pipelineTriggers', [List], { [pipelineTriggers: it] })
        helper.registerAllowedMethod('properties', [List], { declaredProperties = it })
        applyScript = loadScript('vars/applyManifestVersions.groovy')
        helper.registerAllowedMethod('applyManifestVersions', [Map], { applyScript.call(it) })
        helper.registerAllowedMethod('gitlabCommitFiles', [Map], { commits << it; [id: 'new-sha'] })
        helper.registerAllowedMethod('gitlabApiGet', [Map], { statusesArgs = it; [[description: 'firmware/master #151: success']] })
        helper.registerAllowedMethod('linkUpstreamBuild', [Map], { links << it })
        helper.registerAllowedMethod('pipeline', [Closure], { })
        script = loadScript('vars/runFirmwareConfigurator.groovy')
    }

    void refresh() {
        script.@CFG = script.validateConfig([firmwareRepo: REPO])
        script.refreshParameters()
    }

    @Test
    void readsConsistentSnapshotAndListsEveryManifestPackageInOneIndexFetch() {
        refresh()
        assert reads == [[gitUrl: REPO, filePath: 'Manifest', ref: 'master'], [gitUrl: REPO, filePath: 'VERSION', ref: 'snapshot']]
        assert indexCalls == [[repository: 'bullseye', packageNames: ['python3-mcd4-confd', 'dataplane-phs-ng', 'mcd4-lite', 'ulpc', 'ncc'], failOnError: true]]
        def definitions = declaredProperties.first().parameters
        assert definitions.findAll { it._kind == 'choice' }*.name == ['PYTHON3_MCD4_CONFD_VERSION', 'DATAPLANE_PHS_NG_VERSION', 'MCD4_LITE_VERSION', 'ULPC_VERSION', 'NCC_VERSION']
        assert definitions.find { it.name == 'NCC_VERSION' }.choices == ['5.2.20-b267 (current)', '5.2.20-b268']
        assert declaredProperties.last().pipelineTriggers == [[cron: 'H/5 * * * *']]
        assert commits == []
    }

    @Test
    void refreshIsIdempotentAndMrHasNoTimer() {
        binding.env.CHANGE_ID = '1'
        refresh(); refresh()
        assert script.@fields.size() == 5
        assert declaredProperties.last().pipelineTriggers == []
    }

    @Test
    void updatesBothFilesAtomicallyPreservingManifestCommentsAndCrLf() {
        refresh()
        binding.params.NCC_VERSION = '5.2.20-b268'
        binding.params.PYTHON3_MCD4_CONFD_VERSION = '1.1.0-25'
        binding.params.FIRMWARE_VERSION = ' 1.1.3 '
        script.validateInputs(); script.updateFirmware()
        assert commits.size() == 1
        def update = commits.first()
        assert update.gitUrl == REPO && update.branch == 'master'
        assert update.files == [
            [filePath: 'Manifest', content: MANIFEST.replace('1.1.0-24', '1.1.0-25').replace('5.2.20-b267', '5.2.20-b268'), lastCommitId: 'manifest-sha'],
            [filePath: 'VERSION', content: '1.1.3\r\n', lastCommitId: 'version-sha']]
        assert update.commitMessage.contains('Requested by Serge')
        assert update.commitMessage.endsWith('Jenkins-Upstream-Project: configurator/master\nJenkins-Upstream-Build: 12')
    }

    @Test
    void versionOnlyChangeDoesNotRewriteManifest() {
        refresh()
        binding.params.FIRMWARE_VERSION = '1.1.3'
        script.validateInputs(); script.updateFirmware()
        assert commits.first().files*.filePath == ['VERSION']
    }

    @Test
    void currentChoiceKeepsTheFreshValueAndNoChangeLinksExistingBuild() {
        refresh()
        binding.params.NCC_VERSION = 'old-stale-version (current)'
        binding.params.FIRMWARE_VERSION = '1.1.2'
        script.validateInputs()
        assert !script.hasChanges()
        script.linkFirmwareBuild()
        assert commits == []
        assert links == [[project: 'firmware/master', build: 151, reverse: true]]
        assert statusesArgs.path == 'repository/commits/snapshot/statuses'
        assert binding.currentBuild.description == 'No changes'
    }

    @Test
    void unavailableVersionAndMultilineFirmwareVersionFailBeforeCommit() {
        refresh()
        binding.params.NCC_VERSION = '5.2.20-b999'
        expectFailure { script.validateInputs() }
        binding.params.NCC_VERSION = null
        binding.params.FIRMWARE_VERSION = '1.1.3\nanything'
        expectFailure { script.validateInputs() }
        assert commits == []
    }

    @Test
    void writeMethodRejectsMrFeatureBranchAndAutomaticRun() {
        refresh()
        binding.params.NCC_VERSION = '5.2.20-b268'
        script.validateInputs()
        binding.env.CHANGE_ID = '1'
        expectFailure { script.updateFirmware() }
        binding.env.remove('CHANGE_ID'); binding.env.BRANCH_NAME = 'feature'
        expectFailure { script.updateFirmware() }
        binding.env.BRANCH_NAME = 'master'; userStarted = false
        expectFailure { script.updateFirmware() }
        assert commits == []
    }

    @Test
    void wrongManifestEditIsRejectedBeforeCommit() {
        refresh()
        binding.params.NCC_VERSION = '5.2.20-b268'
        script.validateInputs()
        helper.registerAllowedMethod('applyManifestVersions', [Map], { [text: MANIFEST, changed: [:]] })
        expectFailure { script.updateFirmware() }
        assert commits == []
    }

    @Test
    void rejectsUnknownConfigKeysMalformedManifestAndParameterCollisions() {
        expectFailure { script.validateConfig([firmwareRepo: REPO, upstream: 'old']) }
        expectFailure { script.validateConfig([firmwareRepo: REPO, repository: '../bullseye']) }
        expectFailure { script.parseManifest('ncc=1\nncc=2\n') }
        expectFailure { script.parseManifest('ncc=\n') }
        expectFailure { script.parseManifest('# nothing\n') }
        helper.registerAllowedMethod('gitlabGetFile', [Map], { Map args ->
            [text: args.filePath == 'Manifest' ? 'foo-bar=1\nfoo.bar=2\n' : '1.1.2', commit_id: 'x', last_commit_id: 'x']
        })
        expectFailure { refresh() }
        assert commits == []
    }

    @Test
    void supportsAnExplicitReleaseTargetWithoutUsingConfiguratorBranchAsRef() {
        script.@CFG = script.validateConfig([firmwareRepo: REPO, branch: 'release/v.1.1.0'])
        binding.env.BRANCH_NAME = 'MR-1'
        script.refreshParameters()
        assert reads.first().ref == 'release/v.1.1.0'
        assert reads.last().ref == 'snapshot'
        assert declaredProperties.last().pipelineTriggers == []
    }

    void expectFailure(Closure body) {
        try { body(); assert false: 'expected rejection' }
        catch (IllegalArgumentException expected) { }
    }
}
