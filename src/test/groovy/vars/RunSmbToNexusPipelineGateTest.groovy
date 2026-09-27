package vars

import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import org.junit.Before
import org.junit.Test

/** Runs call()'s declarative pipeline to check which stages a build gets. */
class RunSmbToNexusPipelineGateTest extends DeclarativePipelineTest {

    def script
    List stagesRun

    @Before
    @Override
    void setUp() throws Exception {
        super.setUp()
        stagesRun = []
        binding.setVariable('currentBuild', [:])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('readTrusted', [String], { String file -> 'archiveType: directory\n' })
        helper.registerAllowedMethod('readYaml', [Map], { Map args ->
            [
                archiveType    : 'directory',
                smbDirectory   : 'ADVSAT/Mesh/Demod/FPGA_Test_Load',
                archivePattern : 'fpga_mesh_\\w+',
                nexusRepository: 'store-DEV',
                nexusPath      : 'mesh/mesh-demod-fpga',
                requiredFiles  : ['*.core.rbf']
            ]
        })
        helper.registerAllowedMethod('credentials', [String], { String id -> 'user:password' })
        helper.registerAllowedMethod('ansiColor', [String], { String colorMap -> })
        helper.registerAllowedMethod('cleanWs', [], { })
        helper.registerAllowedMethod('sh', [Map], { Map args -> null })
        script = loadScript('vars/runSmbToNexusPipeline.groovy')
        def pipelineScript = script
        pipelineScript.metaClass.scanShare = { ->
            stagesRun << 'scan'
            pipelineScript.newArchives = [[file: 'fpga_mesh_a7_20260923_1.zip']]
        }
        pipelineScript.metaClass.analyzeArchives = { ->
            stagesRun << 'analyze'
            pipelineScript.readyArchives = [[file: 'fpga_mesh_a7_20260923_1.zip']]
        }
        pipelineScript.metaClass.uploadArchives = { -> stagesRun << 'upload' }
    }

    @Test
    void aMergeRequestBuildScansAndAnalyzesButNeverUploads() {
        binding.setVariable('env', [CHANGE_ID: '42', BRANCH_NAME: 'MR-42'])

        script.call()

        assert stagesRun == ['scan', 'analyze']
    }

    @Test
    void aBranchBuildUploads() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])

        script.call()

        assert stagesRun == ['scan', 'analyze', 'upload']
    }
}
