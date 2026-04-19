def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
  def comment = arg.comment
  if(!comment) {
    throw new Exception("jiraAddComment(): argument 'comment' is required but not provided.")
  }
  def issueKey = null
  try {
    issueKey = arg.issueKey
      ? arg.issueKey
      : guessJiraIssueInfo(credentialsId: arg.credentialsId ?: null).key;
  } catch (Exception e) {
    throw new Exception("jiraAddComment(): Failed to get Jira issue info: ${e?.message ?: 'unknown error'}")
  }
  if(!issueKey) {
    throw new Exception("jiraAddComment(): Must provide issueKey or call within a MR context")
  }

  def body = [
    "body": comment
  ]

  def response = httpRequest(
    url: "https://jira.example.com/rest/api/2/issue/${issueKey}/comment",
    httpMode: 'POST',
    authentication: arg.credentialsId ?: 'jira-username-password',
    contentType: 'APPLICATION_JSON',
    acceptType: 'APPLICATION_JSON',
    requestBody: groovy.json.JsonOutput.toJson(body),
    consoleLogResponseBody: false,
    quiet: true
  )
  def responseBody = readJSON(text: response.content)
  return responseBody
}
