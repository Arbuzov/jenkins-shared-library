package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class SortVersionsTest extends BasePipelineTest {

    private static final String BUILD_PATTERN = /\d+\.\d+\.\d+-b\d+(-offline)?/

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

        script = loadScript('vars/sortVersions.groovy')
    }

    // ---------- configurator cases ----------

    @Test
    void keepsTheBuildVersionsOfTheConfiguratorListNewestFirst() {
        def sorted = script.call([
            versions: ['latest', '1.0.0-b99', '1.2.0-encryption-b9', '1.0.0-b100', '2.0.2.b36',
                       '1.2.0-b1', '2.0.3-b69-offline', '2.0.3-b69', '1.0.0-b99'],
            pattern: BUILD_PATTERN,
        ])

        assert sorted == ['2.0.3-b69', '2.0.3-b69-offline', '1.2.0-b1', '1.0.0-b100', '1.0.0-b99']
        assert usageReports.size() == 1
    }

    @Test
    void readsTheBuildNumberRightAfterTheVersionOfASuffixedBuild() {
        // b69 sorts before b70 as a string, so only the build number puts b70 first
        def sorted = script.call([versions: ['2.0.3-b69-offline', '2.0.3-b9-offline', '2.0.3-b70-offline'], pattern: BUILD_PATTERN])

        assert sorted == ['2.0.3-b70-offline', '2.0.3-b69-offline', '2.0.3-b9-offline']
    }

    // ---------- default pattern ----------

    @Test
    void keepsPlainAndBuildVersionsAndDropsSuffixedOnesByDefault() {
        def sorted = script.call([versions: ['1.0.0', '1.0.0-b3', '1.0.0-b3-offline', 'latest']])

        assert sorted == ['1.0.0-b3', '1.0.0']
    }

    @Test
    void ranksAVersionWithoutABuildNumberBelowBuildZero() {
        def sorted = script.call([versions: ['1.0.0', '1.0.0-b0']])

        assert sorted == ['1.0.0-b0', '1.0.0']
    }

    // ---------- order ----------

    @Test
    void comparesBuildNumbersNumerically() {
        def sorted = script.call([versions: ['1.0.0-b99', '1.0.0-b100', '1.0.0-b9']])

        assert sorted == ['1.0.0-b100', '1.0.0-b99', '1.0.0-b9']
    }

    @Test
    void comparesVersionPartsNumerically() {
        def sorted = script.call([versions: ['1.9.0', '1.10.0', '1.9.10', '1.9.9', '10.0.0', '9.0.0']])

        assert sorted == ['10.0.0', '9.0.0', '1.10.0', '1.9.10', '1.9.9', '1.9.0']
    }

    @Test
    void putsAValueWithoutAVersionPrefixLastUnderAPermissivePattern() {
        def sorted = script.call([versions: ['latest', '1.0.0-b1', 'beta', '1.1.0'], pattern: /.*/])

        assert sorted == ['1.1.0', '1.0.0-b1', 'beta', 'latest']
    }

    // ---------- input handling ----------

    @Test
    void removesDuplicates() {
        def sorted = script.call([versions: ['1.0.0-b1', '1.0.0-b2', '1.0.0-b1']])

        assert sorted == ['1.0.0-b2', '1.0.0-b1']
    }

    @Test
    void skipsNullEntries() {
        def sorted = script.call([versions: [null, '1.0.0-b1', null]])

        assert sorted == ['1.0.0-b1']
    }

    @Test
    void comparesEntriesByTheirStringForm() {
        String build = 'b2'
        def sorted = script.call([versions: ["1.0.0-${build}", '1.0.0-b2', '1.0.0-b1']])

        assert sorted == ['1.0.0-b2', '1.0.0-b1']
        assert sorted.every { it instanceof String }
    }

    @Test
    void leavesTheCallersCollectionUnchangedAndReturnsANewList() {
        def versions = ['1.0.0-b1', 'latest', '1.0.0-b2', '1.0.0-b1']

        def sorted = script.call([versions: versions])

        assert versions == ['1.0.0-b1', 'latest', '1.0.0-b2', '1.0.0-b1']
        assert sorted == ['1.0.0-b2', '1.0.0-b1']
        assert sorted instanceof ArrayList
        assert !sorted.is(versions)
    }

    @Test
    void acceptsAnyCollection() {
        def sorted = script.call([versions: new LinkedHashSet(['1.0.0-b1', '1.0.0-b2'])])

        assert sorted == ['1.0.0-b2', '1.0.0-b1']
    }

    @Test
    void returnsAnEmptyListWhenNothingMatches() {
        assert script.call([versions: ['latest', '2.0.2.b36']]) == []
        assert script.call([versions: []]) == []
    }

    // ---------- validation ----------

    @Test
    void failsWhenVersionsIsMissing() {
        try {
            script.call([:])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("sortVersions(): 'versions' is required")
        }
    }

    @Test
    void failsWhenVersionsIsNull() {
        try {
            script.call([versions: null])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("sortVersions(): 'versions' is required")
        }
    }

    @Test
    void failsWhenVersionsIsNotACollection() {
        try {
            script.call([versions: '1.0.0-b1'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'versions' must be a Collection")
        }
    }
}
