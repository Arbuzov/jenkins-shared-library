package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Test

/**
 * The deprecated Jira steps are the backward-compatibility contract of the library:
 * they have to keep their old signatures, delegate to the new jira* steps and report
 * themselves as deprecated.
 */
class DeprecatedJiraStepsTest extends BasePipelineTest {

    List<Map> usageReports
    List<Map> delegatedCalls

    @Before
    void setUp() {
        super.setUp()
        usageReports = []
        delegatedCalls = []
        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> usageReports << args })
        ['jiraGetIssueInfo', 'jiraGuessIssueInfo', 'jiraAddRemoteIssueLink',
         'jiraCreateVersion', 'jiraAssignFixVersionToIssues'].each { String step ->
            helper.registerAllowedMethod(step, [Map], { Map args ->
                delegatedCalls << ([step: step] + args)
                return [key: 'NMSNG-777']
            })
        }
    }

    @Test
    void getJiraIssueInfoKeepsThePositionalSignature() {
        def script = loadScript('vars/getJiraIssueInfo.groovy')

        script.call('NMSNG-1234')

        assert delegatedCalls == [[step: 'jiraGetIssueInfo',
                                   issueKey: 'NMSNG-1234',
                                   credentialsId: 'jira-username-password']]
        assert usageReports.size() == 1
        assert usageReports[0].deprecated == 'jiraGetIssueInfo'
    }

    @Test
    void getJiraIssueInfoKeepsTheCustomCredentialsArgument() {
        def script = loadScript('vars/getJiraIssueInfo.groovy')

        script.call('NMSNG-1234', 'other-jira')

        assert delegatedCalls[0].credentialsId == 'other-jira'
    }

    @Test
    void getJiraIssueInfoKeepsTheIssueIdMapKey() {
        def script = loadScript('vars/getJiraIssueInfo.groovy')

        script.call([issueId: 'NMSNG-1234', credentialsId: 'other-jira'])

        assert delegatedCalls[0].issueKey == 'NMSNG-1234'
        assert delegatedCalls[0].credentialsId == 'other-jira'
        assert usageReports.size() == 1
    }

    @Test
    void getJiraIssueInfoAlsoAcceptsTheNewIssueKeyMapKey() {
        def script = loadScript('vars/getJiraIssueInfo.groovy')

        script.call([issueKey: 'NMSNG-1234'])

        assert delegatedCalls[0].issueKey == 'NMSNG-1234'
        assert delegatedCalls[0].credentialsId == 'jira-username-password'
    }

    @Test
    void guessJiraIssueInfoPassesItsArgumentsThrough() {
        def script = loadScript('vars/guessJiraIssueInfo.groovy')

        def issue = script.call([credentialsId: 'other-jira'])

        assert issue.key == 'NMSNG-777'
        assert delegatedCalls == [[step: 'jiraGuessIssueInfo', credentialsId: 'other-jira']]
        assert usageReports[0].deprecated == 'jiraGuessIssueInfo'
    }

    @Test
    void guessJiraIssueInfoStillWorksWithoutArguments() {
        def script = loadScript('vars/guessJiraIssueInfo.groovy')

        script.call()

        assert delegatedCalls == [[step: 'jiraGuessIssueInfo']]
    }

    @Test
    void addJiraIssueLinkMapsItsPositionalArguments() {
        def script = loadScript('vars/addJiraIssueLink.groovy')

        script.call('NMSNG-1234', 'https://jenkins.asatnet.net/job/build/1/', 'Build 1', 'other-jira')

        assert delegatedCalls == [[step: 'jiraAddRemoteIssueLink',
                                   issueKey: 'NMSNG-1234',
                                   url: 'https://jenkins.asatnet.net/job/build/1/',
                                   title: 'Build 1',
                                   credentialsId: 'other-jira']]
        assert usageReports[0].deprecated == 'jiraAddRemoteIssueLink'
    }

    @Test
    void addJiraIssueLinkKeepsItsOptionalArguments() {
        def script = loadScript('vars/addJiraIssueLink.groovy')

        script.call('NMSNG-1234', 'https://jenkins.asatnet.net/job/build/1/')

        assert delegatedCalls[0].title == ''
        assert delegatedCalls[0].credentialsId == 'jira-username-password'
    }

    @Test
    void createNewJiraVersionMapsItsPositionalArguments() {
        def script = loadScript('vars/createNewJiraVersion.groovy')

        script.call('1.1.0', 'DEVOPS')

        assert delegatedCalls == [[step: 'jiraCreateVersion', version: '1.1.0', projectKey: 'DEVOPS']]
        assert usageReports[0].deprecated == 'jiraCreateVersion'
    }

    @Test
    void assignJiraFixVersionMapsItsPositionalArguments() {
        def script = loadScript('vars/assignJiraFixVersion.groovy')

        script.call('1.1.0', 'DEVOPS', 'In Progress')

        assert delegatedCalls == [[step: 'jiraAssignFixVersionToIssues',
                                   version: '1.1.0',
                                   projectKey: 'DEVOPS',
                                   statusName: 'In Progress']]
        assert usageReports[0].deprecated == 'jiraAssignFixVersionToIssues'
    }
}
