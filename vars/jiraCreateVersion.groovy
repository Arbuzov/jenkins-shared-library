/**
 * Create a version in a Jira project.
 *
 * @param arg.version Version name, e.g. '1.1.1-b456' (required)
 * @param arg.projectKey Jira project key, e.g. 'NMSNG' (required)
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (!arg.version) {
    throw new Exception("jiraCreateVersion(): 'version' is required but not provided.")
  }
  if (!arg.projectKey) {
    throw new Exception("jiraCreateVersion(): 'projectKey' is required but not provided.")
  }

  step([
    $class: 'hudson.plugins.jira.JiraVersionCreatorBuilder',
    jiraVersion: arg.version,
    jiraProjectKey: arg.projectKey
  ])
}
