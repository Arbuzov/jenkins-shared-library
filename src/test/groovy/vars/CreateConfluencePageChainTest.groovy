package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

class CreateConfluencePageChainTest extends BasePipelineTest {

    def script
    def createdPages = []

    @Before
    void setUp() {
        super.setUp()
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('error', [String], { String message -> 
            throw new RuntimeException(message) 
        })
        helper.registerAllowedMethod('libraryResource', [String], { String path -> 
            return '<p>Default template content</p>' 
        })
        
        def pageIdCounter = 12345
        helper.registerAllowedMethod('createConfluencePage', [Map], { Map args ->
            def currentPageId = pageIdCounter++
            def pageUrl = "https://confluence.example.com/pages/viewpage.action?pageId=${currentPageId}"
            createdPages.add([title: args.TITLE, url: pageUrl, parentId: args.ROOT])
            return pageUrl
        })
        
        // Load real getConfluencePageId function
        def getConfluencePageIdScript = loadScript('vars/getConfluencePageId.groovy')
        helper.registerAllowedMethod('getConfluencePageId', [Map], { Map args ->
            return getConfluencePageIdScript.call(args)
        })
        
        script = loadScript('vars/createConfluencePageChain.groovy')
        createdPages.clear()
    }

    @Test
    void createsNestedChildPagesSuccessfully() {
        def children = [
            [title: 'Page 1', content: 'Content 1'],
            [title: 'Page 2', content: 'Content 2']
        ]
        
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: children
        ])
        
        assert result == "https://confluence.example.com/pages/viewpage.action?pageId=12346"
        assert createdPages.size() == 2
        assert createdPages[0].title == 'Page 1'
        assert createdPages[0].parentId == '123'  // First page under original parent
        assert createdPages[1].title == 'Page 2'
        assert createdPages[1].parentId == '12345'  // Second page under first page (nested)
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
            def currentPageId = 12345 + callCount - 1
            def pageUrl = "https://confluence.example.com/pages/viewpage.action?pageId=${currentPageId}"
            createdPages.add([title: args.TITLE, url: pageUrl, parentId: args.ROOT])
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
            assert createdPages[0].parentId == '123'  // First page was created under original parent
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
        
        assert result == "https://confluence.example.com/pages/viewpage.action?pageId=12345"
        assert createdPages.size() == 1
        assert createdPages[0].title == 'Page Without Content'
        assert createdPages[0].parentId == '123'
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
        
        assert result == "https://confluence.example.com/pages/viewpage.action?pageId=12345"
        assert createdPages.size() == 1
        assert createdPages[0].parentId == '123'
    }

    @Test
    void createsFlatPagesWhenFlattenIsTrue() {
        def children = [
            [title: 'Flat Page 1', content: 'Content 1'],
            [title: 'Flat Page 2', content: 'Content 2'],
            [title: 'Flat Page 3', content: 'Content 3']
        ]
        
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: children,
            FLATTEN: true
        ])
        
        assert result == "https://confluence.example.com/pages/viewpage.action?pageId=12347"
        assert createdPages.size() == 3
        // All pages should be direct children of the original parent (flat structure)
        assert createdPages[0].title == 'Flat Page 1'
        assert createdPages[0].parentId == '123'  // First page under original parent
        assert createdPages[1].title == 'Flat Page 2'
        assert createdPages[1].parentId == '123'  // Second page also under original parent
        assert createdPages[2].title == 'Flat Page 3'
        assert createdPages[2].parentId == '123'  // Third page also under original parent
    }

    @Test
    void createsNestedPagesWhenFlattenIsFalse() {
        def children = [
            [title: 'Nested Page 1', content: 'Content 1'],
            [title: 'Nested Page 2', content: 'Content 2'],
            [title: 'Nested Page 3', content: 'Content 3']
        ]
        
        def result = script.call([
            SPACE: 'TEST',
            ROOT: '123',
            CHILDREN: children,
            FLATTEN: false
        ])
        
        assert result == "https://confluence.example.com/pages/viewpage.action?pageId=12347"
        assert createdPages.size() == 3
        // Pages should form a nested chain
        assert createdPages[0].title == 'Nested Page 1'
        assert createdPages[0].parentId == '123'    // First page under original parent
        assert createdPages[1].title == 'Nested Page 2'
        assert createdPages[1].parentId == '12345'  // Second page under first page
        assert createdPages[2].title == 'Nested Page 3'
        assert createdPages[2].parentId == '12346'  // Third page under second page
    }
}