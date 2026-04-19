def call(Map arg) {
  String ID = arg.ID ?: 'SPACE'
  String LABEL = arg.LABEL ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  Boolean DEBUG = arg.DEBUG ?: false

  def linksUrl = "https://confluence.example.com/rest/api/content/${ID}/label"
  httpRequest(
    url: linksUrl,
    httpMode: 'POST',
    consoleLogResponseBody: DEBUG,
    contentType: 'APPLICATION_JSON',
    authentication: CREDENTIALS_ID,
    requestBody: "{\"name\": \"${LABEL}\", \"type\": \"string\", \"id\": \"https://docs.atlassian.com/jira/REST/schema/string#\"}"
  )
}