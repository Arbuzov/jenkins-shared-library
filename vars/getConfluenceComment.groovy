/**
 * Get a Confluence comment by comment ID
 *
 * Example:
 * def body = getConfluenceComment(COMMENT_ID: '1234567890')
 * def raw = getConfluenceComment(COMMENT_ID: '1234567890', RAW: true)
 *
 * @param CONFLUENCE_API The API URL of the Confluence instance
 * @param CREDENTIALS_ID The credentials ID to use for the API
 * @param COMMENT_ID The ID of the comment to get
 * @param RAW Whether to return raw JSON instead of comment body
 * @param ALLOW_NOT_FOUND If true, do not fail when comment is not found (404)
 * @param DEBUG Whether to enable debug logging
 * @return Comment body by default (String or null), raw JSON (Map) when RAW=true, or null when not found and ALLOW_NOT_FOUND=true
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  String COMMENT_ID = arg.COMMENT_ID ?: null
  Boolean DEBUG = arg.DEBUG ?: false
  Boolean RAW = (arg.RAW instanceof Boolean) ? arg.RAW : "${arg.RAW ?: arg.raw ?: 'false'}".toBoolean()
  Boolean ALLOW_NOT_FOUND = (arg.ALLOW_NOT_FOUND instanceof Boolean) ? arg.ALLOW_NOT_FOUND : "${arg.ALLOW_NOT_FOUND ?: arg.allowNotFound ?: 'false'}".toBoolean()

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
      validResponseCodes: ALLOW_NOT_FOUND ? '100:404' : '100:399',
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception e) {
    error("getConfluenceComment(): Failed to get comment with ID '${COMMENT_ID}': ${e.message}")
  }

  if (ALLOW_NOT_FOUND && jsonResponse?.status == 404) {
    if (DEBUG) echo "getConfluenceComment(): Comment not found for ID: ${COMMENT_ID}"
    return null
  }

  def json = null
  try {
    if (!jsonResponse?.content) {
      return null
    }
    json = readJSON(text: jsonResponse.content)
  } catch (Exception e) {
    error("getConfluenceComment(): Failed to read JSON for comment with ID '${COMMENT_ID}': ${e.message}")
  }

  if (RAW) {
    return json
  }

  def commentBody = json?.body?.storage?.value
  return commentBody
}
