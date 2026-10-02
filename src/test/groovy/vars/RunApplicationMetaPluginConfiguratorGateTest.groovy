package vars

import com.lesfurets.jenkins.unit.declarative.AllOfDeclaration
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import com.lesfurets.jenkins.unit.declarative.WhenDeclaration
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Runs call()'s declarative pipeline to check which stage bodies a build runs. */
class RunApplicationMetaPluginConfiguratorGateTest extends DeclarativePipelineTest {

    def script
    List stagesRun
    boolean startedByUser
    List picks

    @Before
    @Override
    void setUp() throws Exception {
        super.setUp()
        stagesRun = []
        startedByUser = false
        picks = []
        // Pipeline Unit has no triggeredBy: a flag stands for "the build was started by a user"
        def gate = this
        def triggeredBy = { String cause ->
            assert cause == 'UserIdCause'
            delegate.expression({ -> gate.startedByUser })
        }
        WhenDeclaration.metaClass.triggeredBy = triggeredBy
        AllOfDeclaration.metaClass.triggeredBy = triggeredBy

        binding.setVariable('currentBuild', [:])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('readTrusted', [String], { String file -> 'pluginRepo: x\n' })
        helper.registerAllowedMethod('readYaml', [Map], { Map args -> [pluginRepo: 'https://gitlab.example.com/group/meta'] })
        helper.registerAllowedMethod('ansiColor', [String], { String colorMap -> })
        helper.registerAllowedMethod('disableRestartFromStage', [], { })
        script = loadScript('vars/runApplicationMetaPluginConfigurator.groovy')
        def pipelineScript = script
        pipelineScript.metaClass.refreshParameters = { -> stagesRun << 'refresh' }
        pipelineScript.metaClass.validateInputs = { ->
            stagesRun << 'validate'
            pipelineScript.@changes = picks
        }
        pipelineScript.metaClass.updateDeployment = { -> stagesRun << 'update' }
        pipelineScript.metaClass.linkDeploymentBuild = { -> stagesRun << 'link' }
    }

    @After
    void removeTriggeredBy() {
        GroovySystem.metaClassRegistry.removeMetaClass(WhenDeclaration)
        GroovySystem.metaClassRegistry.removeMetaClass(AllOfDeclaration)
    }

    @Test
    void aBuildNotStartedByAUserOnlyRefreshesTheLists() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])

        script.call()

        assert stagesRun == ['refresh']
    }

    @Test
    void aUserBuildOfMasterWithAChangeCommitsIt() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])
        startedByUser = true
        picks = [[key: 'nms-ng']]

        script.call()

        assert stagesRun == ['refresh', 'validate', 'update']
    }

    @Test
    void aUserBuildWithoutAChangeLinksTheDeploymentBuild() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])
        startedByUser = true

        script.call()

        assert stagesRun == ['refresh', 'validate', 'link']
    }

    @Test
    void aUserBuildOfAnotherBranchWithAChangeCommitsNothing() {
        binding.setVariable('env', [BRANCH_NAME: 'MR-6', CHANGE_ID: '6'])
        startedByUser = true
        picks = [[key: 'nms-ng']]

        script.call()

        assert stagesRun == ['refresh', 'validate']
    }
}
