/**
 * List the comments of a Jira issue.
 *
 * The comment endpoint is paginated, so the step walks every page: the whole discussion
 * of a long-running ticket comes back instead of Jira's first page only.
 *
 * @param arg.issueKey Jira issue key (default: resolved from the MR title via jiraGuessIssueInfo)
 * @param arg.orderBy Sort order asked from Jira, 'created' oldest first or '-created' newest first (default: Jira's own order)
 * @param arg.expand Jira comment expand parameter, for example 'renderedBody' (default: none)
 * @param arg.limit Stop after this many comments, 0 for the whole issue (default: 0)
 * @param arg.pageSize Comments requested per Jira call (default: 100)
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @param arg.DEBUG Log the request, the response and the number of comments (default: false)
 * @return List of comment Maps, each with id, self, author, body, created and updated, empty when the issue has none
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def DEBUG = arg.DEBUG ?: false
  def credentialsId = arg.credentialsId ?: 'jira-username-password'
  def limit = (arg.limit ?: 0) as int
  def pageSize = (arg.pageSize ?: 100) as int
  if (limit && limit < pageSize) {
    pageSize = limit
  }

  def issueKey = null
  try {
    issueKey = arg.issueKey
      ? arg.issueKey.toString().trim()
      : jiraGuessIssueInfo(credentialsId: credentialsId)?.key
  } catch (Exception exception) {
    throw new Exception("jiraGetIssueComments(): Failed to get Jira issue info: ${exception?.message ?: 'unknown error'}")
  }
  if (!issueKey) {
    throw new Exception("jiraGetIssueComments(): Must provide issueKey or call within a MR context")
  }

  def comments = []
  def total = 0
  while (true) {
    def query = "startAt=${comments.size()}&maxResults=${pageSize}"
    if (arg.orderBy) {
      query += "&orderBy=${arg.orderBy}"
    }
    if (arg.expand) {
      query += "&expand=${arg.expand}"
    }

    def response
    try {
      response = httpRequest(
        url: "https://jira.spacebridge.com/rest/api/2/issue/${issueKey}/comment?${query}",
        httpMode: 'GET',
        contentType: 'APPLICATION_JSON',
        acceptType: 'APPLICATION_JSON',
        authentication: credentialsId,
        consoleLogResponseBody: DEBUG,
        quiet: !DEBUG
      )
    } catch (Exception exception) {
      throw new Exception("jiraGetIssueComments(): Failed to list comments of ${issueKey}: ${exception?.message ?: 'unknown error'}")
    }

    // An issue without comments still answers with a page carrying 'comments': [],
    // so an empty body is an anomaly and must not be reported as "no comments".
    def content = response?.content
    if (!content?.trim()) {
      throw new Exception("jiraGetIssueComments(): Empty response while listing comments of ${issueKey}")
    }
    def page = readJSON(text: content, returnPojo: true) ?: [:]
    def pageComments = page.comments ?: []
    total = (page.total ?: 0) as int
    comments.addAll(pageComments)

    if (pageComments.isEmpty() || comments.size() >= total) {
      break
    }
    if (limit && comments.size() >= limit) {
      break
    }
  }

  if (limit && comments.size() > limit) {
    comments = comments.take(limit)
  }

  if (DEBUG) {
    echo "jiraGetIssueComments(): ${issueKey} has ${total} comment(s), returning ${comments.size()}"
  }
  return comments
}
