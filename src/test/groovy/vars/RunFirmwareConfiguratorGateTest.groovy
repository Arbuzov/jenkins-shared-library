package vars

import com.lesfurets.jenkins.unit.declarative.AllOfDeclaration
import com.lesfurets.jenkins.unit.declarative.DeclarativePipelineTest
import com.lesfurets.jenkins.unit.declarative.WhenDeclaration
import org.junit.After
import org.junit.Before
import org.junit.Test

class RunFirmwareConfiguratorGateTest extends DeclarativePipelineTest {
    def script
    List stagesRun
    boolean userStarted
    boolean change

    @Before
    void setUp() {
        super.setUp()
        stagesRun = []; userStarted = false; change = false
        def gate = this
        def triggeredBy = { String cause ->
            assert cause == 'UserIdCause'
            delegate.expression({ -> gate.userStarted })
        }
        WhenDeclaration.metaClass.triggeredBy = triggeredBy
        AllOfDeclaration.metaClass.triggeredBy = triggeredBy
        binding.setVariable('currentBuild', [:])
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('readTrusted', [String], { 'firmwareRepo: x' })
        helper.registerAllowedMethod('readYaml', [Map], { [firmwareRepo: 'https://gitlab.example/team/firmware'] })
        helper.registerAllowedMethod('ansiColor', [String], { })
        helper.registerAllowedMethod('disableRestartFromStage', [], { })
        script = loadScript('vars/runFirmwareConfigurator.groovy')
        def step = script
        step.metaClass.refreshParameters = { -> stagesRun << 'refresh' }
        step.metaClass.validateInputs = { -> stagesRun << 'validate'; step.@chosen = change ? [ncc: '1'] : [:] }
        step.metaClass.updateFirmware = { -> stagesRun << 'update' }
        step.metaClass.linkFirmwareBuild = { -> stagesRun << 'link' }
    }

    @After
    void removeTriggeredBy() {
        GroovySystem.metaClassRegistry.removeMetaClass(WhenDeclaration)
        GroovySystem.metaClassRegistry.removeMetaClass(AllOfDeclaration)
    }

    @Test
    void timerAndScmBuildsOnlyRefresh() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])
        script.call()
        assert stagesRun == ['refresh']
    }

    @Test
    void manualMasterBuildWithChangesCommits() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])
        userStarted = true; change = true
        script.call()
        assert stagesRun == ['refresh', 'validate', 'update']
    }

    @Test
    void manualBuildWithoutChangesLinksCurrentFirmwareBuild() {
        binding.setVariable('env', [BRANCH_NAME: 'master'])
        userStarted = true
        script.call()
        assert stagesRun == ['refresh', 'validate', 'link']
    }

    @Test
    void manualMrAndFeatureBuildsCannotCommitEvenWithChanges() {
        userStarted = true; change = true
        for (environment in [[BRANCH_NAME: 'MR-1', CHANGE_ID: '1'], [BRANCH_NAME: 'feature'], [BRANCH_NAME: 'master', CHANGE_ID: '1']]) {
            stagesRun.clear()
            binding.setVariable('env', environment)
            script.call()
            assert stagesRun == ['refresh', 'validate']
        }
    }
}
