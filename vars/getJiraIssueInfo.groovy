/**
 * @deprecated Use jiraGetIssueInfo(issueKey: 'PROJ-123') instead.
 *   Kept as a thin wrapper for existing pipelines.
 */
def call(issueId, credentialsId = 'jira-username-password') {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraGetIssueInfo'
  )
  return jiraGetIssueInfo(issueKey: issueId, credentialsId: credentialsId)
}

/**
 * @deprecated Use jiraGetIssueInfo(issueKey: 'PROJ-123') instead.
 */
def call(Map arg) {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraGetIssueInfo'
  )
  return jiraGetIssueInfo(
    issueKey: arg.issueId ?: arg.issueKey,
    credentialsId: arg.credentialsId ?: 'jira-username-password'
  )
}
