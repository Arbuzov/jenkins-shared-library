/**
 * @deprecated Use jiraCreateVersion(version: '1.1.0', projectKey: 'DEVOPS') instead.
 *   Kept as a thin wrapper for existing pipelines.
 */
def call(String jiraVersion, String jiraProjectKey) {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraCreateVersion'
  )
  return jiraCreateVersion(version: jiraVersion, projectKey: jiraProjectKey)
}
