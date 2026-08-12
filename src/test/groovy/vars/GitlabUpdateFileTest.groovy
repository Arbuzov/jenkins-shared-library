package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test

class GitlabUpdateFileTest extends BasePipelineTest {

    static class HttpResponseMock {
        int status = 200
        String content
        int getStatus() { return status }
        String getContent() { return content }
    }

    def script
    def lastHttpRequestArgs

    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('env', [:])

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { creds, body ->
            binding.setVariable('gitLabToken', 'fake-token')
            body()
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return new HttpResponseMock(content: '{"file_path":"deploy/version.txt","branch":"master"}')
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            return new JsonSlurperClassic().parseText(args.text as String)
        })

        script = loadScript('vars/gitlabUpdateFile.groovy')
    }

    @Test
    void updatesFileViaPutWithDerivedProjectPath() {
        def result = script.call([
            filePath: 'deploy/version.txt',
            content: '5\n',
            gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
        ])

        assert result.file_path == 'deploy/version.txt'
        assert result.branch == 'master'

        assert lastHttpRequestArgs.httpMode == 'PUT'
        assert lastHttpRequestArgs.url ==
            'https://gitlab.asatnet.net/api/v4/projects/devops%2Fjenkins-shared-library/repository/files/deploy%2Fversion.txt'
        assert lastHttpRequestArgs.customHeaders[0].name == 'PRIVATE-TOKEN'
        assert lastHttpRequestArgs.customHeaders[0].value == 'fake-token'
        assert lastHttpRequestArgs.validResponseCodes == '100:399'

        def payload = new JsonSlurperClassic().parseText(lastHttpRequestArgs.requestBody as String)
        assert payload.branch == 'master'
        assert payload.content == '5\n'
        assert payload.commit_message == 'Update deploy/version.txt'
    }

    @Test
    void usesProjectIdBranchCommitMessageAndAuthorOverrides() {
        script.call([
            filePath: 'config/state.json',
            content: '{"ready":true}',
            projectId: 1234,
            gitlabBaseUrl: 'https://gitlab.example.com',
            branch: 'main',
            commitMessage: 'ci: stamp ready',
            authorName: 'CI Bot',
            authorEmail: 'ci@example.com',
        ])

        assert lastHttpRequestArgs.url ==
            'https://gitlab.example.com/api/v4/projects/1234/repository/files/config%2Fstate.json'

        def payload = new JsonSlurperClassic().parseText(lastHttpRequestArgs.requestBody as String)
        assert payload.branch == 'main'
        assert payload.commit_message == 'ci: stamp ready'
        assert payload.author_name == 'CI Bot'
        assert payload.author_email == 'ci@example.com'
    }

    @Test
    void failsWhenFilePathMissing() {
        try {
            script.call([content: 'x'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains("'filePath' is required")
        }
    }

    @Test
    void failsWhenContentMissing() {
        try {
            script.call([filePath: 'a.txt'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains("'content' is required")
        }
    }

    @Test
    void failsWhenNeitherGitUrlNorProjectIdAvailable() {
        try {
            script.call([filePath: 'a.txt', content: 'x'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains("provide 'gitUrl' or 'projectId'")
        }
    }
}
