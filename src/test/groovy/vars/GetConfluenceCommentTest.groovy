package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class GetConfluenceCommentTest extends BasePipelineTest {

    def script
    def lastHttpRequestArgs

    @Before
    void setUp() {
        super.setUp()
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
            return [content: '{"id":"123","type":"comment","body":{"storage":{"value":"<p>Hello</p>"}}}']
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
    void failsWhenCommentIdMissing() {
        try {
            script.call([:])
            assert false, "Should have thrown an error"
        } catch (RuntimeException e) {
            assert e.message.contains('You must supply COMMENT_ID')
        }
    }
}
