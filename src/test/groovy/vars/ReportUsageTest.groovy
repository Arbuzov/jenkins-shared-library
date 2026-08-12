package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class ReportUsageTest extends BasePipelineTest {

    def script
    List<Map> influxPoints

    @Before
    void setUp() {
        super.setUp()
        influxPoints = []
        helper.registerAllowedMethod('sendToInfluxDB', [Map], { Map args -> influxPoints << args })
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
