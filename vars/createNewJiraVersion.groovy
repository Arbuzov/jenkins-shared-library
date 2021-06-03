
def call(String jiraVersion, String jiraProjectKey) {
  step([
    $class: 'hudson.plugins.jira.JiraVersionCreatorBuilder', 
    jiraVersion: jiraVersion,
    jiraProjectKey: jiraProjectKey
  ])
}