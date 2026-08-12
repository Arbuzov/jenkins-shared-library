/**
 * @deprecated Use jiraGuessIssueInfo() instead.
 *   Kept as a thin wrapper for existing pipelines.
 */
def call(Map arg = [:]) {
  reportUsage(
    path: getClass().protectionDomain.codeSource.location.path,
    deprecated: 'jiraGuessIssueInfo'
  )
  return jiraGuessIssueInfo(arg)
}
