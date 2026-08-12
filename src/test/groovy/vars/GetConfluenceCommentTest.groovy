package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class GetConfluenceCommentTest extends BasePipelineTest {

    def script
    def lastHttpRequestArgs
    def httpResponseContent

    @Before
    void setUp() {
        super.setUp()
        httpResponseContent = '{"id":"123","type":"comment","body":{"storage":{"value":"<p>Hello</p>"}}}'
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
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
        script = loadScript('vars/getConfluenceComment.groovy')
    }

    @Test
    void returnsCommentBodyByDefault() {
        def result = script.call([COMMENT_ID: '123'])

        assert result == '<p>Hello</p>'
        assert lastHttpRequestArgs.url == 'https://confluence.spacebridge.com/rest/api/content/123?expand=body.storage'
    }

    @Test
    void returnsRawJsonWhenRawIsTrue() {
        def result = script.call([COMMENT_ID: '123', raw: true])

        assert result.id == '123'
        assert result.type == 'comment'
        assert result.body.storage.value == '<p>Hello</p>'
    }

    @Test
    void returnsNullWhenBodyIsNull() {
        httpResponseContent = '{"id":"123","type":"comment","body":{"storage":{"value":null}}}'

        def result = script.call([COMMENT_ID: '123'])

        assert result == null
    }

    @Test
    void returnsNullWhenNotFoundAndAllowNotFoundEnabled() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return [status: 404, content: '']
        })

        def result = script.call([COMMENT_ID: '404', ALLOW_NOT_FOUND: true])

        assert result == null
        assert lastHttpRequestArgs.validResponseCodes == '100:404'
    }

    @Test
    void failsWhenCommentIdMissing() {
        try {
            script.call([:])
            assert false, "Should have thrown an error"
        } catch (RuntimeException e) {
            assert e.message.contains('You must supply COMMENT_ID')
        }
    }
}
