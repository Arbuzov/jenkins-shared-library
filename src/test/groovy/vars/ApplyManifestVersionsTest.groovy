package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class ApplyManifestVersionsTest extends BasePipelineTest {

    static final String MANIFEST =
        'name: nms-single-meta\r\nplugins:\r\n  nms-ng: 2.0.3-b69-offline\r\n  nms-monitoring: 2.5.3-b90\r\n' +
        'environment:\r\n  NMS_BACKEND_VERSION: 1.0.0-b694\r\n'

    def script
    def usageReports

    @Before
    void setUp() {
        super.setUp()
        usageReports = []

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> usageReports << args })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })

        script = loadScript('vars/applyManifestVersions.groovy')
    }

    // ---------- YAML and properties ----------

    @Test
    void setsAChangedValueAndKeepsTheRestOfACrlfManifest() {
        def result = script.call([text: MANIFEST,
                                  versions: ['nms-ng': '2.0.3-b70', NMS_BACKEND_VERSION: '1.0.0-b694']])

        assert result.changed == ['nms-ng': '2.0.3-b70']
        assert result.text == MANIFEST.replace('nms-ng: 2.0.3-b69-offline', 'nms-ng: 2.0.3-b70')
        assert usageReports.size() == 1
    }

    @Test
    void setsAPropertiesLineAndKeepsItsSeparator() {
        def result = script.call([text: 'ncc=5.2.20\nmcd4-lite = 1.5.13-b69\n',
                                  versions: [ncc: '5.2.21', 'mcd4-lite': '1.5.14-b1']])

        assert result.text == 'ncc=5.2.21\nmcd4-lite = 1.5.14-b1\n'
        assert result.changed == [ncc: '5.2.21', 'mcd4-lite': '1.5.14-b1']
    }

    @Test
    void returnsTheTextUnchangedWithoutVersions() {
        def result = script.call([text: MANIFEST])

        assert result.text == MANIFEST
        assert result.changed == [:]
    }

    @Test
    void acceptsAnEmptyText() {
        def result = script.call([text: '', versions: [ncc: '5.2.21']])

        assert result.text == ''
        assert result.changed == [:]
    }

    @Test
    void leavesAKeyWithNoLineOut() {
        def result = script.call([text: 'ncc=5.2.20\n', versions: [phs: '3.1.0']])

        assert result.text == 'ncc=5.2.20\n'
        assert result.changed == [:]
    }

    @Test
    void editsTheLastLineWithoutATrailingNewline() {
        def result = script.call([text: 'name: nms-single-meta\nversion: 1.1.0', versions: [version: '1.2.0']])

        assert result.text == 'name: nms-single-meta\nversion: 1.2.0'
        assert result.changed == [version: '1.2.0']
    }

    // ---------- whitespace ----------

    @Test
    void setsAnEmptyValueWithoutSwallowingTheNextLine() {
        def result = script.call([text: 'ncc=\nmcd4-lite=1.5.13-b69\n', versions: [ncc: '5.2.20']])

        assert result.text == 'ncc=5.2.20\nmcd4-lite=1.5.13-b69\n'
        assert result.changed == [ncc: '5.2.20']
    }

    @Test
    void setsAnEmptyYamlValueAfterASpaceSoTheLineStaysAMappingEntry() {
        def result = script.call([text: 'environment:\n  NMS_BACKEND_VERSION:\n  NMS_FRONTEND_VERSION: 1.1.1-b603\n',
                                  versions: [NMS_BACKEND_VERSION: '1.0.0-b695']])

        assert result.text == 'environment:\n  NMS_BACKEND_VERSION: 1.0.0-b695\n  NMS_FRONTEND_VERSION: 1.1.1-b603\n'
        assert result.changed == [NMS_BACKEND_VERSION: '1.0.0-b695']
    }

    @Test
    void keepsAColonWithoutASpaceWhenTheLineHadAValue() {
        def result = script.call([text: 'x:1\n', versions: [x: '2']])

        assert result.text == 'x:2\n'
    }

    @Test
    void doesNotReportTheValueALineAlreadyHasBeforeTrailingWhitespace() {
        def result = script.call([text: 'ncc=5.2.20 \n', versions: [ncc: '5.2.20']])

        assert result.text == 'ncc=5.2.20 \n'
        assert result.changed == [:]
    }

    @Test
    void keepsTheTrailingWhitespaceOfAChangedLine() {
        def result = script.call([text: 'ncc=5.2.20 \n', versions: [ncc: '5.2.21']])

        assert result.text == 'ncc=5.2.21 \n'
        assert result.changed == [ncc: '5.2.21']
    }

    @Test
    void keepsTheIndentationOfATabIndentedLine() {
        def result = script.call([text: 'plugins:\n\tnms-ng:\t2.0.3-b69\n', versions: ['nms-ng': '2.0.3-b70']])

        assert result.text == 'plugins:\n\tnms-ng:\t2.0.3-b70\n'
    }

    // ---------- keys ----------

    @Test
    void setsEveryLineThatCarriesTheKey() {
        def result = script.call([text: 'ncc=5.2.20\nother=1\nncc=5.2.19\n', versions: [ncc: '5.2.21']])

        assert result.text == 'ncc=5.2.21\nother=1\nncc=5.2.21\n'
        assert result.changed == [ncc: '5.2.21']
    }

    @Test
    void leavesAKeyThatExtendsTheGivenKeyAlone() {
        String text = 'NMS_BACKEND_VERSION_OLD: 1.0.0-b600\nOLD_NMS_BACKEND_VERSION: 1.0.0-b500\n' +
                      'NMS_BACKEND_VERSION: 1.0.0-b694\n'

        def result = script.call([text: text, versions: [NMS_BACKEND_VERSION: '1.0.0-b700']])

        assert result.text == 'NMS_BACKEND_VERSION_OLD: 1.0.0-b600\nOLD_NMS_BACKEND_VERSION: 1.0.0-b500\n' +
                              'NMS_BACKEND_VERSION: 1.0.0-b700\n'
        assert result.changed == [NMS_BACKEND_VERSION: '1.0.0-b700']
    }

    @Test
    void matchesAKeyWithRegexCharactersLiterally() {
        def result = script.call([text: 'axb=1\na.b=1\n', versions: ['a.b': '2']])

        assert result.text == 'axb=1\na.b=2\n'
        assert result.changed == ['a.b': '2']
    }

    @Test
    void doesNotMatchAKeyWithRegexCharactersAgainstAnotherKey() {
        def result = script.call([text: 'axb=1\n', versions: ['a.b': '2']])

        assert result.text == 'axb=1\n'
        assert result.changed == [:]
    }

    // ---------- values ----------

    @Test
    void insertsAReplacementValueLiterally() {
        def result = script.call([text: 'ncc=5.2.20\nmcd=1\n', versions: [ncc: 'a$1b', mcd: 'c\\d']])

        assert result.text == 'ncc=a$1b\nmcd=c\\d\n'
        assert result.changed == [ncc: 'a$1b', mcd: 'c\\d']
    }

    @Test
    void skipsNullEmptyAndMultiLineValues() {
        String text = 'a=1\nb=2\nc=3\nd=4\ne=5\n'

        def result = script.call([text: text, versions: [a: null, b: '', c: '5\n6', d: '7\r8', e: '9\u20280']])

        assert result.text == text
        assert result.changed == [:]
    }

    @Test
    void writesANonStringValueAsItsString() {
        def result = script.call([text: 'replicas: 1\n', versions: [replicas: 3]])

        assert result.text == 'replicas: 3\n'
        assert result.changed == [replicas: '3']
    }

    // ---------- validation ----------

    @Test
    void failsWithoutText() {
        try {
            script.call([versions: [ncc: '5.2.21']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("applyManifestVersions(): 'text' is required")
        }
    }

    @Test
    void failsOnATextThatIsNotAString() {
        try {
            script.call([text: [ncc: '5.2.20']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'text' must be a String")
        }
    }

    @Test
    void failsOnVersionsThatAreNotAMap() {
        try {
            script.call([text: 'ncc=5.2.20\n', versions: ['ncc', '5.2.21']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'versions' must be a Map")
        }
    }

    @Test
    void failsOnABlankKey() {
        try {
            script.call([text: '=5.2.20\n', versions: [' ': '5.2.21']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'versions' has a null or blank key")
        }
    }

    @Test
    void failsOnANullKey() {
        Map versions = [:]
        versions.put(null, '5.2.21')
        try {
            script.call([text: 'ncc=5.2.20\n', versions: versions])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'versions' has a null or blank key")
        }
    }
}
