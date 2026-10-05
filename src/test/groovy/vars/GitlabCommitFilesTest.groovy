package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class GitlabCommitFilesTest extends BasePipelineTest {
    def script
    List requests
    Map response
    @Before
    void setUp() {
        super.setUp()
        requests = []
        response = [content: '{"id":"new-commit"}']
        binding.setVariable('gitLabToken', 'test-token')
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('error', [String], { throw new IllegalArgumentException(it) })
        helper.registerAllowedMethod('string', [Map], { it })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { List entries, Closure body -> body() })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args -> requests << args; response })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            assert args.returnPojo == true
            new JsonSlurper().parseText(args.text)
        })
        script = loadScript('vars/gitlabCommitFiles.groovy')
    }

    Map arguments() {
        [gitUrl: 'https://gitlab.example:8443/team/firmware.git', branch: 'release/v.1.1.0', commitMessage: 'Update\n\nversions',
            files: [[filePath: 'Manifest', content: 'ncc=5.2.20-b268\n', lastCommitId: 'manifest-sha'],
                    [filePath: 'VERSION', content: '1.1.3\n', lastCommitId: 'version-sha']]]
    }

    @Test
    void postsBothGuardedUpdatesInOneRequestWithNoForce() {
        assert script.call(arguments()).id == 'new-commit'
        assert requests.size() == 1
        def request = requests.first()
        assert request.url == 'https://gitlab.example:8443/api/v4/projects/team%2Ffirmware/repository/commits'
        assert request.httpMode == 'POST'
        assert request.validResponseCodes == '201'
        assert request.consoleLogResponseBody == false
        assert request.customHeaders.first().maskValue == true
        def payload = new JsonSlurper().parseText(request.requestBody)
        assert payload.keySet() == ['branch', 'commit_message', 'actions'] as Set
        assert payload.branch == 'release/v.1.1.0'
        assert payload.actions == [
            [action: 'update', file_path: 'Manifest', content: 'ncc=5.2.20-b268\n', last_commit_id: 'manifest-sha'],
            [action: 'update', file_path: 'VERSION', content: '1.1.3\n', last_commit_id: 'version-sha']]
    }

    @Test
    void rejectsMissingGuardsDuplicatePathsAndCredentialUrlsBeforeHttp() {
        for (change in [{ it.files.first().remove('lastCommitId') },
                        { it.files.last().filePath = 'Manifest' },
                        { it.gitUrl = 'https://user:secret@gitlab.example/team/firmware' }]) {
            def args = arguments()
            change(args)
            try { script.call(args); assert false: 'must reject unsafe input' }
            catch (IllegalArgumentException expected) { }
        }
        assert requests == []
    }

    @Test
    void propagatesConflictWithoutRetryingOrDroppingGuards() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            throw new RuntimeException('400: file changed')
        })
        try { script.call(arguments()); assert false }
        catch (RuntimeException expected) { assert expected.message == '400: file changed' }
        assert requests.size() == 1
    }
}
