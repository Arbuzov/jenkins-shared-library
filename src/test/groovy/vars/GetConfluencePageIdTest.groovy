package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class GetConfluencePageIdTest extends BasePipelineTest {

    def script

    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
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
}
