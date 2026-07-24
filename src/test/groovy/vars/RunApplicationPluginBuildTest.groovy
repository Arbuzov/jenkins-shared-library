package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class RunApplicationPluginBuildTest extends BasePipelineTest {

    def script
    Map<String, Object> files
    Map<String, Object> written

    @Before
    void setUp() {
        super.setUp()
        files = [:]
        written = [:]
        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('fileExists', [String], { String f -> files.containsKey(f) })
        helper.registerAllowedMethod('readYaml', [Map], { Map args -> files[args.file] })
        helper.registerAllowedMethod('readJSON', [Map], { Map args -> files[args.file] })
        helper.registerAllowedMethod('writeYaml', [Map], { Map args ->
            // writeYaml throws at runtime on an existing file unless overwrite:true
            assert args.overwrite == true
            written[args.file] = args.data
        })
        helper.registerAllowedMethod('writeJSON', [Map], { Map args -> written[args.file] = args.json })
        script = loadScript('vars/runApplicationPluginBuild.groovy')
    }

    @Test
    void readManifestPrefersYaml() {
        files['Manifest'] = [name: 'nms-monitoring', version: '2.5.3']
        files['manifest.json'] = [name: 'legacy', version: '9.9.9']
        def m = script.readManifest()
        assert m.name == 'nms-monitoring'
        assert m.version == '2.5.3'
    }

    @Test
    void readManifestFallsBackToJson() {
        files['manifest.json'] = [name: 'nms-ng', version: '2.0.3']
        assert script.readManifest().name == 'nms-ng'
    }

    @Test
    void updateManifestStampsBothFormats() {
        files['Manifest'] = [name: 'nms-monitoring', version: '2.5.3', permissions: [x: 1]]
        files['manifest.json'] = [name: 'nms-monitoring', version: '2.5.3']
        script.updateManifest('2.5.3-b99')
        assert written['Manifest'].version == '2.5.3-b99'
        assert written['manifest.json'].version == '2.5.3-b99'
        assert written['Manifest'].permissions == [x: 1]   // untouched fields preserved
    }

    @Test
    void updateManifestJsonOnlyWhenNoYaml() {
        files['manifest.json'] = [name: 'legacy', version: '1.0.0']
        script.updateManifest('1.0.0-b5')
        assert written['manifest.json'].version == '1.0.0-b5'
        assert !written.containsKey('Manifest')
    }
}
