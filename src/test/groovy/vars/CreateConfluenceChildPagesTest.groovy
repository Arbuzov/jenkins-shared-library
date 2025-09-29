package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class CreateConfluenceChildPagesTest extends BasePipelineTest {

    def script
    def createdPages = []

    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('error', [String], { String message -> 
            throw new RuntimeException(message) 
        })
        helper.registerAllowedMethod('libraryResource', [String], { String path -> 
            return '<p>Default template content</p>' 
        })
        helper.registerAllowedMethod('createConfluencePage', [Map], { Map args ->
            def pageUrl = "https://confluence.example.com/pages/viewpage.action?pageId=12345"
            createdPages.add([title: args.TITLE, url: pageUrl])
            return pageUrl
        })
        
        script = loadScript('vars/createConfluenceChildPages.groovy')
        createdPages.clear()
    }

    @Test
    void createsChildPagesSuccessfully() {
        def children = [
            [title: 'Page 1', content: 'Content 1'],
            [title: 'Page 2', content: 'Content 2']
        ]
        
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: children
        ])
        
        assert result == "https://confluence.example.com/pages/viewpage.action?pageId=12345"
        assert createdPages.size() == 2
        assert createdPages[0].title == 'Page 1'
        assert createdPages[1].title == 'Page 2'
    }

    @Test
    void returnsNullWhenNoChildren() {
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: []
        ])
        
        assert result == null
        assert createdPages.size() == 0
    }

    @Test
    void failsWhenChildrenIsString() {
        try {
            script.call([
                SPACE: 'TEST',
                ROOT: '123',
                CHILDREN: 'not a list'
            ])
            assert false, "Should have thrown an error"
        } catch (RuntimeException e) {
            assert e.message.contains('CHILDREN parameter must be a List, but received a String')
        }
    }

    @Test
    void failsWhenChildrenIsNotList() {
        try {
            script.call([
                SPACE: 'TEST',
                ROOT: '123',
                CHILDREN: 123
            ])
            assert false, "Should have thrown an error"
        } catch (RuntimeException e) {
            assert e.message.contains('CHILDREN parameter must be a List, but received: Integer')
        }
    }

    @Test
    void failsWhenPageCreationFails() {
        // Override createConfluencePage to throw error on second call
        def callCount = 0
        helper.registerAllowedMethod('createConfluencePage', [Map], { Map args ->
            callCount++
            if (callCount == 2) {
                throw new RuntimeException("Confluence API error")
            }
            def pageUrl = "https://confluence.example.com/pages/viewpage.action?pageId=12345"
            createdPages.add([title: args.TITLE, url: pageUrl])
            return pageUrl
        })

        def children = [
            [title: 'Page 1', content: 'Content 1'],
            [title: 'Page 2', content: 'Content 2']
        ]
        
        try {
            script.call([
                SPACE: 'TEST',
                ROOT: '123',
                CHILDREN: children
            ])
            assert false, "Should have thrown an error"
        } catch (RuntimeException e) {
            assert e.message.contains("Failed to create child page 'Page 2': Confluence API error")
            assert createdPages.size() == 1  // Only first page created
        }
    }

    @Test
    void usesFallbackTemplateWhenNoContent() {
        def children = [
            [title: 'Page Without Content']
        ]
        
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: children
        ])
        
        assert result != null
        assert createdPages.size() == 1
        assert createdPages[0].title == 'Page Without Content'
    }

    @Test
    void handlesDebugMode() {
        def children = [
            [title: 'Debug Page']
        ]
        
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: children,
            DEBUG: true
        ])
        
        assert result != null
        assert createdPages.size() == 1
    }
}