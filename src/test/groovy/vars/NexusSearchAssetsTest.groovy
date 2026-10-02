package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test

class NexusSearchAssetsTest extends BasePipelineTest {

    private static final String SEARCH_URL = 'https://nexus.asatnet.net/service/rest/v1/search/assets'

    private static Map httpResponse(String content, int status = 200) {
        // A plain Map, deliberately: it has no getContent(), so this pins the house
        // contract that the step reads the body as response?.content.
        return [status: status, content: content]
    }

    private static Map asset(String version) {
        return [
            path: "com/spacebridge/application-plugin/nms-ng/${version}/nms-ng-${version}.tar.gz".toString(),
            downloadUrl: "https://nexus.asatnet.net/repository/maven-releases/nms-ng-${version}.tar.gz".toString(),
            repository: 'maven-releases',
            format: 'maven2',
            maven2: [groupId: 'com.spacebridge.application-plugin', artifactId: 'nms-ng',
                     version: version, extension: 'tar.gz'],
        ]
    }

    private static Map page(List<String> versions, String continuationToken) {
        return httpResponse(JsonOutput.toJson([
            items: versions.collect { version -> asset(version) },
            continuationToken: continuationToken,
        ]))
    }

    def script
    def requests
    def responses
    def echoed

    @Before
    void setUp() {
        super.setUp()
        binding.setVariable('env', [:])
        requests = []
        echoed = []
        responses = [page([], null)]

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoed << message })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            requests << args
            assert args.httpMode == 'GET'
            assert args.validResponseCodes == '100:599'
            return responses.size() > 1 ? responses.remove(0) : responses[0]
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            assert args.returnPojo == true
            return new JsonSlurperClassic().parseText(args.text as String)
        })

        script = loadScript('vars/nexusSearchAssets.groovy')
    }

    private List<String> requestedUrls() {
        return requests.collect { request -> request.url as String }
    }

    // ---------- query ----------

    @Test
    void keepsTheDottedKeysAndEncodesTheValuesInTheOrderOfTheQuery() {
        script.call([query: [
            repository: 'maven-releases',
            'maven.groupId': 'com.spacebridge.application-plugin',
            'maven.baseVersion': '1.2.0-SNAPSHOT',
            q: 'nms ng/2.0+x',
        ]])

        assert requestedUrls() == [SEARCH_URL + '?repository=maven-releases' +
            '&maven.groupId=com.spacebridge.application-plugin' +
            '&maven.baseVersion=1.2.0-SNAPSHOT&q=nms+ng%2F2.0%2Bx']
    }

    @Test
    void encodesTheKeysToo() {
        script.call([query: ['a key&b': 'value']])

        assert requestedUrls() == [SEARCH_URL + '?a+key%26b=value']
    }

    @Test
    void usesTheGivenNexusUrl() {
        script.call([nexusUrl: 'https://nexus.example.com', query: [repository: 'maven-releases']])

        assert requestedUrls() == ['https://nexus.example.com/service/rest/v1/search/assets?repository=maven-releases']
    }

    // ---------- pages ----------

    @Test
    void returnsTheItemsOfASinglePage() {
        responses = [page(['2.0.3-b69', '2.0.3-b70'], null)]

        def assets = script.call([query: [repository: 'maven-releases', 'maven.artifactId': 'nms-ng']])

        assert assets*.maven2*.version == ['2.0.3-b69', '2.0.3-b70']
        assert assets[0] == asset('2.0.3-b69')
        assert requestedUrls() == [SEARCH_URL + '?repository=maven-releases&maven.artifactId=nms-ng']
    }

    @Test
    void joinsThreePagesInOrderWithTheTokenUrlEncoded() {
        responses = [
            page(['1.0.0-b1', '1.0.0-b2'], 'token+1/='),
            page(['1.0.0-b3'], 'token 2'),
            page(['1.0.0-b4'], null),
        ]

        def assets = script.call([query: [repository: 'maven-releases']])

        assert assets*.maven2*.version == ['1.0.0-b1', '1.0.0-b2', '1.0.0-b3', '1.0.0-b4']
        assert requestedUrls() == [
            SEARCH_URL + '?repository=maven-releases',
            SEARCH_URL + '?repository=maven-releases&continuationToken=token%2B1%2F%3D',
            SEARCH_URL + '?repository=maven-releases&continuationToken=token+2',
        ]
    }

    @Test
    void stopsTheWalkOnANullToken() {
        responses = [
            httpResponse('{"items":[' + JsonOutput.toJson(asset('1.0.0-b1')) + '],"continuationToken":null}'),
            page(['never-read'], null),
        ]

        def assets = script.call([query: [repository: 'maven-releases']])

        assert assets*.maven2*.version == ['1.0.0-b1']
        assert requests.size() == 1
    }

    @Test
    void stopsTheWalkWhenTheBodyHasNoToken() {
        responses = [httpResponse('{"items":[' + JsonOutput.toJson(asset('1.0.0-b1')) + ']}')]

        def assets = script.call([query: [repository: 'maven-releases']])

        assert assets.size() == 1
        assert requests.size() == 1
    }

    @Test
    void returnsAnEmptyListWhenNothingMatches() {
        responses = [httpResponse('{"items":[],"continuationToken":null}')]

        def assets = script.call([query: [repository: 'maven-releases', 'maven.artifactId': 'nope']])

        assert assets == []
        assert requests.size() == 1
    }

    @Test
    void returnsASearchOfExactlyMaxPagesPages() {
        responses = [
            page(['1.0.0-b1'], 't1'),
            page(['1.0.0-b2'], 't2'),
            page(['1.0.0-b3'], null),
        ]

        def assets = script.call([query: [repository: 'maven-releases'], maxPages: 3])

        assert assets*.maven2*.version == ['1.0.0-b1', '1.0.0-b2', '1.0.0-b3']
        assert requests.size() == 3
    }

    @Test
    void failsRatherThanTruncatingWhenATokenRemainsAfterMaxPages() {
        responses = [
            page(['1.0.0-b1'], 't1'),
            page(['1.0.0-b2'], 't2'),
            page(['1.0.0-b3'], 't3'),
        ]

        try {
            script.call([query: [repository: 'maven-releases'], maxPages: 3])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains(
                "more than 3 pages for ${SEARCH_URL}?repository=maven-releases. " +
                "Narrow the query, or raise 'maxPages'.")
        }
        assert requests.size() == 3
    }

    // ---------- request ----------

    @Test
    void authenticatesWithTheDefaultCredentials() {
        script.call([query: [repository: 'maven-releases']])

        assert requests[0].authentication == 'jira-username-password'
        assert requests[0].quiet == true
        assert requests[0].consoleLogResponseBody == false
    }

    @Test
    void authenticatesWithTheGivenCredentials() {
        script.call([query: [repository: 'maven-releases'], credentialsId: 'nexus-reader'])

        assert requests[0].authentication == 'nexus-reader'
    }

    @Test
    void logsTheRequestUrlOnlyInDebug() {
        script.call([query: [repository: 'maven-releases']])
        assert echoed.isEmpty()

        script.call([query: [repository: 'maven-releases'], debug: true])
        assert echoed == ["[nexusSearchAssets] GET ${SEARCH_URL}?repository=maven-releases".toString()]
        assert requests[1].quiet == false
        assert requests[1].consoleLogResponseBody == true
    }

    // ---------- errors ----------

    @Test
    void failsOnAnErrorStatusNamingTheUrlAndTheCredentials() {
        responses = [httpResponse('', 401)]

        try {
            script.call([query: [repository: 'maven-releases']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("GET ${SEARCH_URL}?repository=maven-releases answered 401")
            assert exception.message.contains("'jira-username-password'")
        }
    }

    @Test
    void failsOnAnErrorStatusOfALaterPage() {
        responses = [
            page(['1.0.0-b1'], 't1'),
            httpResponse('{"message":"Server Error"}', 500),
        ]

        try {
            script.call([query: [repository: 'maven-releases']])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('continuationToken=t1 answered 500')
        }
    }

    @Test
    void failsWithoutAQuery() {
        try {
            script.call([:])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("nexusSearchAssets(): 'query' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnAnEmptyQuery() {
        try {
            script.call([query: [:]])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'query' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnAQueryThatIsNotAMap() {
        try {
            script.call([query: 'repository=maven-releases'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'query' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnAQueryValueThatIsNull() {
        try {
            script.call([query: [repository: 'maven-releases', 'maven.artifactId': null]])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message == "nexusSearchAssets(): 'query' has no value for maven.artifactId."
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnANonPositiveMaxPages() {
        try {
            script.call([query: [repository: 'maven-releases'], maxPages: 0])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'maxPages' must be >= 1")
        }
        assert requests.isEmpty()
    }
}
