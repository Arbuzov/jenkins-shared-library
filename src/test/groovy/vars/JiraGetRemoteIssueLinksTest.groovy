package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class JiraGetRemoteIssueLinksTest extends BasePipelineTest {

    def script
    Map lastHttpRequestArgs
    String httpResponseContent
    Map guessedIssue
    List<String> echoMessages

    @Before
    void setUp() {
        super.setUp()
        lastHttpRequestArgs = null
        echoMessages = []
        guessedIssue = [key: 'NMSNG-777']
        httpResponseContent = '''[
          {"id":1,"self":"https://jira.spacebridge.com/rest/api/2/issue/10000/remotelink/1",
           "object":{"url":"https://jenkins.asatnet.net/job/build/1/","title":"Report TC-NMSNG-042 (build 1)"}},
          {"id":2,"self":"https://jira.spacebridge.com/rest/api/2/issue/10000/remotelink/2",
           "object":{"url":"https://jenkins.asatnet.net/job/build/2/","title":"Report TC-NMSNG-100 (build 2)"}},
          {"id":3,"self":"https://jira.spacebridge.com/rest/api/2/issue/10000/remotelink/3",
           "object":{"url":"https://confluence.spacebridge.com/display/X"}}
        ]'''

        helper.registerAllowedMethod('reportUsage', [String], { String path -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            // Jenkins readJSON gives json-lib objects unless returnPojo is set
            assert args.returnPojo == true
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('guessJiraIssueInfo', [Map], { Map args -> guessedIssue })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return [status: 200, content: httpResponseContent]
        })
        script = loadScript('vars/jiraGetRemoteIssueLinks.groovy')
    }

    @Test
    void returnsAllLinksOfTheGivenIssue() {
        def links = script.call([issueKey: 'NMSNG-1234'])

        assert links.size() == 3
        assert links[0].object.url == 'https://jenkins.asatnet.net/job/build/1/'
        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234/remotelink'
        assert lastHttpRequestArgs.httpMode == 'GET'
        assert lastHttpRequestArgs.authentication == 'jira-username-password'
        assert lastHttpRequestArgs.quiet == true
    }

    @Test
    void filtersByTitleSubstring() {
        def links = script.call([issueKey: 'NMSNG-1234', titleContains: 'TC-NMSNG-042'])

        assert links.size() == 1
        assert links[0].id == 1
    }

    @Test
    void titleFilterDoesNotMatchAnotherTestCaseId() {
        assert script.call([issueKey: 'NMSNG-1234', titleContains: 'TC-NMSNG-04']).size() == 1
        assert script.call([issueKey: 'NMSNG-1234', titleContains: 'TC-NMSNG-99']).isEmpty()
    }

    @Test
    void titleFilterSkipsLinksWithoutATitle() {
        def links = script.call([issueKey: 'NMSNG-1234', titleContains: 'display'])

        assert links.isEmpty()
    }

    @Test
    void filtersByExactUrl() {
        def links = script.call([issueKey: 'NMSNG-1234', url: 'https://jenkins.asatnet.net/job/build/2/'])

        assert links.size() == 1
        assert links[0].object.title == 'Report TC-NMSNG-100 (build 2)'
    }

    @Test
    void urlFilterIsExactMatch() {
        assert script.call([issueKey: 'NMSNG-1234', url: 'https://jenkins.asatnet.net/job/build/2']).isEmpty()
    }

    @Test
    void appliesTitleAndUrlFiltersTogether() {
        def links = script.call([
            issueKey: 'NMSNG-1234',
            titleContains: 'TC-NMSNG-042',
            url: 'https://jenkins.asatnet.net/job/build/2/'])

        assert links.isEmpty()
    }

    @Test
    void returnsEmptyListWhenIssueHasNoLinks() {
        httpResponseContent = '[]'

        assert script.call([issueKey: 'NMSNG-1234']).isEmpty()
    }

    @Test
    void failsWhenResponseIsBlank() {
        httpResponseContent = '   '

        try {
            script.call([issueKey: 'NMSNG-1234'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Empty response while listing remote links of NMSNG-1234')
        }
    }

    @Test
    void failsWhenResponseHasNoContent() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args -> return [status: 200] })

        try {
            script.call([issueKey: 'NMSNG-1234'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Empty response while listing remote links of NMSNG-1234')
        }
    }

    @Test
    void trimsTheProvidedIssueKey() {
        script.call([issueKey: '  NMSNG-1234 '])

        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234/remotelink'
    }

    @Test
    void resolvesIssueKeyFromMrContextWhenNotProvided() {
        def links = script.call([:])

        assert links.size() == 3
        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-777/remotelink'
    }

    @Test
    void usesCustomCredentialsAndDebugLogging() {
        script.call([issueKey: 'NMSNG-1234', credentialsId: 'other-jira', DEBUG: true, titleContains: 'TC-NMSNG-042'])

        assert lastHttpRequestArgs.authentication == 'other-jira'
        assert lastHttpRequestArgs.quiet == false
        assert lastHttpRequestArgs.consoleLogResponseBody == true
        assert echoMessages.any { it.contains('NMSNG-1234 has 3 remote link(s), returning 1') }
    }

    @Test
    void failsWhenIssueKeyCannotBeResolved() {
        guessedIssue = null

        try {
            script.call([:])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Must provide issueKey or call within a MR context')
        }
    }

    @Test
    void failsWhenJiraCallFails() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            throw new RuntimeException('403 Forbidden')
        })

        try {
            script.call([issueKey: 'NMSNG-1234'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Failed to list remote links of NMSNG-1234')
            assert exception.message.contains('403 Forbidden')
        }
    }
}
