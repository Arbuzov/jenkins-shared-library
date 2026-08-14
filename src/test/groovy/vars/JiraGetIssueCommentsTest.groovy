package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class JiraGetIssueCommentsTest extends BasePipelineTest {

    def script
    List<Map> httpRequests
    List<Map> allComments
    Map guessedIssue
    List<String> echoMessages
    String forcedResponseContent

    @Before
    void setUp() {
        super.setUp()
        httpRequests = []
        echoMessages = []
        forcedResponseContent = null
        guessedIssue = [key: 'NMSNG-777']
        allComments = (1..5).collect { index ->
            [id      : "${index}".toString(),
             self    : "https://jira.spacebridge.com/rest/api/2/issue/10000/comment/${index}".toString(),
             author  : [name: 'arbuzov', displayName: 'Serge Arbuzov'],
             body    : "Comment ${index}".toString(),
             created : "2026-08-1${index}T10:00:00.000+0000".toString()]
        }

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            // Jenkins readJSON gives json-lib objects unless returnPojo is set
            assert args.returnPojo == true
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('jiraGuessIssueInfo', [Map], { Map args -> guessedIssue })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            httpRequests << args
            if (forcedResponseContent != null) {
                return [status: 200, content: forcedResponseContent]
            }
            return [status: 200, content: pageFor(args.url as String)]
        })
        script = loadScript('vars/jiraGetIssueComments.groovy')
    }

    private String pageFor(String url) {
        int startAt = ((url =~ /startAt=(\d+)/)[0][1]) as int
        int maxResults = ((url =~ /maxResults=(\d+)/)[0][1]) as int
        return JsonOutput.toJson([startAt   : startAt,
                                  maxResults: maxResults,
                                  total     : allComments.size(),
                                  comments  : allComments.drop(startAt).take(maxResults)])
    }

    @Test
    void returnsEveryCommentOfTheGivenIssue() {
        def comments = script.call([issueKey: 'NMSNG-1234'])

        assert comments.size() == 5
        assert comments[0].body == 'Comment 1'
        assert comments[4].body == 'Comment 5'
        assert httpRequests.size() == 1
        assert httpRequests[0].url.startsWith(
            'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234/comment?startAt=0&maxResults=100')
        assert httpRequests[0].httpMode == 'GET'
        assert httpRequests[0].authentication == 'jira-username-password'
        assert httpRequests[0].quiet == true
    }

    @Test
    void walksEveryPageWhenJiraPaginates() {
        def comments = script.call([issueKey: 'NMSNG-1234', pageSize: 2])

        assert comments.size() == 5
        assert comments.collect { it.id } == ['1', '2', '3', '4', '5']
        assert httpRequests.size() == 3
        assert httpRequests[0].url.contains('startAt=0&maxResults=2')
        assert httpRequests[1].url.contains('startAt=2&maxResults=2')
        assert httpRequests[2].url.contains('startAt=4&maxResults=2')
    }

    @Test
    void stopsAtTheRequestedLimit() {
        def comments = script.call([issueKey: 'NMSNG-1234', limit: 3])

        assert comments.size() == 3
        assert comments.collect { it.id } == ['1', '2', '3']
    }

    @Test
    void limitShrinksThePageSizeToASingleCall() {
        script.call([issueKey: 'NMSNG-1234', limit: 2])

        assert httpRequests.size() == 1
        assert httpRequests[0].url.contains('startAt=0&maxResults=2')
    }

    @Test
    void limitLargerThanTheIssueReturnsEverything() {
        assert script.call([issueKey: 'NMSNG-1234', limit: 50]).size() == 5
    }

    @Test
    void truncatesTheLastPageToTheLimit() {
        def comments = script.call([issueKey: 'NMSNG-1234', pageSize: 2, limit: 3])

        assert comments.size() == 3
        assert httpRequests.size() == 2
    }

    @Test
    void passesOrderByAndExpandToJira() {
        script.call([issueKey: 'NMSNG-1234', orderBy: '-created', expand: 'renderedBody'])

        assert httpRequests[0].url.contains('&orderBy=-created')
        assert httpRequests[0].url.contains('&expand=renderedBody')
    }

    @Test
    void omitsOrderByAndExpandWhenNotAsked() {
        script.call([issueKey: 'NMSNG-1234'])

        assert !httpRequests[0].url.contains('orderBy')
        assert !httpRequests[0].url.contains('expand')
    }

    @Test
    void returnsEmptyListWhenIssueHasNoComments() {
        forcedResponseContent = '{"startAt":0,"maxResults":100,"total":0,"comments":[]}'

        assert script.call([issueKey: 'NMSNG-1234']).isEmpty()
        assert httpRequests.size() == 1
    }

    @Test
    void failsWhenResponseIsBlank() {
        forcedResponseContent = '   '

        try {
            script.call([issueKey: 'NMSNG-1234'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Empty response while listing comments of NMSNG-1234')
        }
    }

    @Test
    void failsWhenResponseHasNoContent() {
        helper.registerAllowedMethod('httpRequest', [Map], { Map args -> return [status: 200] })

        try {
            script.call([issueKey: 'NMSNG-1234'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Empty response while listing comments of NMSNG-1234')
        }
    }

    @Test
    void trimsTheProvidedIssueKey() {
        script.call([issueKey: '  NMSNG-1234 '])

        assert httpRequests[0].url.startsWith(
            'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234/comment?')
    }

    @Test
    void resolvesIssueKeyFromMrContextWhenNotProvided() {
        def comments = script.call([:])

        assert comments.size() == 5
        assert httpRequests[0].url.startsWith(
            'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-777/comment?')
    }

    @Test
    void usesCustomCredentialsAndDebugLogging() {
        script.call([issueKey: 'NMSNG-1234', credentialsId: 'other-jira', DEBUG: true, limit: 2])

        assert httpRequests[0].authentication == 'other-jira'
        assert httpRequests[0].quiet == false
        assert httpRequests[0].consoleLogResponseBody == true
        assert echoMessages.any { it.contains('NMSNG-1234 has 5 comment(s), returning 2') }
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
            assert exception.message.contains('Failed to list comments of NMSNG-1234')
            assert exception.message.contains('403 Forbidden')
        }
    }
}
