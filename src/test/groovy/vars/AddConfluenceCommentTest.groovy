package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class AddConfluenceCommentTest extends BasePipelineTest {

    def script
    def lastHttpRequestArgs
    def httpResponseContent

    @Before
    void setUp() {
        super.setUp()
        httpResponseContent = '{"id":"555","type":"comment","body":{"storage":{"value":"<p>Hello</p>"}}}'

        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return [status: 200, content: httpResponseContent]
        })

        script = loadScript('vars/addConfluenceComment.groovy')
    }

    @Test
    void createsCommentAndReturnsCompactResultByDefault() {
        def result = script.call([
            PAGE_ID: '123',
            COMMENT: '<p>Hello</p>'
        ])

        assert result.id == '555'
        assert result.type == 'comment'
        assert result.body == '<p>Hello</p>'
        assert lastHttpRequestArgs.url == 'https://confluence.spacebridge.com/rest/api/content'
        assert lastHttpRequestArgs.httpMode == 'POST'

        def payload = new JsonSlurper().parseText(lastHttpRequestArgs.requestBody as String)
        assert payload.type == 'comment'
        assert payload.container.type == 'page'
        assert payload.container.id == '123'
        assert payload.body.storage.value == '<p>Hello</p>'
        assert payload.body.storage.representation == 'storage'
    }

    @Test
    void returnsRawJsonWhenRawEnabled() {
        def result = script.call([
            PAGE_ID: '123',
            COMMENT: '<p>Hello</p>',
            RAW: true
        ])

        assert result.id == '555'
        assert result.type == 'comment'
        assert result.body.storage.value == '<p>Hello</p>'
    }

    @Test
    void failsWhenPageIdMissing() {
        try {
            script.call([COMMENT: '<p>Hello</p>'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains('You must supply PAGE_ID')
        }
    }

    @Test
    void failsWhenCommentMissing() {
        try {
            script.call([PAGE_ID: '123'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains('You must supply COMMENT')
        }
    }

    @Test
    void failsWithContextWhenHttpRequestThrows() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            throw new RuntimeException('HTTP 401 Unauthorized')
        })

        try {
            script.call([PAGE_ID: '123', COMMENT: '<p>Hello</p>'])
            assert false, 'Should have thrown an error'
        } catch (RuntimeException e) {
            assert e.message.contains("addConfluenceComment(): Failed to add comment to page '123'")
            assert e.message.contains('HTTP 401 Unauthorized')
        }
    }
}
