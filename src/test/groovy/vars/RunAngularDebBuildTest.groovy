package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class RunAngularDebBuildTest extends BasePipelineTest {

    def script
    List<Map> found
    String raised

    @Before
    void setUp() {
        super.setUp()
        found = []
        raised = null
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('findFiles', [Map], { Map args -> found })
        helper.registerAllowedMethod('error', [String], { String message ->
            raised = message
            throw new RuntimeException(message)
        })
        script = loadScript('vars/runAngularDebBuild.groovy')
    }

    @Test
    void resolveDebPackageReturnsTheSingleArtifact() {
        found = [[name: 'app_1.1.1-47-47_all.deb', path: 'artifacts/app_1.1.1-47-47_all.deb']]
        assert script.resolveDebPackage() == 'artifacts/app_1.1.1-47-47_all.deb'
        assert raised == null
    }

    @Test
    void resolveDebPackageFailsWhenNothingMatches() {
        found = []
        try {
            script.resolveDebPackage()
            assert false, 'expected error()'
        } catch (RuntimeException ignored) {
        }
        assert raised.contains('found 0')
        assert raised.contains('none')
    }

    @Test
    void resolveDebPackageFailsOnAmbiguity() {
        found = [[name: 'a.deb', path: 'artifacts/a.deb'], [name: 'b.deb', path: 'artifacts/b.deb']]
        try {
            script.resolveDebPackage()
            assert false, 'expected error()'
        } catch (RuntimeException ignored) {
        }
        assert raised.contains('found 2')
        assert raised.contains('a.deb, b.deb')
    }

    @Test
    void callRequiresProjectName() {
        try {
            script.call([:])
            assert false, 'expected error()'
        } catch (RuntimeException ignored) {
        }
        assert raised.contains('PROJECT_NAME is required')
    }
}
