/**
 * @deprecated Use jiraAddRemoteIssueLink(issueKey: 'PROJ-123', url: link) instead.
 *   Kept as a thin wrapper for existing pipelines.
 */
def call(issueId, link, title = '', credentialsId = 'jira-username-password') {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraAddRemoteIssueLink'
  )
  return jiraAddRemoteIssueLink(
    issueKey: issueId,
    url: link,
    title: title,
    credentialsId: credentialsId
  )
}
