package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.junit.Before
import org.junit.Test

class DockerGetImageTagsTest extends BasePipelineTest {

    static final String FIRST_URL = 'https://registry.nexus.asatnet.net/v2/spacebridge/nms-ng-backend/tags/list'
    static final String NEXT_URL = 'https://registry.nexus.asatnet.net/v2/spacebridge/nms-ng-backend/tags/list' +
        '?n=5&last=1.0.0-b103'

    private static Map httpResponse(String content, int status = 200, Map headers = [:]) {
        // A plain Map, deliberately: it has no getContent() / getHeaders(), so this pins
        // the house contract that the step reads response?.content and response?.headers.
        return [status: status, content: content, headers: headers]
    }

    private static String tagsJson(List tags) {
        return JsonOutput.toJson([name: 'spacebridge/nms-ng-backend', tags: tags])
    }

    def script
    List requests
    List echoed
    List responses

    @Before
    void setUp() {
        super.setUp()
        requests = []
        echoed = []
        responses = [httpResponse(tagsJson([]))]

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

        script = loadScript('vars/dockerGetImageTags.groovy')
    }

    // ---------- single page ----------

    @Test
    void returnsTheTagsOfASinglePageFromTheDefaultRegistry() {
        responses = [httpResponse(tagsJson(['1.0.0-b101', '1.0.0-b102', 'latest']))]

        def tags = script.call([image: 'spacebridge/nms-ng-backend'])

        assert tags == ['1.0.0-b101', '1.0.0-b102', 'latest']
        assert requests*.url == [FIRST_URL]
        assert requests[0].authentication == 'jira-username-password'
        assert requests[0].quiet == true
        assert requests[0].consoleLogResponseBody == false
    }

    @Test
    void usesTheGivenRegistryHostAndCredentials() {
        script.call([image: 'spacebridge/nms-ng-frontend',
                     registryHost: 'registry-dev.nexus.asatnet.net',
                     credentialsId: 'local-registry-access'])

        assert requests*.url == ['https://registry-dev.nexus.asatnet.net/v2/spacebridge/nms-ng-frontend/tags/list']
        assert requests[0].authentication == 'local-registry-access'
    }

    @Test
    void returnsAnEmptyListWhenTheRegistryAnswersNullTags() {
        responses = [httpResponse('{"name":"spacebridge/nms-ng-backend","tags":null}')]

        def tags = script.call([image: 'spacebridge/nms-ng-backend'])

        assert tags == []
        assert requests.size() == 1
    }

    @Test
    void returnsAnEmptyListWhenTheRegistryAnswersNoTags() {
        def tags = script.call([image: 'spacebridge/nms-ng-backend'])

        assert tags == []
        assert requests.size() == 1
    }

    // ---------- paging ----------

    @Test
    void followsALowerCaseLinkHeaderWithAnAbsoluteUrl() {
        responses = [
            httpResponse(tagsJson(['1.0.0-b101', '1.0.0-b102', '1.0.0-b103']), 200,
                [link: ['<' + NEXT_URL + '>; rel="next"']]),
            httpResponse(tagsJson(['1.0.0-b104', 'latest'])),
        ]

        def tags = script.call([image: 'spacebridge/nms-ng-backend'])

        assert tags == ['1.0.0-b101', '1.0.0-b102', '1.0.0-b103', '1.0.0-b104', 'latest']
        assert requests*.url == [FIRST_URL, NEXT_URL]
    }

    @Test
    void resolvesARelativeLinkAgainstTheRegistryHost() {
        responses = [
            httpResponse(tagsJson(['1.0.0']), 200,
                [Link: ['</v2/spacebridge/nms-ng-frontend/tags/list?n=1&last=1.0.0>; rel="next"']]),
            httpResponse(tagsJson(['1.0.1'])),
        ]

        def tags = script.call([image: 'spacebridge/nms-ng-frontend', registryHost: 'registry-dev.nexus.asatnet.net'])

        assert tags == ['1.0.0', '1.0.1']
        assert requests*.url == [
            'https://registry-dev.nexus.asatnet.net/v2/spacebridge/nms-ng-frontend/tags/list',
            'https://registry-dev.nexus.asatnet.net/v2/spacebridge/nms-ng-frontend/tags/list?n=1&last=1.0.0',
        ]
    }

    @Test
    void readsALinkHeaderGivenAsAString() {
        responses = [
            httpResponse(tagsJson(['1.0.0-b101']), 200, [Link: '<' + NEXT_URL + '>; rel="next"']),
            httpResponse(tagsJson(['1.0.0-b104'])),
        ]

        def tags = script.call([image: 'spacebridge/nms-ng-backend'])

        assert tags == ['1.0.0-b101', '1.0.0-b104']
        assert requests*.url == [FIRST_URL, NEXT_URL]
    }

    @Test
    void failsRatherThanTruncatingWhenMorePagesThanMaxPagesAreAnnounced() {
        responses = [httpResponse(tagsJson(['1.0.0-b101']), 200, [link: ['<' + NEXT_URL + '>; rel="next"']])]

        try {
            script.call([image: 'spacebridge/nms-ng-backend', maxPages: 2])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains('more than 2 pages')
            assert exception.message.contains(FIRST_URL)
            assert exception.message.contains("'maxPages'")
        }
        assert requests.size() == 2
    }

    @Test
    void returnsExactlyMaxPagesWhenNoFurtherPageIsAnnounced() {
        responses = [
            httpResponse(tagsJson(['a']), 200, [link: ['<' + NEXT_URL + '>; rel="next"']]),
            httpResponse(tagsJson(['b'])),
        ]

        def tags = script.call([image: 'spacebridge/nms-ng-backend', maxPages: 2])

        assert tags == ['a', 'b']
        assert requests.size() == 2
    }

    // ---------- errors ----------

    @Test
    void failsOnAMissingImageAnswer() {
        responses = [httpResponse(
            '{"errors":[{"code":"NAME_UNKNOWN","message":"repository name not known to registry"}]}', 404)]

        try {
            script.call([image: 'spacebridge/no-such-image'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains(
                'GET https://registry.nexus.asatnet.net/v2/spacebridge/no-such-image/tags/list answered 404')
            assert exception.message.contains("'spacebridge/no-such-image'")
            assert exception.message.contains('registry.nexus.asatnet.net')
            assert exception.message.contains("'jira-username-password'")
        }
    }

    @Test
    void failsWhenImageIsMissing() {
        try {
            script.call([:])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'image' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsWhenImageIsEmpty() {
        try {
            script.call([image: ''])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'image' is required")
        }
        assert requests.isEmpty()
    }

    @Test
    void failsOnANonPositiveMaxPages() {
        try {
            script.call([image: 'spacebridge/nms-ng-backend', maxPages: 0])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException exception) {
            assert exception.message.contains("'maxPages' must be >= 1")
        }
        assert requests.isEmpty()
    }

    // ---------- debug ----------

    @Test
    void logsTheRequestUrlOnlyInDebug() {
        script.call([image: 'spacebridge/nms-ng-backend'])
        assert echoed.isEmpty()

        script.call([image: 'spacebridge/nms-ng-backend', debug: true])
        assert echoed == ['[dockerGetImageTags] GET ' + FIRST_URL]
        assert requests[1].quiet == false
        assert requests[1].consoleLogResponseBody == true
    }

    // ---------- nextPageLink ----------

    @Test
    void nextPageLinkIsNullWithoutHeaders() {
        assert script.nextPageLink(null) == null
        assert script.nextPageLink([:]) == null
    }

    @Test
    void nextPageLinkIsNullWithoutALinkHeader() {
        assert script.nextPageLink(['Content-Type': ['application/json']]) == null
    }

    @Test
    void nextPageLinkIsNullForARelOtherThanNext() {
        assert script.nextPageLink([Link: ['</v2/a/tags/list?last=x>; rel="prev"']]) == null
    }

    @Test
    void nextPageLinkMatchesTheHeaderNameInAnyCase() {
        assert script.nextPageLink([LINK: ['</v2/a/tags/list?last=x>; rel="next"']]) == '/v2/a/tags/list?last=x'
    }

    @Test
    void nextPageLinkPicksTheNextEntryAmongSeveral() {
        assert script.nextPageLink([link: ['</v2/a/tags/list?last=p>; rel="prev"', '</v2/a/tags/list?last=n>; rel=next']]) ==
            '/v2/a/tags/list?last=n'
    }

    @Test
    void nextPageLinkFindsRelAfterOtherParameters() {
        assert script.nextPageLink([Link: '</v2/a/tags/list?n=5&last=x>; type="application/json"; rel="next"']) ==
            '/v2/a/tags/list?n=5&last=x'
    }

    @Test
    void nextPageLinkFindsNextAmongSeveralRelations() {
        assert script.nextPageLink([Link: '</v2/a/tags/list?last=x>; REL="prev next"']) == '/v2/a/tags/list?last=x'
        assert script.nextPageLink([Link: '</v2/a/tags/list?last=x>; rel="nextpage"']) == null
    }
}
