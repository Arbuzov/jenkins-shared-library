def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.spacebridge.com/rest/api'
  String TITLE = arg.TITLE ?: 'New page'
  String SPACE = arg.SPACE ?: 'SPACE'
  String ROOT = arg.ROOT ?: ''
  String CONTENT = arg.CONTENT ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  echo CONTENT
  def postContentUrl = "${CONFLUENCE_API}/content"
  def newReleaseContent = "{\"type\":\"page\",\"title\":\"${TITLE}\",\"ancestors\":[{\"id\":\"${ROOT}\"}],\"space\":{\"key\":\"${SPACE}\"},\"body\":{\"storage\":{\"value\":\"${CONTENT.replaceAll('"','\'')}\",\"representation\":\"storage\"}}}"
  def releaseResponce = httpRequest(
    url: postContentUrl,
    httpMode: 'POST',
    consoleLogResponseBody: true,
    contentType: 'APPLICATION_JSON',
    authentication: CREDENTIALS_ID,
    requestBody: newReleaseContent
  )
}