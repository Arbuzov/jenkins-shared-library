
/**
 * Transition issue to validation and assign to Reporter.
 *
 * @deprecated Kept as a thin wrapper for existing pipelines — use
 *   jiraTransitionIssue(targetStatus: 'In Validation', assignee: <reporter>) instead.
 *
 * @param issueKey Jira issue key
 * @param credentialsId Jira credentials ID
 * @return New assignee display name
 */
String call(Map arg = [:]) {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraTransitionIssue'
  )

  def issue = null
  try {
    issue = arg.issueKey
      ? jiraGetIssueInfo(
          issueKey: arg.issueKey,
          credentialsId: arg.credentialsId ?: null
        )
      : jiraGuessIssueInfo(credentialsId: arg.credentialsId ?: null)
  } catch (Exception e) {
    throw new Exception("jiraTransitionIssueToValidation(): Failed to get Jira issue info: ${e?.message ?: 'unknown error'}")
  }
  if (!issue) {
    throw new Exception("jiraTransitionIssueToValidation(): Must provide issueKey or call within a MR context")
  }

  def reporterName = null
  def reporterDisplayName = null
  if (issue.fields.reporter
    && issue.fields.reporter.toString() != 'null'
    && issue.fields.reporter.name
    && issue.fields.reporter.name.toString() != 'null') {
    reporterName = issue.fields.reporter.name
    reporterDisplayName = issue.fields.reporter.displayName
  }

  jiraTransitionIssue(
    issueKey: issue.key,
    targetStatus: 'In Validation',
    assignee: reporterName,
    credentialsId: arg.credentialsId,
    DEBUG: arg.DEBUG,
  )
  return reporterDisplayName
}
