/**
 * List the remote issue links of a Jira issue — the links created by addJiraIssueLink().
 * Not to be confused with Jira issue links, which describe relations between issues.
 *
 * Typical use is a duplicate check before adding a link: read the links of an issue and
 * add a new one only when it is not there yet. The check is best effort — a concurrent
 * build can add the same link between the check and the creation.
 *
 * @param arg.issueKey Jira issue key (default: resolved from the MR title via guessJiraIssueInfo)
 * @param arg.titleContains Return only the links whose title contains this substring (case sensitive)
 * @param arg.url Return only the links pointing to this exact URL
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @param arg.DEBUG Log the request, the response and the number of links (default: false)
 * @return List of remote link Maps, each with id, self and object [url, title], empty when nothing matches
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def DEBUG = arg.DEBUG ?: false
  def credentialsId = arg.credentialsId ?: 'jira-username-password'

  def issueKey = null
  try {
    issueKey = arg.issueKey
      ? arg.issueKey.toString().trim()
      : guessJiraIssueInfo(credentialsId: credentialsId)?.key
  } catch (Exception exception) {
    throw new Exception("jiraGetRemoteIssueLinks(): Failed to get Jira issue info: ${exception?.message ?: 'unknown error'}")
  }
  if (!issueKey) {
    throw new Exception("jiraGetRemoteIssueLinks(): Must provide issueKey or call within a MR context")
  }

  def response
  try {
    response = httpRequest(
      url: "https://jira.spacebridge.com/rest/api/2/issue/${issueKey}/remotelink",
      httpMode: 'GET',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: credentialsId,
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception exception) {
    throw new Exception("jiraGetRemoteIssueLinks(): Failed to list remote links of ${issueKey}: ${exception?.message ?: 'unknown error'}")
  }

  // An issue without remote links still answers with '[]', so an empty body is an anomaly.
  // The endpoint is not paginated, the answer always holds every link of the issue.
  def content = response?.content
  if (!content?.trim()) {
    throw new Exception("jiraGetRemoteIssueLinks(): Empty response while listing remote links of ${issueKey}")
  }
  def allLinks = readJSON(text: content, returnPojo: true) ?: []

  def links = allLinks
  if (arg.titleContains) {
    links = links.findAll { (it.object?.title ?: '').toString().contains(arg.titleContains) }
  }
  if (arg.url) {
    links = links.findAll { (it.object?.url ?: '').toString() == arg.url }
  }

  if (DEBUG) {
    echo "jiraGetRemoteIssueLinks(): ${issueKey} has ${allLinks.size()} remote link(s), returning ${links.size()}"
  }
  return links
}
