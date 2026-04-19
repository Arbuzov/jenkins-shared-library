def call(Map arg) {
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
  String ID = arg.ID ?: 'SPACE'
  String LABEL = arg.LABEL ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  Boolean DEBUG = arg.DEBUG ?: false

  def linksUrl = "${CONFLUENCE_API}/content/${ID}/label"
  httpRequest(
    url: linksUrl,
    httpMode: 'POST',
    consoleLogResponseBody: DEBUG,
    contentType: 'APPLICATION_JSON',
    authentication: CREDENTIALS_ID,
    requestBody: "{\"name\": \"${LABEL}\", \"type\": \"string\", \"id\": \"https://docs.atlassian.com/jira/REST/schema/string#\"}"
  )
}