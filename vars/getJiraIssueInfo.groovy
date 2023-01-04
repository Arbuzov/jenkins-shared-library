call(issueId, credentialsId = 'jira-username-password') {
  def issueUrl = "https://jira.spacebridge.com/rest/api/2/issue/${issueId}"
    def issueResponce = httpRequest(
      url: issueUrl,
      httpMode: 'GET',
      consoleLogResponseBody: false,
      contentType: 'APPLICATION_JSON',
      authentication: credentialsId
    )
  return readJSON(text: issueResponce.getContent())
}