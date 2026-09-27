package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Runs the directory mode's Inspect and Download scripts the way Jenkins does, with sh -e,
 * against a fake smbclient. Skipped where there is no sh with GNU date and find.
 */
class RunSmbToNexusPipelineShellTest extends BasePipelineTest {

    @Rule
    public TemporaryFolder workspace = new TemporaryFolder()

    def script
    Map scripts

    static final String LOAD = 'fpga_mesh_a7_20260923_1'

    /**
     * Prints ENTRIES and SUB_ENTRIES ("name attribute size seconds-ago" per line) the way
     * smbclient 4.13 prints `recurse ON; ls`. For mget, writes WRITE_BYTES bytes and exits 0,
     * as smbclient does even when a file failed.
     */
    static final String FAKE_SMBCLIENT = '''#!/bin/sh
case "$*" in
    *mget*)
        head -c "$WRITE_BYTES" /dev/zero > "content/$LOAD/top.sof"
        exit 0
        ;;
esac
[ -z "$FAIL_LISTING" ] || exit 1
now=$(date +%s)
entries() {
    printf '%s\\n' "$1" | while read -r name attribute size age; do
        [ -n "$name" ] || continue
        mtime=$(date -d "@$((now - age))" '+%a %b %e %H:%M:%S %Y')
        if [ -n "$FORMAT_DRIFT" ]; then
            printf '  %-36s %s %9s %s\\n' "$name" "$attribute" "$size" "$mtime"
        else
            printf '  %-36s %s %9s  %s\\n' "$name" "$attribute" "$size" "$mtime"
        fi
    done
}
entries "$ENTRIES"
printf '\\n\\\\sub\\n'
entries "$SUB_ENTRIES"
printf '\\n\\t\\t1073741823 blocks of size 4096. 52428800 blocks available\\n'
'''

    @Before
    void setUp() {
        super.setUp()
        Assume.assumeTrue('needs sh with GNU date and find', shellWorks())
        scripts = [:]
        binding.setVariable('env', [:])
        helper.registerAllowedMethod('sh', [Map], { Map args ->
            scripts[args.label] = args.script
            return args.returnStdout ? '62238574 5400' : null
        })
        helper.registerAllowedMethod('withEnv', [List, Closure], { List vars, Closure body -> body() })
        helper.registerAllowedMethod('zip', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        script = loadScript('vars/runSmbToNexusPipeline.groovy')
        script.fetchDirectory([file: LOAD + '.zip', load: LOAD, url: 'x'])
    }

    static boolean shellWorks() {
        try {
            def process = new ProcessBuilder('sh', '-c', 'date -d @0 +%s && find . -maxdepth 0 -printf x')
                .redirectErrorStream(true)
                .start()
            process.inputStream.text
            return process.waitFor() == 0
        } catch (IOException exception) {
            return false
        }
    }

    /** Runs the script sh got under label in a fresh workspace, with the fake smbclient first on the PATH. */
    Map run(String label, Map environment) {
        File dir = workspace.newFolder()
        File bin = new File(dir, 'bin')
        bin.mkdirs()
        File smbclient = new File(bin, 'smbclient')
        smbclient.setText(FAKE_SMBCLIENT, 'UTF-8')
        smbclient.setExecutable(true)
        // a file rather than sh -c: Windows would mangle the quotes in a command-line argument
        File scriptFile = new File(dir, 'step.sh')
        scriptFile.setText(scripts[label], 'UTF-8')
        def builder = new ProcessBuilder('sh', '-e', scriptFile.absolutePath).directory(dir)
        builder.environment().putAll([LOAD: LOAD, SMB_OPTS: '', SMB_DIR: 'FPGA_Test_Load', SMB_SHARE: '//share/data'] + environment)
        builder.environment().put('PATH', bin.absolutePath + File.pathSeparator + System.getenv('PATH'))
        def process = builder.start()
        def out = process.inputStream.getText('UTF-8')
        def err = process.errorStream.getText('UTF-8')
        return [exit: process.waitFor(), out: out.trim(), err: err]
    }

    /** bytes and age that Inspect printed. */
    List<Long> inspect(Map environment) {
        def result = run("Inspect ${LOAD}", environment)
        assert result.exit == 0: result.err
        return result.out.tokenize(' ').collect { it as long }
    }

    @Test
    void inspectTakesTheAgeFromTheDotEntry() {
        // the copy keeps each file's mtime, so the new file shows only in the directory's "."
        def (bytes, age) = inspect(ENTRIES: '''
            .     D 0        300
            ..    D 0        99999
            top.sof   A 62238000 864000
            a.core.rbf A 574     864000
        ''')

        assert bytes == 62238574
        assert age >= 300 && age < 400
    }

    @Test
    void inspectIgnoresTheParentDirectory() {
        // ".." changes whenever another load lands next to this one
        def (bytes, age) = inspect(ENTRIES: '''
            .     D 0        7200
            ..    D 0        60
            top.sof   A 62238000 7200
        ''')

        assert bytes == 62238000
        assert age >= 7200 && age < 7300
    }

    @Test
    void inspectLooksIntoSubdirectories() {
        def (bytes, age) = inspect(
            ENTRIES: '''
                .     D 0    7200
                ..    D 0    7200
                sub   D 0    7200
            ''',
            SUB_ENTRIES: '''
                .       D 0   7200
                ..      D 0   7200
                late.stp A 1000 120
            '''
        )

        assert bytes == 1000
        assert age >= 120 && age < 220
    }

    @Test
    void inspectReportsAnEmptyLoadAsZeroBytes() {
        def (bytes, age) = inspect(ENTRIES: '''
            .     D 0    7200
            ..    D 0    7200
        ''')

        assert bytes == 0
        assert age >= 7200
    }

    @Test
    void inspectFailsOnAListingItCannotParse() {
        def result = run("Inspect ${LOAD}", [FORMAT_DRIFT: '1', ENTRIES: 'top.sof A 62238000 7200'])

        assert result.exit != 0
        assert result.err.contains("${LOAD}: no sizes and mtimes in its listing")
    }

    @Test
    void inspectFailsWhenTheShareCannotBeListed() {
        def result = run("Inspect ${LOAD}", [FAIL_LISTING: '1'])

        assert result.exit != 0
        assert result.err.contains("${LOAD}: cannot list it on the share")
    }

    @Test
    void downloadPassesWhenEveryByteArrived() {
        def result = run("Download ${LOAD}", [BYTES: '1000', WRITE_BYTES: '1000'])

        assert result.exit == 0: result.err
        assert result.out.contains("${LOAD}: downloaded 1000 of 1000 bytes")
    }

    @Test
    void downloadFailsWhenMgetDroppedAFile() {
        // smbclient 4.13 mget exits 0 when a file fails; only the byte count shows it
        def result = run("Download ${LOAD}", [BYTES: '1000', WRITE_BYTES: '900'])

        assert result.exit != 0
        assert result.out.contains("${LOAD}: downloaded 900 of 1000 bytes")
    }
}
