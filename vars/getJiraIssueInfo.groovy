call(issueId, credentialsId = 'jira-username-password') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def issueUrl = "https://jira.spacebridge.com/rest/api/2/issue/${issueId}"
  def issueResponce = httpRequest(
    url: issueUrl,
    httpMode: 'GET',
    consoleLogResponseBody: false,
    contentType: 'APPLICATION_JSON',
    authentication: credentialsId,
    quiet: true
  )
  return readJSON(text: issueResponce.getContent())
}

def call(Map params) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def issueId = params.issueId
  def credentialsId = params.credentialsId ?: 'jira-username-password'
  if (!issueId) {
    error("Parameter 'issueId' is required but not provided.")
  }
  return this.call(issueId, credentialsId)
}