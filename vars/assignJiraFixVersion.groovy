/**
 * @deprecated Use jiraAssignFixVersionToIssues(version: '1.1.0', projectKey: 'DEVOPS',
 *   statusName: 'In Progress') instead. Kept as a thin wrapper for existing pipelines.
 */
def call(String jiraVersion, String jiraProjectKey, String statusName) {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraAssignFixVersionToIssues'
  )
  return jiraAssignFixVersionToIssues(
    version: jiraVersion,
    projectKey: jiraProjectKey,
    statusName: statusName
  )
}
