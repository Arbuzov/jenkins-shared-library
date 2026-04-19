
def call(String jiraVersion, String jiraProjectKey) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  step([
    $class: 'hudson.plugins.jira.JiraVersionCreatorBuilder', 
    jiraVersion: jiraVersion,
    jiraProjectKey: jiraProjectKey
  ])
}