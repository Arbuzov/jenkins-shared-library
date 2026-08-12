/**
 * Fetch a Jira issue via the Jira REST API.
 *
 * @param arg.issueKey Jira issue key or numeric id, e.g. 'NMSNG-1234' (required)
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @return Map with the issue JSON
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def issueKey = arg.issueKey?.toString()?.trim()
  if (!issueKey) {
    throw new Exception("jiraGetIssueInfo(): 'issueKey' is required but not provided.")
  }

  def response = httpRequest(
    url: "https://jira.spacebridge.com/rest/api/2/issue/${issueKey}",
    httpMode: 'GET',
    contentType: 'APPLICATION_JSON',
    acceptType: 'APPLICATION_JSON',
    authentication: arg.credentialsId ?: 'jira-username-password',
    consoleLogResponseBody: false,
    quiet: true
  )
  return readJSON(text: response.getContent())
}
