/**
 * Fetch a Jira issue via the Jira REST API.
 *
 * @param arg.issueKey Jira issue key or numeric id, e.g. 'NMSNG-1234' (required)
 * @param arg.withComments Replace fields.comment with every comment of the issue,
 *   read through jiraGetIssueComments (default: false)
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @return Map with the issue JSON
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def issueKey = arg.issueKey?.toString()?.trim()
  if (!issueKey) {
    throw new Exception("jiraGetIssueInfo(): 'issueKey' is required but not provided.")
  }
  def credentialsId = arg.credentialsId ?: 'jira-username-password'

  def response = httpRequest(
    url: "https://jira.spacebridge.com/rest/api/2/issue/${issueKey}",
    httpMode: 'GET',
    contentType: 'APPLICATION_JSON',
    acceptType: 'APPLICATION_JSON',
    authentication: credentialsId,
    consoleLogResponseBody: false,
    quiet: true
  )
  def issue = readJSON(text: response?.content)

  if (arg.withComments) {
    // The issue endpoint carries one page of fields.comment; replace it with the whole thread.
    def comments = jiraGetIssueComments(issueKey: issueKey, credentialsId: credentialsId)
    issue.fields = issue.fields ?: [:]
    issue.fields.comment = [
      comments: comments,
      total: comments.size(),
      startAt: 0,
      maxResults: comments.size()
    ]
  }
  return issue
}
