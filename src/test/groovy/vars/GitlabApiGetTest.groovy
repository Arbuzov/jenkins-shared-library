package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test

class GitlabApiGetTest extends BasePipelineTest {

    private static final String BASE = 'https://gitlab.example.com'
    private static final String PROJECT_PREFIX = 'https://gitlab.example.com/api/v4/projects/' +
        'devops%2Fjenkins-shared-library/'

    private static Map httpResponse(String content, int status = 200) {
        // A plain Map, deliberately: it has no getContent(), so this pins the house
        // contract that the step reads the body as response?.content.
        return [status: status, content: content]
    }

    def script
    def requestedUrls
    def requests
    def credentialIds
    def echoed
    def token
    def response

    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('env', [:])
        requestedUrls = []
        requests = []
        credentialIds = []
        echoed = []
        token = 'fake-token'
        response = httpResponse('{"name":"master"}')

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoed << message })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('string', [Map], { Map args ->
            credentialIds << args.credentialsId
            assert args.variable == 'gitLabToken'
            return args
        })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { creds, body ->
            binding.setVariable('gitLabToken', token)
            body()
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requestedUrls << args.url
            requests << args
            assert args.customHeaders[0].name == 'PRIVATE-TOKEN'
            assert args.customHeaders[0].value == 'fake-token'
            assert args.httpMode == 'GET'
            assert args.validResponseCodes == '100:599'
            return response
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            assert args.returnPojo == true
            return new JsonSlurperClassic().parseText(args.text as String)
        })

        script = loadScript('vars/gitlabApiGet.groovy')
    }

    // ---------- project derivation ----------

    @Test
    void getsTheResourceOfTheProjectDerivedFromAnScpGitUrl() {
        def branch = script.call([
            path: 'repository/branches/master',
            gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
        ])

        assert branch == [name: 'master']
        assert requestedUrls == ['https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/branches/master']
    }

    @Test
    void derivesTheProjectFromAnHttpsGitUrl() {
        script.call([path: 'repository/tags',
                     gitUrl: 'https://gitlab.asatnet.net/devops/jenkins-shared-library.git'])

        assert requestedUrls[0] == 'https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tags'
    }

    @Test
    void dropsTheSshPortOfAnSshSchemeUrl() {
        script.call([path: 'repository/tags',
                     gitUrl: 'ssh://git@gitlab.example.com:2222/devops/jenkins-shared-library.git'])

        assert !requestedUrls[0].contains(':2222')
        assert requestedUrls[0] == PROJECT_PREFIX + 'repository/tags'
    }

    @Test
    void keepsThePortOfAnHttpCloneUrl() {
        script.call([path: 'repository/tags',
                     gitUrl: 'http://gitlab.example.com:8080/devops/jenkins-shared-library.git'])

        assert requestedUrls[0] == 'http://gitlab.example.com:8080/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tags'
    }

    @Test
    void keepsANonDefaultPortAndTheSchemeOfTheGitUrl() {
        script.call([path: 'repository/tags',
                     gitUrl: 'https://gitlab.example.com:8443/devops/jenkins-shared-library.git'])

        assert requestedUrls[0] == 'https://gitlab.example.com:8443/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tags'
    }

    @Test
    void keepsTheUserInfoOfTheGitUrlOutOfTheRequestUrl() {
        script.call([path: 'repository/tags',
                     gitUrl: 'https://oauth2:s3cr3t@gitlab.example.com/devops/jenkins-shared-library.git'])

        assert !requestedUrls[0].contains('s3cr3t')
        assert !requestedUrls[0].contains('oauth2')
        assert requestedUrls[0] == PROJECT_PREFIX + 'repository/tags'
    }

    @Test
    void fallsBackToTheGitUrlOfTheEnvironment() {
        binding.setVariable('env', [GIT_URL: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git'])

        script.call([path: 'repository/tags'])

        assert requestedUrls[0] == 'https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tags'
    }

    @Test
    void usesANumericProjectIdWithAnExplicitBaseUrl() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE])

        assert requestedUrls[0] == 'https://gitlab.example.com/api/v4/projects/412/repository/tags'
    }

    @Test
    void encodesAProjectPathPassedAsProjectId() {
        script.call([path: 'repository/tags', projectId: 'devops/sub/jenkins-shared-library',
                     gitlabBaseUrl: BASE])

        assert requestedUrls[0] == 'https://gitlab.example.com/api/v4/projects/' +
            'devops%2Fsub%2Fjenkins-shared-library/repository/tags'
    }

    @Test
    void passesAnAlreadyEncodedProjectPathThrough() {
        script.call([path: 'repository/tags', projectId: 'devops%2Fjenkins-shared-library',
                     gitlabBaseUrl: BASE])

        assert requestedUrls[0] == PROJECT_PREFIX + 'repository/tags'
    }

    @Test
    void prefersAnExplicitBaseUrlOverTheHostOfTheGitUrl() {
        script.call([path: 'repository/tags',
                     gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
                     gitlabBaseUrl: 'https://gitlab-mirror.example.com'])

        assert requestedUrls[0] == 'https://gitlab-mirror.example.com/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tags'
    }

    @Test
    void prefersAProjectIdOverTheProjectOfTheGitUrl() {
        script.call([path: 'repository/tags', projectId: 412,
                     gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git'])

        assert requestedUrls[0] == 'https://gitlab.asatnet.net/api/v4/projects/412/repository/tags'
    }

    // ---------- path and query ----------

    @Test
    void passesTheCallerEncodedPathThroughUnchanged() {
        script.call([path: 'repository/files/deploy%2Fmy%20file.yml', projectId: 412, gitlabBaseUrl: BASE])

        assert requestedUrls[0] ==
            'https://gitlab.example.com/api/v4/projects/412/repository/files/deploy%2Fmy%20file.yml'
    }

    @Test
    void encodesTheQueryInTheOrderOfTheMap() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE,
                     query: [ref: 'release/v.1.1.0', all: true, sort: 'desc']])

        assert requestedUrls[0] == 'https://gitlab.example.com/api/v4/projects/412/repository/tags' +
            '?ref=release%2Fv.1.1.0&all=true&sort=desc'
    }

    @Test
    void encodesTheKeysOfTheQueryToo() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE,
                     query: ['a b': 'c&d', per_page: 100]])

        assert requestedUrls[0].endsWith('/repository/tags?a+b=c%26d&per_page=100')
    }

    @Test
    void addsNoQuestionMarkWithoutAQuery() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE])
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE, query: [:]])

        assert requestedUrls.size() == 2
        requestedUrls.each { url -> assert !url.contains('?') }
    }

    @Test
    void queryStringJoinsEncodedPairsWithAmpersands() {
        assert script.queryString([ref: 'feature/a b', page: 2]) == 'ref=feature%2Fa+b&page=2'
        assert script.queryString([:]) == ''
    }

    // ---------- request ----------

    @Test
    void sendsOneGetWithTheTokenOfTheDefaultCredentials() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE])

        assert requests.size() == 1
        assert credentialIds == ['gitlab-text-secret']
        assert requests[0].customHeaders == [[name: 'PRIVATE-TOKEN', value: 'fake-token']]
        assert requests[0].quiet == true
        assert requests[0].consoleLogResponseBody == false
    }

    @Test
    void usesTheGivenCredentials() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE,
                     credentialsId: 'other-gitlab-token'])

        assert credentialIds == ['other-gitlab-token']
    }

    @Test
    void logsTheRequestUrlOnlyInDebug() {
        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE])
        assert echoed.isEmpty()

        script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE, debug: true])
        assert echoed == ['[gitlabApiGet] GET https://gitlab.example.com/api/v4/projects/412/repository/tags']
        assert requests[1].quiet == false
        assert requests[1].consoleLogResponseBody == true
    }

    // ---------- response ----------

    @Test
    void returnsAMapBody() {
        response = httpResponse('{"file_name":"Manifest","commit_id":"abc123"}')

        def file = script.call([path: 'repository/files/Manifest', projectId: 412, gitlabBaseUrl: BASE])

        assert file instanceof Map
        assert file == [file_name: 'Manifest', commit_id: 'abc123']
    }

    @Test
    void returnsAListBodyAsItsFirstPageOnly() {
        response = httpResponse('[{"name":"v1.1.0"},{"name":"v1.0.0"}]')

        def tags = script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE])

        assert tags instanceof List
        assert tags*.name == ['v1.1.0', 'v1.0.0']
        assert requests.size() == 1
    }

    // ---------- errors ----------

    @Test
    void failsWithoutAPath() {
        try {
            script.call([projectId: 412, gitlabBaseUrl: BASE])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("gitlabApiGet(): 'path' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnAQueryValueThatIsNull() {
        try {
            script.call([projectId: 412, gitlabBaseUrl: BASE, path: 'repository/files/Manifest', query: [ref: null]])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message == "gitlabApiGet(): 'query' has no value for ref."
        }
        assert requests.isEmpty()
    }

    @Test
    void failsWhenNeitherGitUrlNorProjectIdAvailable() {
        try {
            script.call([path: 'repository/tags'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("provide 'gitUrl' or 'projectId' (env.GIT_URL is unset)")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsWhenProjectIdIsGivenWithoutABaseUrl() {
        try {
            script.call([path: 'repository/tags', projectId: 412])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'gitlabBaseUrl' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsWhenTheCredentialsHoldNoToken() {
        token = null

        try {
            script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE,
                         credentialsId: 'empty-token'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("credentials 'empty-token' is undefined")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnAnErrorStatusNamingTheUrlAndTheCredentials() {
        response = httpResponse('{"message":"404 File Not Found"}', 404)

        try {
            script.call([path: 'repository/files/Manifest', projectId: 412, gitlabBaseUrl: BASE,
                         query: [ref: 'nope']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('gitlabApiGet(): GET ' +
                'https://gitlab.example.com/api/v4/projects/412/repository/files/Manifest?ref=nope ' +
                "answered 404. Check the project, the path and the token of credentials 'gitlab-text-secret'.")
        }
    }

    @Test
    void failsOnAServerErrorRatherThanLettingTheHttpPluginThrow() {
        response = httpResponse('{"message":"500 Internal Server Error"}', 500)

        try {
            script.call([path: 'repository/tags', projectId: 412, gitlabBaseUrl: BASE])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('answered 500')
        }
    }
}
