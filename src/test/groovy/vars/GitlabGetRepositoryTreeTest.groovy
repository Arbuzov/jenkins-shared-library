package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test

class GitlabGetRepositoryTreeTest extends BasePipelineTest {

    private static Map httpResponse(String content, int status = 200) {
        // A plain Map, deliberately: it has no getContent(), so this pins the house
        // contract that the step reads the body as response?.content.
        return [status: status, content: content]
    }

    def script
    def requestedUrls
    def responses
    def echoed

    private static String entriesJson(int count, String prefix) {
        return JsonOutput.toJson((1..count).collect { index ->
            [id: "sha${prefix}${index}", name: "${prefix}${index}.txt",
             type: 'blob', path: "${prefix}${index}.txt", mode: '100644']
        })
    }

    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('env', [:])
        requestedUrls = []
        echoed = []
        responses = [httpResponse('[]')]

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoed << message })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { creds, body ->
            binding.setVariable('gitLabToken', 'fake-token')
            body()
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requestedUrls << args.url
            assert args.customHeaders[0].name == 'PRIVATE-TOKEN'
            assert args.customHeaders[0].value == 'fake-token'
            assert args.httpMode == 'GET'
            assert args.validResponseCodes == '100:599'
            return responses.size() > 1 ? responses.remove(0) : responses[0]
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            return new JsonSlurperClassic().parseText(args.text as String)
        })

        script = loadScript('vars/gitlabGetRepositoryTree.groovy')
    }

    @Test
    void listsTheRootOfTheProjectDerivedFromGitUrl() {
        responses = [httpResponse(
            '[{"id":"a1","name":"vars","type":"tree","path":"vars","mode":"040000"}]')]

        def tree = script.call([gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git'])

        assert tree.size() == 1
        assert tree[0].path == 'vars'
        assert tree[0].type == 'tree'
        assert requestedUrls == ['https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree?per_page=100&recursive=false&page=1']
    }

    @Test
    void encodesPathAndRefAndRequestsRecursively() {
        script.call([
            gitUrl: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git',
            path: 'deploy/charts',
            ref: 'feature/a b',
            recursive: true,
        ])

        assert requestedUrls[0] == 'https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree' +
            '?per_page=100&recursive=true&path=deploy%2Fcharts&ref=feature%2Fa+b&page=1'
    }

    @Test
    void usesProjectIdWithAnExplicitBaseUrl() {
        script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com'])

        assert requestedUrls[0] ==
            'https://gitlab.example.com/api/v4/projects/412/repository/tree' +
            '?per_page=100&recursive=false&page=1'
    }

    @Test
    void encodesAProjectPathPassedAsProjectId() {
        script.call([projectId: 'devops/sub/jenkins-shared-library',
                     gitlabBaseUrl: 'https://gitlab.example.com'])

        assert requestedUrls[0] == 'https://gitlab.example.com/api/v4/projects/' +
            'devops%2Fsub%2Fjenkins-shared-library/repository/tree' +
            '?per_page=100&recursive=false&page=1'
    }

    @Test
    void passesAnAlreadyEncodedProjectPathThrough() {
        script.call([projectId: 'devops%2Fjenkins-shared-library',
                     gitlabBaseUrl: 'https://gitlab.example.com'])

        assert requestedUrls[0].startsWith('https://gitlab.example.com/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void derivesTheProjectFromAnHttpsGitUrl() {
        script.call([gitUrl: 'https://gitlab.asatnet.net/devops/jenkins-shared-library.git'])

        assert requestedUrls[0].startsWith('https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void dropsTheSshPortOfAnSshSchemeUrl() {
        script.call([gitUrl: 'ssh://git@gitlab.example.com:2222/devops/jenkins-shared-library.git'])

        assert !requestedUrls[0].contains(':2222')
        assert requestedUrls[0].startsWith('https://gitlab.example.com/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void keepsThePortOfAnHttpCloneUrl() {
        script.call([gitUrl: 'http://gitlab.example.com:8080/devops/jenkins-shared-library.git'])

        assert requestedUrls[0].startsWith('http://gitlab.example.com:8080/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void keepsTheUserInfoOfTheGitUrlOutOfTheRequestUrl() {
        script.call([gitUrl: 'https://oauth2:s3cr3t@gitlab.example.com/devops/jenkins-shared-library.git'])

        assert !requestedUrls[0].contains('s3cr3t')
        assert requestedUrls[0].startsWith('https://gitlab.example.com/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void fallsBackToTheGitUrlOfTheEnvironment() {
        binding.setVariable('env', [GIT_URL: 'git@gitlab.asatnet.net:devops/jenkins-shared-library.git'])

        script.call([:])

        assert requestedUrls[0].startsWith('https://gitlab.asatnet.net/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void walksEveryPageUntilAShortOneArrives() {
        responses = [
            httpResponse(entriesJson(2, 'a')),
            httpResponse(entriesJson(1, 'b')),
        ]

        def tree = script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com', pageSize: 2])

        assert tree.size() == 3
        assert tree*.path == ['a1.txt', 'a2.txt', 'b1.txt']
        assert requestedUrls.size() == 2
        assert requestedUrls[0].endsWith('per_page=2&recursive=false&page=1')
        assert requestedUrls[1].endsWith('per_page=2&recursive=false&page=2')
    }

    @Test
    void stopsOnAnEmptyPageWithoutAnExtraRequest() {
        responses = [
            httpResponse(entriesJson(2, 'a')),
            httpResponse('[]'),
        ]

        def tree = script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com', pageSize: 2])

        assert tree.size() == 2
        assert requestedUrls.size() == 2
    }

    @Test
    void returnsAnEmptyListForAnEmptyTree() {
        def tree = script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com'])

        assert tree == []
        assert requestedUrls.size() == 1
    }

    @Test
    void failsRatherThanTruncatingWhenThePageWalkLimitIsExceeded() {
        responses = [httpResponse(entriesJson(2, 'a'))]

        try {
            script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com',
                         pageSize: 2, maxPages: 3])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('more than 6 entries')
        }
        assert requestedUrls.size() == 4
    }

    @Test
    void returnsATreeOfExactlyTheEntryLimit() {
        responses = [
            httpResponse(entriesJson(2, 'a')),
            httpResponse(entriesJson(2, 'b')),
            httpResponse(entriesJson(2, 'c')),
            httpResponse('[]'),
        ]

        def tree = script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com',
                                pageSize: 2, maxPages: 3])

        assert tree.size() == 6
        assert requestedUrls.size() == 4
    }

    @Test
    void failsOnTheFirstEntryBeyondTheLimit() {
        responses = [
            httpResponse(entriesJson(2, 'a')),
            httpResponse(entriesJson(2, 'b')),
            httpResponse(entriesJson(2, 'c')),
            httpResponse(entriesJson(1, 'd')),
        ]

        try {
            script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com',
                         pageSize: 2, maxPages: 3])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('more than 6 entries')
        }
    }

    @Test
    void failsOnAServerErrorRatherThanLettingTheHttpPluginThrow() {
        responses = [httpResponse('{"message":"500 Internal Server Error"}', 500)]

        try {
            script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('answered 500')
        }
    }

    @Test
    void keepsANonDefaultPortAndTheSchemeOfTheGitUrl() {
        script.call([gitUrl: 'https://gitlab.example.com:8443/devops/jenkins-shared-library.git'])

        assert requestedUrls[0].startsWith('https://gitlab.example.com:8443/api/v4/projects/' +
            'devops%2Fjenkins-shared-library/repository/tree')
    }

    @Test
    void failsOnAnErrorStatusFromTheApi() {
        responses = [httpResponse('{"message":"404 Tree Not Found"}', 404)]

        try {
            script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com', ref: 'nope'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('answered 404')
            assert exception.message.contains('gitlab-text-secret')
        }
    }

    @Test
    void failsWhenNeitherGitUrlNorProjectIdAvailable() {
        try {
            script.call([:])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("provide 'gitUrl' or 'projectId'")
        }
    }

    @Test
    void failsWhenProjectIdIsGivenWithoutABaseUrl() {
        try {
            script.call([projectId: 412])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'gitlabBaseUrl' is required")
        }
    }

    @Test
    void failsOnAnOutOfRangePageSize() {
        try {
            script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com', pageSize: 101])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'pageSize' must be between 1 and 100")
        }
    }

    @Test
    void failsOnANonPositiveMaxPages() {
        try {
            script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com', maxPages: 0])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'maxPages' must be >= 1")
        }
    }

    @Test
    void logsTheRequestUrlOnlyInDebug() {
        script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com'])
        assert echoed.isEmpty()

        script.call([projectId: 412, gitlabBaseUrl: 'https://gitlab.example.com', debug: true])
        assert echoed.size() == 1
        assert echoed[0].startsWith('[gitlabGetRepositoryTree] GET https://gitlab.example.com')
    }
}
