package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class JiraAddRemoteIssueLinkTest extends BasePipelineTest {

    def script
    Map lastHttpRequestArgs
    Map guessedIssue

    @Before
    void setUp() {
        super.setUp()
        lastHttpRequestArgs = null
        guessedIssue = [key: 'NMSNG-777']

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('jiraGuessIssueInfo', [Map], { Map args -> guessedIssue })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            lastHttpRequestArgs = args
            return [status: 201, content: '{"id":10000}']
        })
        script = loadScript('vars/jiraAddRemoteIssueLink.groovy')
    }

    Map requestBody() {
        new JsonSlurper().parseText(lastHttpRequestArgs.requestBody as String)
    }

    @Test
    void postsTheLinkToTheGivenIssue() {
        def response = script.call([
            issueKey: 'NMSNG-1234',
            url: 'https://jenkins.asatnet.net/job/build/1/',
            title: 'Build 1'])

        assert response.status == 201
        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234/remotelink'
        assert lastHttpRequestArgs.httpMode == 'POST'
        assert lastHttpRequestArgs.authentication == 'jira-username-password'
        assert requestBody().object.url == 'https://jenkins.asatnet.net/job/build/1/'
        assert requestBody().object.title == 'Build 1'
    }

    @Test
    void titleDefaultsToTheLastUrlSegment() {
        script.call([issueKey: 'NMSNG-1234', url: 'https://jenkins.asatnet.net/job/build/report.html'])

        assert requestBody().object.title == 'report.html'
    }

    @Test
    void blankTitleFallsBackToTheLastUrlSegment() {
        script.call([issueKey: 'NMSNG-1234', url: 'https://jenkins.asatnet.net/job/build/1/', title: '  '])

        assert requestBody().object.title == '1'
    }

    @Test
    void escapesTheTitleAndTheUrl() {
        script.call([
            issueKey: 'NMSNG-1234',
            url: 'https://jenkins.asatnet.net/job/build/1/',
            title: 'Report "quoted" \\ build'])

        assert requestBody().object.title == 'Report "quoted" \\ build'
    }

    @Test
    void resolvesIssueKeyFromMrContextWhenNotProvided() {
        script.call([url: 'https://jenkins.asatnet.net/job/build/1/'])

        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-777/remotelink'
    }

    @Test
    void trimsTheProvidedIssueKey() {
        script.call([issueKey: '  NMSNG-1234 ', url: 'https://jenkins.asatnet.net/job/build/1/'])

        assert lastHttpRequestArgs.url == 'https://jira.spacebridge.com/rest/api/2/issue/NMSNG-1234/remotelink'
    }

    @Test
    void usesCustomCredentials() {
        script.call([issueKey: 'NMSNG-1234', url: 'https://jenkins.asatnet.net/', credentialsId: 'other-jira'])

        assert lastHttpRequestArgs.authentication == 'other-jira'
    }

    @Test
    void failsWithoutAUrl() {
        try {
            script.call([issueKey: 'NMSNG-1234'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains("'url' is required")
        }
        assert lastHttpRequestArgs == null
    }

    @Test
    void failsWhenIssueKeyCannotBeResolved() {
        guessedIssue = null

        try {
            script.call([url: 'https://jenkins.asatnet.net/'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('Must provide issueKey or call within a MR context')
        }
    }
}
