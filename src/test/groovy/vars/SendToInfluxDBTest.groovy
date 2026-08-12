package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class SendToInfluxDBTest extends BasePipelineTest {

    def script
    List<Map> requests

    @Before
    void setUp() {
        super.setUp()
        requests = []
        helper.registerAllowedMethod('httpRequest', [Map], { Map args -> requests << args })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        binding.setVariable('env', [JOB_NAME: 'devops/jenkins-shared-library'])
        script = loadScript('vars/sendToInfluxDB.groovy')
    }

    @Test
    void writesPlainTagsAsIs() {
        script.call(measurement: 'shared_call', tags: [function: 'addLinkToBuild'], fields: [value: 1])

        assert requests.size() == 1
        assert requests[0].requestBody.toString() == 'shared_call,function=addLinkToBuild value=1'
    }

    @Test
    void keepsRenderingNullTagValuesInsteadOfThrowing() {
        script.call(measurement: 'shared_call', tags: [branch: null], fields: [value: 1])

        assert requests[0].requestBody.toString() == 'shared_call,branch=null value=1'
    }

    @Test
    void escapesSpacesAndCommasInTags() {
        script.call(
            measurement: 'shared_call',
            tags: [function: 'addLinkToBuild', project: 'Team A, master'],
            fields: [value: 1]
        )

        assert requests[0].requestBody.toString() ==
            'shared_call,function=addLinkToBuild,project=Team\\ A\\,\\ master value=1'
    }
}
