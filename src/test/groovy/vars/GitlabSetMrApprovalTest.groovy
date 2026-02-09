package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GitlabSetMrApprovalTest extends BasePipelineTest {

    static class HttpResponseMock {
        String content
        String getContent() { return content }
    }

    def script
    def lastHttpRequestArgs

    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return new HttpResponseMock(content: '{"ok":true}')
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            return [ok: true]
        })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { creds, body ->
            binding.setVariable('privateToken', 'fake-token')
            body()
        })
        script = loadScript('vars/gitlabSetMrApproval.groovy')
    }

    @Test
    void approveUsesGitlabProjectHttpUrl() {
        binding.setVariable('env', [
            CHANGE_ID: '77',
            GITLAB_PROJECT_HTTP_URL: 'https://gitlab.example.com/group/project'
        ])

        script.call([projectId: '123'])

        assert lastHttpRequestArgs.url == 'https://gitlab.example.com/api/v4/projects/123/merge_requests/77/approve'
        assert lastHttpRequestArgs.httpMode == 'POST'
    }

    @Test
    void unapproveUsesGitUrlWhenNoProjectHttpUrl() {
        binding.setVariable('env', [
            CHANGE_ID: '9',
            GIT_URL: 'git@gitlab.company.com:team/repo.git'
        ])

        script.call([projectId: '456', approve: false])

        assert lastHttpRequestArgs.url == 'https://gitlab.company.com/api/v4/projects/456/merge_requests/9/unapprove'
        assert lastHttpRequestArgs.httpMode == 'POST'
        assert lastHttpRequestArgs.validResponseCodes == '100:399,404'
    }

    @Test
    void fallsBackToDefaultGitlabUrlWhenEnvMissing() {
        binding.setVariable('env', [:])

        script.call([projectId: '42', mrIid: '1'])

        assert lastHttpRequestArgs.url == 'https://gitlab.asatnet.net/api/v4/projects/42/merge_requests/1/approve'
    }
}
