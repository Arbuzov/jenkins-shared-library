@NonCPS
def confluenceSortPageChildren(list) {
  list.sort { a, b -> (a.extensions.position ?: 0) <=> (b.extensions.position ?: 0) }
}

/**
 * Get the children of a Confluence page
 *
 * @param CONFLUENCE_API The API URL of the Confluence instance
 * @param CREDENTIALS_ID The credentials ID to use for the API
 * @param PAGE_ID The ID of the page to get the children of
 * @param DEBUG Whether to enable debug logging
 * @return The children of the page
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

  if (DEBUG) echo "getConfluencePageChildren(): Getting children for page ID: ${PAGE_ID}"

  def results = []
  def start = 0
  def limit = 100

  while (true) {
    def jsonResponse = null
    try {
      jsonResponse = httpRequest(
        url: "${CONFLUENCE_API}/content/${PAGE_ID}/child?expand=page&start=${start}&limit=${limit}",
        httpMode: 'GET',
        authentication: CREDENTIALS_ID,
        acceptType: 'APPLICATION_JSON',
        consoleLogResponseBody: DEBUG,
        quiet: !DEBUG
      )
    } catch (Exception e) {
      error("getConfluencePageChildren(): Failed to get children for page with ID '${PAGE_ID}': ${e.message}")
    }

    def pageResults = []
    try {
      pageResults = readJSON(text: jsonResponse.content).page.results ?: []
    } catch (Exception e) {
      error("getConfluencePageChildren(): Failed to read JSON for page with ID '${PAGE_ID}': ${e.message}")
    }
    if (!pageResults) {
      break
    }
    results.addAll(pageResults)
    start += limit
  }

  if (!results || results.isEmpty()) {
    if (DEBUG) echo "getConfluencePageChildren(): No children found for page with ID '${PAGE_ID}'"
    return []
  }

  // Sort by extensions.position
  confluenceSortPageChildren(results)
  // Get the page IDs
  def pageIds = results.collect { it.id }
  return pageIds
}