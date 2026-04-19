def call(issueId, credentialsId = 'jira-username-password') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def issueUrl = "https://jira.example.com/rest/api/2/issue/${issueId}"
  def issueResponce = httpRequest(
    url: issueUrl,
    httpMode: 'GET',
    contentType: 'APPLICATION_JSON',
    acceptType: 'APPLICATION_JSON',
    authentication: credentialsId,
    consoleLogResponseBody: false,
    quiet: true
  )
  return readJSON(text: issueResponce.getContent())
}

/*
 * Overloaded method to accept parameters as a map
 * Function call with named parameters is deprecated but still 
 * supported for backward compatibility
 */
def call(Map arg) {
  def issueId = arg.issueId
  def credentialsId = arg.credentialsId ?: 'jira-username-password'
  if (!issueId) {
    error("Parameter 'issueId' is required but not provided.")
  }
  return this.call(issueId, credentialsId)
}