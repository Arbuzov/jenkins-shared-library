package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class ReportUsageTest extends BasePipelineTest {

    def script
    List<Map> influxPoints
    List<String> echoMessages

    @Before
    void setUp() {
        super.setUp()
        influxPoints = []
        echoMessages = []
        helper.registerAllowedMethod('sendToInfluxDB', [Map], { Map args -> influxPoints << args })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        binding.setVariable('env', [
            JOB_NAME      : 'devops/jenkins-shared-library',
            getEnvironment: { -> [JOB_NAME: 'devops/jenkins-shared-library', BUILD_ID: '42'] }
        ])
        script = loadScript('vars/reportUsage.groovy')
    }

    @Test
    void reportsTheFunctionNameTakenFromThePath() {
        script.call(path: '/var/jenkins/libs/custom-lib/vars/addLinkToBuild.groovy')

        assert influxPoints.size() == 1
        assert influxPoints[0].measurement == 'shared_call'
        assert influxPoints[0].tags.function == 'addLinkToBuild'
        assert influxPoints[0].tags.project == 'devops/jenkins-shared-library'
        assert influxPoints[0].fields.value == 1
        assert influxPoints[0].fields.BUILD_ID.toString() == '"42"'
    }

    @Test
    void extraAttributesBecomeTags() {
        script.call(path: '/vars/addLinkToBuild.groovy', deprecated: true)

        assert influxPoints[0].tags.deprecated == true
        assert influxPoints[0].tags.function == 'addLinkToBuild'
        assert !influxPoints[0].tags.containsKey('path')
    }

    @Test
    void deprecatedStepsWarnInTheBuildLog() {
        script.call(path: '/vars/addLinkToBuild.groovy', deprecated: true)

        assert echoMessages == ['WARNING: addLinkToBuild() is deprecated']
        assert influxPoints[0].tags.deprecated == true
    }

    @Test
    void theDeprecationWarningNamesTheReplacementStep() {
        script.call(path: '/vars/addLinkToBuild.groovy', deprecated: 'addBuildLink')

        assert echoMessages == ['WARNING: addLinkToBuild() is deprecated, use addBuildLink() instead']
        assert influxPoints[0].tags.deprecated == 'addBuildLink'
    }

    @Test
    void stepsThatAreNotDeprecatedDoNotWarn() {
        script.call(path: '/vars/addLinkToBuild.groovy')

        assert echoMessages.isEmpty()
    }

    @Test
    void extraAttributesCanNotOverrideTheReservedTags() {
        script.call(path: '/vars/addLinkToBuild.groovy', function: 'somethingElse', project: 'somethingElse')

        assert influxPoints[0].tags.function == 'addLinkToBuild'
        assert influxPoints[0].tags.project == 'devops/jenkins-shared-library'
    }

    @Test
    void nullAttributesAreDropped() {
        script.call(path: '/vars/addLinkToBuild.groovy', deprecated: null)

        assert !influxPoints[0].tags.containsKey('deprecated')
    }

    @Test
    void sendsNothingWithoutAPath() {
        script.call([:])

        assert influxPoints.isEmpty()
    }
}
