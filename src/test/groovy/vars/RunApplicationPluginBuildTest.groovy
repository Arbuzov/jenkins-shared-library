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
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('fileExists', [String], { String f -> files.containsKey(f) })
        // clone: the real steps parse the file afresh on every read, so a test must
        // not pass just because the step mutated the fixture in place. Shallow is
        // enough while the steps only touch the top-level version key.
        helper.registerAllowedMethod('readYaml', [Map], { Map args -> files[args.file]?.clone() })
        helper.registerAllowedMethod('readJSON', [Map], { Map args -> files[args.file]?.clone() })
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
        def pojoFlags = []
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            pojoFlags << args.returnPojo
            files[args.file]?.clone()
        })
        files['manifest.json'] = [name: 'nms-ng', version: '2.0.3']
        assert script.readManifest().name == 'nms-ng'
        // without returnPojo readJSON answers a net.sf.json.JSONObject, which
        // snakeyaml refuses to dump when this value seeds a generated Manifest
        assert pojoFlags == [true]
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
    void updateManifestGeneratesYamlFromJson() {
        files['manifest.json'] = [name: 'legacy', version: '1.0.0', url: '/legacy']
        script.updateManifest('1.0.0-b5')
        assert written['Manifest'] == [name: 'legacy', version: '1.0.0-b5', url: '/legacy']
        assert written['manifest.json'].version == '1.0.0-b5'
    }

    @Test
    void updateManifestKeepsJsonOwnContent() {
        files['Manifest'] = [name: 'nms-monitoring', version: '2.5.3']
        files['manifest.json'] = [name: 'nms-monitoring', version: '2.5.3', legacyOnly: true]
        script.updateManifest('2.5.3-b99')
        assert written['manifest.json'].legacyOnly == true
        assert !written['Manifest'].containsKey('legacyOnly')
    }

    @Test
    void updateManifestWritesPlainVersionString() {
        files['Manifest'] = [name: 'nms-ng', version: '2.0.3']
        def base = '2.0.3'
        script.updateManifest("${base}-b7")   // a GString, as the pipeline passes it
        // snakeyaml tags a GString as !!org.codehaus.groovy.runtime.GStringImpl
        assert written['Manifest'].version.getClass() == String
        assert written['Manifest'].version == '2.0.3-b7'
    }
}
