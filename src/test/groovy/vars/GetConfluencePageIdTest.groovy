package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class GetConfluencePageIdTest extends BasePipelineTest {

    def script
    def httpCalls

    @Before
    void setUp() {
        super.setUp()
        httpCalls = []
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            httpCalls << args
            return [status: 200, content: JsonOutput.toJson([results: [[id: '718045806']]])]
        })
        script = loadScript('vars/getConfluencePageId.groovy')
    }

    @Test
    void returnsPageIdWhenNotLastParameter() {
        def result = script.call([url: 'https://confluence.example.com/pages/viewpage.action?pageId=12345&spaceKey=SPACE&foo=bar'])
        assert result == '12345'
    }

    @Test
    void decodesUrlEncodedPageIdValue() {
        def result = script.call([url: 'https://confluence.example.com/pages/viewpage.action?foo=bar&pageId=123%20456&baz=qux'])
        assert result == '123 456'
    }

    @Test
    void returnsNullWhenPageIdMissing() {
        def result = script.call([url: 'https://confluence.example.com/pages/viewpage.action?foo=bar'])
        assert result == null
    }

    @Test
    void returnsNullForInvalidUrl() {
        def result = script.call([url: 'not a valid url'])
        assert result == null
    }

    @Test
    void resolvesPageIdFromDisplayUrlWithQueryAndAnchor() {
        def result = script.call([
            url: 'https://confluence.spacebridge.com/display/DEVOPS/Copy+of+R-1.1+PRD+-+Code+Security?focusedCommentId=718045806#comment-'
        ])

        assert result == '718045806'
        assert httpCalls.size() == 1
        assert httpCalls[0].url == 'https://confluence.spacebridge.com/rest/api/content?spaceKey=DEVOPS&title=Copy+of+R-1.1+PRD+-+Code+Security'
    }

    @Test
    void resolvesPageIdFromDisplayUrlWithoutQuery() {
        def result = script.call([
            url: 'https://confluence.spacebridge.com/display/DEVOPS/Copy+of+R-1.1+PRD+-+Code+Security'
        ])

        assert result == '718045806'
        assert httpCalls.size() == 1
    }

    @Test
    void resolvesPageIdFromPathOnlyUsingProvidedApi() {
        def result = script.call([
            path: '/display/DEVOPS/Copy+of+R-1.1+PRD+-+Code+Security',
            CONFLUENCE_API: 'https://confluence.spacebridge.com/rest/api'
        ])

        assert result == '718045806'
        assert httpCalls.size() == 1
        assert httpCalls[0].url == 'https://confluence.spacebridge.com/rest/api/content?spaceKey=DEVOPS&title=Copy+of+R-1.1+PRD+-+Code+Security'
    }

    @Test
    void resolvesPageIdFromDisplayUrlWithWikiContext() {
        def result = script.call([
            url: 'https://confluence.example.com/wiki/display/DEVOPS/Copy+of+R-1.1+PRD+-+Code+Security'
        ])

        assert result == '718045806'
        assert httpCalls.size() == 1
        assert httpCalls[0].url == 'https://confluence.example.com/wiki/rest/api/content?spaceKey=DEVOPS&title=Copy+of+R-1.1+PRD+-+Code+Security'
    }

    @Test
    void supportsStringInputWithPathOnly() {
        def result = script.call('/display/DEVOPS/Copy+of+R-1.1+PRD+-+Code+Security')

        assert result == '718045806'
        assert httpCalls.size() == 1
        assert httpCalls[0].url == 'https://confluence.spacebridge.com/rest/api/content?spaceKey=DEVOPS&title=Copy+of+R-1.1+PRD+-+Code+Security'
    }
}
