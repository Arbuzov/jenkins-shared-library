package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class JiraGetIssueInfoTest extends BasePipelineTest {

    def script
    Map lastHttpRequestArgs
    List<Map> commentCalls
    List<Map> issueComments
    String httpResponseContent

    @Before
    void setUp() {
        super.setUp()
        lastHttpRequestArgs = null
        commentCalls = []
        issueComments = [[id: '1', body: 'First'], [id: '2', body: 'Second']]
        httpResponseContent = '''{"key":"NMSNG-1234","fields":{"summary":"Stamped manifest",
          "comment":{"startAt":0,"maxResults":1,"total":2,"comments":[{"id":"1","body":"First"}]}}}'''

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('jiraGetIssueComments', [Map], { Map args ->
            commentCalls << args
            return issueComments
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return [status: 200, content: httpResponseContent]
        })
        script = loadScript('vars/jiraGetIssueInfo.groovy')
    }

    @Test
    void fetchesTheIssueByKey() {
        def issue = script.call([issueKey: 'NMSNG-1234'])

        assert issue.key == 'NMSNG-1234'
        assert issue.fields.summary == 'Stamped manifest'
        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234'
        assert lastHttpRequestArgs.httpMode == 'GET'
        assert lastHttpRequestArgs.authentication == 'jira-username-password'
        assert lastHttpRequestArgs.quiet == true
    }

    @Test
    void leavesTheCommentPageAloneByDefault() {
        def issue = script.call([issueKey: 'NMSNG-1234'])

        assert commentCalls.isEmpty()
        assert issue.fields.comment.comments.size() == 1
        assert issue.fields.comment.maxResults == 1
    }

    @Test
    void replacesTheCommentPageWithTheWholeThreadWhenAsked() {
        def issue = script.call([issueKey: 'NMSNG-1234', withComments: true])

        assert commentCalls == [[issueKey: 'NMSNG-1234', credentialsId: 'jira-username-password']]
        assert issue.fields.comment.comments == issueComments
        assert issue.fields.comment.total == 2
        assert issue.fields.comment.startAt == 0
        assert issue.fields.comment.maxResults == 2
        assert issue.fields.summary == 'Stamped manifest'
    }

    @Test
    void addsTheCommentsToAnIssueWithoutACommentField() {
        httpResponseContent = '{"key":"NMSNG-1234","fields":{"summary":"No comments yet"}}'

        def issue = script.call([issueKey: 'NMSNG-1234', withComments: true])

        assert issue.fields.comment.comments == issueComments
        assert issue.fields.summary == 'No comments yet'
    }

    @Test
    void reportsAnEmptyThreadAsAnEmptyCommentList() {
        issueComments = []

        def issue = script.call([issueKey: 'NMSNG-1234', withComments: true])

        assert issue.fields.comment.comments.isEmpty()
        assert issue.fields.comment.total == 0
    }

    @Test
    void passesTheCredentialsToBothCalls() {
        script.call([issueKey: 'NMSNG-1234', withComments: true, credentialsId: 'other-jira'])

        assert lastHttpRequestArgs.authentication == 'other-jira'
        assert commentCalls[0].credentialsId == 'other-jira'
    }

    @Test
    void trimsTheProvidedIssueKey() {
        script.call([issueKey: '  NMSNG-1234 ', withComments: true])

        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234'
        assert commentCalls[0].issueKey == 'NMSNG-1234'
    }

    @Test
    void failsWhenIssueKeyIsMissing() {
        try {
            script.call([:])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains("'issueKey' is required but not provided")
        }
    }
}
