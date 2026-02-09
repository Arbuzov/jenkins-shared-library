/**
 * Get a Confluence comment by comment ID
 *
 * Example:
 * def body = getConfluenceComment(COMMENT_ID: '1234567890')
 * def raw = getConfluenceComment(COMMENT_ID: '1234567890', raw: true)
 *
 * @param CONFLUENCE_API The API URL of the Confluence instance
 * @param CREDENTIALS_ID The credentials ID to use for the API
 * @param COMMENT_ID The ID of the comment to get
 * @param raw Whether to return raw JSON instead of comment body
 * @param DEBUG Whether to enable debug logging
 * @return Comment body (String) by default, or raw JSON (Map) when raw=true
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.spacebridge.com/rest/api'
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  String COMMENT_ID = arg.COMMENT_ID ?: null
  Boolean DEBUG = arg.DEBUG ?: false
  Boolean RAW = (arg.raw instanceof Boolean) ? arg.raw : "${arg.raw ?: 'false'}".toBoolean()

  if (!COMMENT_ID) {
    error('You must supply COMMENT_ID')
  }

  if (DEBUG) echo "getConfluenceComment(): Getting comment by ID: ${COMMENT_ID}"

  def jsonResponse = null
  try {
    jsonResponse = httpRequest(
      url: "${CONFLUENCE_API}/content/${COMMENT_ID}?expand=body.storage",
      httpMode: 'GET',
      authentication: CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception e) {
    error("getConfluenceComment(): Failed to get comment with ID '${COMMENT_ID}': ${e.message}")
  }

  def json = null
  try {
    json = readJSON(text: jsonResponse.content)
  } catch (Exception e) {
    error("getConfluenceComment(): Failed to read JSON for comment with ID '${COMMENT_ID}': ${e.message}")
  }

  if (RAW) {
    return json
  }

  def commentBody = json?.body?.storage?.value ?: ''
  return commentBody
}
