/**
 * Attach a remote link (e.g. a build or a report URL) to a Jira issue.
 * Not to be confused with Jira issue links, which describe relations between issues.
 *
 * Jira does not deduplicate remote links, use jiraGetRemoteIssueLinks() to check
 * whether the link is already there.
 *
 * @param arg.url Link target (required)
 * @param arg.issueKey Jira issue key (default: resolved from the MR title via jiraGuessIssueInfo)
 * @param arg.title Link title (default: the last segment of the URL)
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @return The httpRequest response of the link creation
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def credentialsId = arg.credentialsId ?: 'jira-username-password'

  def url = arg.url?.toString()?.trim()
  if (!url) {
    throw new Exception("jiraAddRemoteIssueLink(): 'url' is required but not provided.")
  }

  def issueKey = null
  try {
    issueKey = arg.issueKey
      ? arg.issueKey.toString().trim()
      : jiraGuessIssueInfo(credentialsId: credentialsId)?.key
  } catch (Exception exception) {
    throw new Exception("jiraAddRemoteIssueLink(): Failed to get Jira issue info: ${exception?.message ?: 'unknown error'}")
  }
  if (!issueKey) {
    throw new Exception("jiraAddRemoteIssueLink(): Must provide issueKey or call within a MR context")
  }

  def title = arg.title?.toString()?.trim() ?: url.tokenize('/')[-1]

  return httpRequest(
    url: "https://jira.spacebridge.com/rest/api/2/issue/${issueKey}/remotelink",
    httpMode: 'POST',
    consoleLogResponseBody: false,
    contentType: 'APPLICATION_JSON',
    authentication: credentialsId,
    requestBody: groovy.json.JsonOutput.toJson([object: [url: url, title: title]]),
    quiet: true
  )
}
