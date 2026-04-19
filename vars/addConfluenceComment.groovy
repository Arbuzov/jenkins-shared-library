import groovy.json.JsonOutput

/**
 * Add a comment to a Confluence page.
 *
 * Example:
 * def comment = addConfluenceComment(PAGE_ID: '1234567890', COMMENT: '<p>Hello from Jenkins</p>')
 * echo "Comment ID: ${comment.id}"
 *
 * @param CONFLUENCE_API The API URL of the Confluence instance
 * @param CREDENTIALS_ID The credentials ID to use for the API
 * @param PAGE_ID The ID of the page to add comment to
 * @param COMMENT The comment body in Confluence storage format (HTML)
 * @param RAW Whether to return full API response (Map)
 * @param DEBUG Whether to enable debug logging
 * @return By default: [id, type, body], RAW=true: full comment JSON
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  String PAGE_ID = arg.PAGE_ID ?: arg.pageId ?: null
  String COMMENT = arg.COMMENT ?: arg.comment ?: null
  Boolean DEBUG = (arg.DEBUG instanceof Boolean) ? arg.DEBUG : "${arg.DEBUG ?: 'false'}".toBoolean()
  Boolean RAW = (arg.RAW instanceof Boolean) ? arg.RAW : "${arg.RAW ?: arg.raw ?: 'false'}".toBoolean()

  if (!PAGE_ID) {
    error('You must supply PAGE_ID')
  }
  if (!COMMENT) {
    error('You must supply COMMENT')
  }

  if (DEBUG) echo "addConfluenceComment(): Adding comment to page ID: ${PAGE_ID}"

  def commentPayload = [
    type: 'comment',
    container: [type: 'page', id: PAGE_ID.toString()],
    body: [
      storage: [
        value: COMMENT,
        representation: 'storage'
      ]
    ]
  ]

  def response = null
  try {
    response = httpRequest(
      url: "${CONFLUENCE_API}/content",
      httpMode: 'POST',
      authentication: CREDENTIALS_ID,
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      requestBody: JsonOutput.toJson(commentPayload),
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception e) {
    error("addConfluenceComment(): Failed to add comment to page '${PAGE_ID}': ${e.message}")
  }

  if (!response?.content) {
    return null
  }

  def json = null
  try {
    json = readJSON(text: response.content)
  } catch (Exception e) {
    error("addConfluenceComment(): Failed to read JSON response for page '${PAGE_ID}': ${e.message}")
  }

  if (RAW) {
    return json
  }

  return [
    id: json?.id,
    type: json?.type,
    body: json?.body?.storage?.value
  ]
}
