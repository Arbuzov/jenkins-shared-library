package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class LinkUpstreamFromCommitTest extends BasePipelineTest {

    def script
    List<Map> linkArgs
    List<String> echoMessages
    Map envMap
    String shStub
    boolean shCalled
    Throwable shThrow

    @Before
    void setUp() {
        super.setUp()
        linkArgs = []
        echoMessages = []
        envMap = [:]
        shStub = null
        shCalled = false
        shThrow = null

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('linkUpstreamBuild', [Map], { Map args ->
            linkArgs << args
            return true
        })
        helper.registerAllowedMethod('sh', [Map], { Map args ->
            shCalled = true
            if (shThrow != null) {
                throw shThrow
            }
            return shStub
        })

        binding.setVariable('env', envMap)
        script = loadScript('vars/linkUpstreamFromCommit.groovy')
    }

    // ---- parseUpstreamCoordinates (pure) --------------------------------------

    @Test
    void parsesExplicitTrailers() {
        def coords = script.parseUpstreamCoordinates(
            'chore: results\n\nJenkins-Upstream-Project: v_v/test-pipelines/test-orchestrator/master\nJenkins-Upstream-Build: 42\n')
        assert coords.project == 'v_v/test-pipelines/test-orchestrator/master'
        assert coords.build == 42
        assert coords.source == 'trailer'
    }

    @Test
    void parsesJenkinsBuildUrl() {
        def coords = script.parseUpstreamCoordinates(
            'chore: bump\n\nhttps://jenkins.asatnet.net/job/v_v/job/test-pipelines/job/test-orchestrator/job/master/57/')
        assert coords.project == 'v_v/test-pipelines/test-orchestrator/master'
        assert coords.build == 57
        assert coords.source == 'buildUrl'
    }

    @Test
    void parsesOrchestratorSentence() {
        def coords = script.parseUpstreamCoordinates(
            'Auto-bump verified versions from grp/sub/proj/master #99')
        assert coords.project == 'grp/sub/proj/master'
        assert coords.build == 99
        assert coords.source == 'sentence'
    }

    @Test
    void decodesPercentEncodedFolderNamesInBuildUrl() {
        def coords = script.parseUpstreamCoordinates(
            'x\n\nhttps://ci/job/a%20b/job/c/12/')
        assert coords.project == 'a b/c'
        assert coords.build == 12
        assert coords.source == 'buildUrl'
    }

    @Test
    void parsesBuildUrlWithNumericFolderSegment() {
        def coords = script.parseUpstreamCoordinates('bump\n\nhttps://ci/job/2024/job/master/5/')
        assert coords.project == '2024/master'
        assert coords.build == 5
        assert coords.source == 'buildUrl'
    }

    @Test
    void parsesBuildUrlWithContextPathPrefix() {
        def coords = script.parseUpstreamCoordinates(
            'bump\n\nhttps://ci.example.com/jenkins/job/grp/job/proj/job/master/9/')
        assert coords.project == 'grp/proj/master'
        assert coords.build == 9
        assert coords.source == 'buildUrl'
    }

    @Test
    void ignoresMalformedPercentEscapeInUrl() {
        // A stray "/job/foo%/12/" must not throw; with no other coordinates the
        // parse is a safe no-op.
        assert script.parseUpstreamCoordinates('logs at https://ci/job/foo%/12/ ok') == null
    }

    @Test
    void doesNotMatchNonBuildJobUrl() {
        // A console/sub-resource URL (build number is not the last path segment)
        // is not a build URL and must not be mis-parsed.
        assert script.parseUpstreamCoordinates('see https://ci/job/a/job/b/57/console for logs') == null
    }

    @Test
    void rejectsUrlWhoseSeparatorsAreNotJob() {
        // "/job/foo/bar/42/" is not a real Jenkins build URL (segments must
        // alternate name/job/name/...): reject rather than invent project "foo".
        assert script.parseUpstreamCoordinates('https://x/job/foo/bar/42/') == null
    }

    @Test
    void parsesSecondBuildUrlWhenFirstIsRejected() {
        // A malformed first /job/.../<n>/ must not shadow a later valid BUILD_URL.
        def coords = script.parseUpstreamCoordinates(
            'x https://ci/job/foo%/9/ then https://ci/job/a/job/b/5/')
        assert coords.project == 'a/b'
        assert coords.build == 5
        assert coords.source == 'buildUrl'
    }

    @Test
    void trailersTakePrecedenceOverUrlAndSentence() {
        def coords = script.parseUpstreamCoordinates(
            'Auto-bump verified versions from wrong/job #1\n' +
            'https://ci/job/wrong/job/job/2/\n' +
            'Jenkins-Upstream-Project: right/job\nJenkins-Upstream-Build: 3\n')
        assert coords.project == 'right/job'
        assert coords.build == 3
        assert coords.source == 'trailer'
    }

    @Test
    void parsesJobFullNamesContainingSpaces() {
        // Jenkins folder/job names may contain spaces — the project capture must
        // not stop at the first whitespace.
        def viaTrailer = script.parseUpstreamCoordinates(
            'x\n\nJenkins-Upstream-Project: My Folder/test orchestrator/master\nJenkins-Upstream-Build: 11\n')
        assert viaTrailer.project == 'My Folder/test orchestrator/master'
        assert viaTrailer.build == 11
        assert viaTrailer.source == 'trailer'

        def viaSentence = script.parseUpstreamCoordinates(
            'Auto-bump verified versions from My Folder/orchestrator #12')
        assert viaSentence.project == 'My Folder/orchestrator'
        assert viaSentence.build == 12
        assert viaSentence.source == 'sentence'
    }

    @Test
    void returnsNullForOrdinaryCommit() {
        assert script.parseUpstreamCoordinates('fix: correct a typo in README') == null
        assert script.parseUpstreamCoordinates('') == null
        assert script.parseUpstreamCoordinates(null) == null
    }

    // ---- call() dispatch ------------------------------------------------------

    @Test
    void callLinksUsingMessageArg() {
        def result = script.call([
            message: 'Jenkins-Upstream-Project: a/b/master\nJenkins-Upstream-Build: 7'
        ])
        assert linkArgs.size() == 1
        assert linkArgs[0].project == 'a/b/master'
        assert linkArgs[0].build == 7
        assert result.linked == true
        assert result.source == 'trailer'
        assert !shCalled
    }

    @Test
    void callReadsGitlabPluginEnvVarByDefault() {
        envMap.GITLAB_OA_LAST_COMMIT_MESSAGE =
            'Auto-bump verified versions from v_v/test-pipelines/test-orchestrator/master #123\n\nhttps://ci/job/v_v/job/test-pipelines/job/test-orchestrator/job/master/123/'
        script.call([:])
        assert linkArgs.size() == 1
        assert linkArgs[0].project == 'v_v/test-pipelines/test-orchestrator/master'
        assert linkArgs[0].build == 123
        assert !shCalled
    }

    @Test
    void callHonoursMessageEnvVarOverride() {
        envMap.MY_MSG = 'Jenkins-Upstream-Project: p/q\nJenkins-Upstream-Build: 5'
        script.call([messageEnvVar: 'MY_MSG'])
        assert linkArgs.size() == 1
        assert linkArgs[0].project == 'p/q'
        assert linkArgs[0].build == 5
    }

    @Test
    void callFallsBackToGitLogWhenEnabled() {
        shStub = 'Auto-bump verified versions from f/g/master #8\n'
        script.call([gitFallback: true])
        assert shCalled
        assert linkArgs.size() == 1
        assert linkArgs[0].project == 'f/g/master'
        assert linkArgs[0].build == 8
    }

    @Test
    void callTreatsStringFalseGitFallbackAsDisabled() {
        // A Jenkins boolean param arrives as the string "false" (Groovy-truthy).
        shStub = 'Auto-bump verified versions from f/g/master #8\n'
        def result = script.call([gitFallback: 'false'])
        assert result == null
        assert !shCalled
        assert linkArgs.isEmpty()
    }

    @Test
    void callEnablesGitFallbackForStringTrue() {
        shStub = 'Auto-bump verified versions from f/g/master #8\n'
        script.call([gitFallback: 'true'])
        assert shCalled
        assert linkArgs.size() == 1
        assert linkArgs[0].project == 'f/g/master'
    }

    @Test
    void callRethrowsAbortFromGitFallback() {
        // A pipeline abort while reading git must not be swallowed into a no-op.
        shThrow = new InterruptedException('aborted')
        try {
            script.call([gitFallback: true])
            assert false, 'should have propagated the interruption'
        } catch (InterruptedException expected) {
            assert expected.message == 'aborted'
        }
    }

    @Test
    void callSwallowsOrdinaryGitFailure() {
        shThrow = new RuntimeException('not a git repo')
        def result = script.call([gitFallback: true])
        assert result == null
        assert linkArgs.isEmpty()
    }

    @Test
    void callDoesNotReadGitByDefault() {
        // Without gitFallback, an unrelated build (e.g. a GenericTrigger deletion
        // webhook checking out master) must not read HEAD and mis-attribute an
        // upstream cause for the branch's latest auto-bump commit.
        shStub = 'Auto-bump verified versions from f/g/master #8\n'
        def result = script.call([:])
        assert result == null
        assert linkArgs.isEmpty()
        assert !shCalled
    }

    @Test
    void callIsNoOpForOrdinaryCommit() {
        def result = script.call([message: 'fix: ordinary developer commit'])
        assert result == null
        assert linkArgs.isEmpty()
    }

    @Test
    void callSkipsWhenGitFallbackYieldsBlank() {
        shStub = '   '
        def result = script.call([gitFallback: true])
        assert result == null
        assert linkArgs.isEmpty()
    }
}
