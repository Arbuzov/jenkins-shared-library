/**
 * Get the content of a Confluence page
 *
 * Example:
 * def ret = getConfluencePageContent(PAGE_ID: '1234567890')
 * echo ret.title
 * echo ret.content
 *
 * @param CONFLUENCE_API The API URL of the Confluence instance
 * @param CREDENTIALS_ID The credentials ID to use for the API
 * @param PAGE_ID The ID of the page to get the content of
 * @param DEBUG Whether to enable debug logging
 * @return A map with the title and content of the page
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  String PAGE_ID = arg.PAGE_ID ?: null
  Boolean DEBUG = arg.DEBUG ?: false

  if (!PAGE_ID) {
    error('You must supply PAGE_ID')
  }

  if (DEBUG) echo "getConfluencePageContent(): Getting content for page ID: ${PAGE_ID}"

  def jsonResponse = null
  try {
    jsonResponse = httpRequest(
      url: "${CONFLUENCE_API}/content/${PAGE_ID}?expand=body.storage",
      httpMode: 'GET',
      authentication: CREDENTIALS_ID,
      acceptType: 'APPLICATION_JSON',
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception e) {
    error("getConfluencePageContent(): Failed to get page content for page ID '${PAGE_ID}': ${e.message}")
  }

  def json = null
  try {
    json = readJSON(text: jsonResponse.content)
  } catch (Exception e) {
    error("getConfluencePageContent(): Failed to read JSON for page with ID '${PAGE_ID}': ${e.message}")
  }

  def title = json.title
  def content = json.body.storage.value

  if (!title) {
    title = ""
  }
  if (!content) {
    content = ""
  }

  if (DEBUG) echo "getConfluencePageContent(): Page ID: ${PAGE_ID}, Title: ${title}"

  return [title: title, content: content]
}
