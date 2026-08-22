package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class TagToGitlabTest extends BasePipelineTest {

    static class HttpResponseMock {
        int status = 200
        String content
        int getStatus() { return status }
        String getContent() { return content }
    }

    def script
    List<Map> requests

    @Before
    void setUp() {
        super.setUp()
        requests = []

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { creds, body ->
            binding.setVariable('gitLabAPISecret', 'fake-token')
            body()
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            if (args.httpMode == 'GET' && args.url.contains('/repository/tags')) {
                return new HttpResponseMock(status: 200, content: '[{"name":"rolling"}]')
            }
            return new HttpResponseMock(status: 200, content: '{}')
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            return new groovy.json.JsonSlurperClassic().parseText(args.text as String)
        })

        script = loadScript('vars/tagToGitlab.groovy')
    }

    @Test
    void treatsAnAnnotatedTagAlreadyOnTheRefAsSuccess() {
        // GitLab reports an annotated tag's target as the tag object, not the commit,
        // so comparing target to the ref never matches and a correct tag looked failed
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            if (args.httpMode == 'GET' && args.url.endsWith('/repository/tags')) {
                return new HttpResponseMock(status: 200, content: '[{"name":"rolling"}]')
            }
            if (args.httpMode == 'GET' && args.url.contains('/repository/tags/rolling')) {
                return new HttpResponseMock(status: 200, content:
                    '{"name":"rolling","target":"1eca9e06f4b8c6102c48bd14d472c4a837eb64f9",' +
                    '"commit":{"id":"f6b6ff1ad5f3a706c1c84d8849db7904bd57dbc1"}}')
            }
            if (args.httpMode == 'POST') {
                return new HttpResponseMock(status: 400, content: '{"message":"Tag rolling already exists"}')
            }
            return new HttpResponseMock(status: 200, content: '{}')
        })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })

        script.call('git@gitlab.example.net:devops/jenkins-shared-library.git', 'rolling',
                    'f6b6ff1ad5f3a706c1c84d8849db7904bd57dbc1', 'rolling tag', 'deprecated', false)
    }

    @Test
    void failsWhenTheExistingTagPointsSomewhereElse() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            if (args.httpMode == 'GET' && args.url.endsWith('/repository/tags')) {
                return new HttpResponseMock(status: 200, content: '[{"name":"rolling"}]')
            }
            if (args.httpMode == 'GET' && args.url.contains('/repository/tags/rolling')) {
                return new HttpResponseMock(status: 200, content:
                    '{"name":"rolling","commit":{"id":"0000000000000000000000000000000000000000"}}')
            }
            if (args.httpMode == 'POST') {
                return new HttpResponseMock(status: 400, content: '{"message":"Tag rolling already exists"}')
            }
            return new HttpResponseMock(status: 200, content: '{}')
        })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })

        try {
            script.call('git@gitlab.example.net:devops/jenkins-shared-library.git', 'rolling',
                        'f6b6ff1ad5f3a706c1c84d8849db7904bd57dbc1', 'rolling tag', 'deprecated', false)
            assert false: 'expected an error'
        } catch (RuntimeException e) {
            assert e.message.contains("Failed to create GitLab tag 'rolling'")
        }
    }

    @Test
    void retriesWithoutReleaseDescriptionWhenGitlabReturns400() {
        int createCalls = 0
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            if (args.httpMode == 'GET' && args.url.contains('/repository/tags')) {
                return new HttpResponseMock(status: 200, content: '[{"name":"rolling"}]')
            }
            if (args.httpMode == 'POST' && args.url.contains('/repository/tags?tag_name=')) {
                createCalls++
                if (createCalls == 1) {
                    return new HttpResponseMock(status: 400, content: '{"message":"release already exists"}')
                }
                return new HttpResponseMock(status: 201, content: '{}')
            }
            return new HttpResponseMock(status: 200, content: '{}')
        })

        script.call(
            'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
            'rolling',
            'a5cd2d54ab2541194b21828c294704ab72b5c390',
            'rolling tag',
            'deprecated',
            false
        )

        assert requests.size() == 4

        def getTags = requests[0]
        assert getTags.httpMode == 'GET'
        assert getTags.url == 'https://gitlab.asatnet.net/api/v4/projects/devops%2Fjenkins-shared-library/repository/tags'
        assert getTags.customHeaders[0].name == 'PRIVATE-TOKEN'

        def deleteTag = requests[1]
        assert deleteTag.httpMode == 'DELETE'
        assert deleteTag.url.endsWith('/projects/devops%2Fjenkins-shared-library/repository/tags/rolling')

        def firstCreateTag = requests[2]
        assert firstCreateTag.httpMode == 'POST'
        assert firstCreateTag.url.contains('/repository/tags?tag_name=rolling')
        assert firstCreateTag.url.contains('&ref=a5cd2d54ab2541194b21828c294704ab72b5c390')
        assert firstCreateTag.url.contains('&message=rolling+tag')
        assert firstCreateTag.url.contains('&release_description=deprecated')

        def retriedCreateTag = requests[3]
        assert retriedCreateTag.httpMode == 'POST'
        assert retriedCreateTag.url.contains('/repository/tags?tag_name=rolling')
        assert retriedCreateTag.url.contains('&ref=a5cd2d54ab2541194b21828c294704ab72b5c390')
        assert retriedCreateTag.url.contains('&message=rolling+tag')
        assert !retriedCreateTag.url.contains('release_description=')
    }

    @Test
    void treatsTagAsCreatedWhenItAlreadyPointsToRequestedRef() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            if (args.httpMode == 'GET' && args.url.endsWith('/repository/tags')) {
                return new HttpResponseMock(status: 200, content: '[{"name":"rolling"}]')
            }
            if (args.httpMode == 'POST' && args.url.contains('/repository/tags?tag_name=rolling')) {
                return new HttpResponseMock(status: 400, content: '{"message":"Tag already exists"}')
            }
            if (args.httpMode == 'GET' && args.url.endsWith('/repository/tags/rolling')) {
                return new HttpResponseMock(status: 200, content: '{"name":"rolling","target":"a5cd2d54ab2541194b21828c294704ab72b5c390"}')
            }
            return new HttpResponseMock(status: 200, content: '{}')
        })

        script.call(
            'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
            'rolling',
            'a5cd2d54ab2541194b21828c294704ab72b5c390',
            'rolling tag',
            '',
            false
        )

        assert requests.size() == 4
        assert requests[2].httpMode == 'POST'
        assert requests[3].httpMode == 'GET'
        assert requests[3].url.endsWith('/repository/tags/rolling')
    }

    @Test
    void skipsMutationWhenTagExistsAndSkipIfExistTrue() {
        script.call(
            'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
            'rolling',
            'a5cd2d54ab2541194b21828c294704ab72b5c390'
        )

        assert requests.size() == 1
        assert requests[0].httpMode == 'GET'
    }
}
