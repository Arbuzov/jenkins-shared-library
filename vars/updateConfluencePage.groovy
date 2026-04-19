/**
 * Update a Confluence page
 * Example:
 * updateConfluencePage(PAGE_ID: '1234567890', TITLE: 'New Title', CONTENT: 'New Content')
 *
 * @param CONFLUENCE_API The API URL of the Confluence instance
 * @param CREDENTIALS_ID The credentials ID to use for the API
 * @param PAGE_ID The ID of the page to update
 * @param TITLE The new title of the page (optional)
 * @param CONTENT The new content of the page (optional, html)
 * @param DEBUG Whether to enable debug logging
 * @return True if the page was updated successfully, false otherwise
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  String PAGE_ID = arg.PAGE_ID ?: null
  String TITLE = arg.TITLE ?: null
  String CONTENT = arg.CONTENT ?: null
  Boolean DEBUG = arg.DEBUG ?: false

  if (!PAGE_ID) {
    error('You must supply PAGE_ID')
  }

  if (!TITLE && !CONTENT) {
    error('updateConfluencePage(): You must supply TITLE or CONTENT')
  }

  if (DEBUG) echo "updateConfluencePage(): Updating Confluence page ID: ${PAGE_ID}"

  // Get current version Number
  def jsonVersion = null
  try {
    jsonVersion = httpRequest(
      url: "${CONFLUENCE_API}/content/${PAGE_ID}?expand=version",
      httpMode: 'GET',
      authentication: CREDENTIALS_ID,
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception e) {
    error("updateConfluencePage(): Failed to get version number for page ID '${PAGE_ID}': ${e.message}")
  }

  def versionNumber = null
  def prevTitle = null
  try {
    versionNumber = readJSON(text: jsonVersion.content).version.number
    prevTitle = readJSON(text: jsonVersion.content).title
  } catch (Exception e) {
    error("updateConfluencePage(): Failed to read JSON for page with ID '${PAGE_ID}': ${e.message}")
  }

  if (!versionNumber) {
    error("updateConfluencePage(): Failed to get version number for page with ID '${PAGE_ID}'")
  }

  def request = [
    type: 'page',
    version: [
      number: versionNumber + 1
    ],
  ]

  if (TITLE) {
    request.title = TITLE
  } else {
    request.title = prevTitle
  }
  if (CONTENT) {
    request.body = [
      storage: [
        value: CONTENT,
        representation: 'storage'
      ]
    ]
  }

  def jsonBody = groovy.json.JsonOutput.toJson(request)

  try {
    httpRequest(
      url: "${CONFLUENCE_API}/content/${PAGE_ID}",
      httpMode: 'PUT',
      authentication: CREDENTIALS_ID,
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      requestBody: jsonBody,
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
  } catch (Exception e) {
    error("updateConfluencePage(): Failed to update page with ID '${PAGE_ID}': ${e.message}")
  }

  return true
}