package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test
import org.junit.Assume

class SortDebianVersionsTest extends BasePipelineTest {
    def script
    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('error', [String], { throw new IllegalArgumentException(it) })
        script = loadScript('vars/sortDebianVersions.groovy')
    }

    @Test
    void ranksBuildNumbersEpochsRevisionsAndPrereleases() {
        def versions = ['1.1.0-9', '1.1.0-24', '1.1.0-100', '1.1.0~rc1-1', '1.1.0',
                        '1.1.0-b9', '1.1.0-b10', '1:0.1-1', '1.1.0+b2', '1.1.0+b10']
        assert script.call(versions: versions) == ['1:0.1-1', '1.1.0+b10', '1.1.0+b2',
            '1.1.0-b10', '1.1.0-b9', '1.1.0-100', '1.1.0-24', '1.1.0-9', '1.1.0', '1.1.0~rc1-1']
        assert versions.first() == '1.1.0-9'
    }

    @Test
    void comparesLongDigitRunsAndLeadingZerosWithoutOverflow() {
        assert script.compareVersion('1.0-b9999999999999999999999', '1.0-b10000000000000000000000') < 0
        assert script.compareVersion('1.0-01', '1.0-1') == 0
        assert script.compareVersion('1.0', '1.0-0') == 0
        assert script.call(versions: ['1.0-1', null, '1.0-1']) == ['1.0-1']
    }

    @Test
    void agreesWithDpkgForAllPairsOfRepresentativeDebianVersions() {
        // dpkg is the oracle: this catches a comparator that just reproduces the expected fixtures.
        def probe
        try { probe = new ProcessBuilder('dpkg', '--version').start() }
        catch (IOException unavailable) { Assume.assumeNoException(unavailable); return }
        assert probe.waitFor() == 0
        def versions = ['1.0', '1.0-0', '1.0-01', '1.0-1', '1.0-2', '1.0-10', '1.0-b9', '1.0-b10',
            '1.0+b2', '1.0+b10', '1.0~rc1', '1.0~~', '1.0a', '1.0.a', '1.0+git', '1:0.1',
            '2:0.1-1', '1.0-1~bpo11+1', '1.0-1+deb11u2', '1.0-1+deb11u10', '1.0-999999999999999999999']
        for (a in versions) {
            for (b in versions) {
                int comparison = script.compareVersion(a, b)
                String operation = comparison == 0 ? 'eq' : comparison < 0 ? 'lt' : 'gt'
                def process = new ProcessBuilder('dpkg', '--compare-versions', a, operation, b).start()
                assert process.waitFor() == 0: "${a} ${operation} ${b} disagrees with dpkg"
            }
        }
    }
}
