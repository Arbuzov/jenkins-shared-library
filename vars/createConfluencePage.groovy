String call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.spacebridge.com/rest/api'
  String TITLE = arg.TITLE ?: 'New page'
  String SPACE = arg.SPACE ?: 'SPACE'
  String ROOT = arg.ROOT ?: ''
  String ROOT_NAME = arg.ROOT_NAME ?: ''
  String CONTENT = arg.CONTENT ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  Boolean DEBUG = arg.DEBUG ?: false
  String LABEL = arg.LABEL ?: ''

  if (!ROOT && ROOT_NAME) {
    if (DEBUG) echo "Looking up Confluence page ID for title '${ROOT_NAME}' in space '${SPACE}'"
    def lookup = httpRequest(
      url: "${CONFLUENCE_API}/content?spaceKey=${SPACE}&title=${URLEncoder.encode(ROOT_NAME, 'UTF-8')}",
      httpMode: 'GET',
      authentication: CREDENTIALS_ID,
      consoleLogResponseBody: DEBUG,
      quiet: !DEBUG
    )
    def results = readJSON(text: lookup.content).results
    if (!results || results.isEmpty()) {
      error("No page found with title '${ROOT_NAME}' in space '${SPACE}'")
    }
    if (results.size() > 1) {
      error("Multiple pages found titled '${ROOT_NAME}'. Please specify ROOT (ID) instead.")
    }
    ROOT = results[0].id
    if (DEBUG) echo "Resolved parent page ID: ${ROOT}"
  }

  if (!ROOT) {
    error('You must supply either ROOT (numeric page ID) or ROOT_NAME (page title)')
  }

  if (DEBUG) {
    echo CONTENT
  }

  if (DEBUG) {
    echo "Checking for existing page titled '${TITLE}' under parent ${ROOT}"
  }
  def searchUrl = "${CONFLUENCE_API}/content?spaceKey=${SPACE}&title=${URLEncoder.encode(TITLE,'UTF-8')}&expand=ancestors"
  def lookupNew = httpRequest(
    url: searchUrl,
    httpMode: 'GET',
    authentication: CREDENTIALS_ID,
    consoleLogResponseBody: DEBUG,
    quiet: !DEBUG
  )
  def candidates = readJSON(text: lookupNew.content).results
  def dupes = candidates.findAll { page ->
    page.ancestors.any { it.id.toString() == ROOT.toString() }
  }
  if (dupes) {
    def existingId = dupes[0].id
    if (DEBUG) {
      echo "Page already exists (pageId=${existingId}) – skipping creation."
    }
    return "https://confluence.spacebridge.com/pages/viewpage.action?pageId=${existingId}"
  }

  def postContentUrl = "${CONFLUENCE_API}/content"
  def newReleaseContent = "{\"type\":\"page\",\"title\":\"${TITLE}\",\"ancestors\":[{\"id\":\"${ROOT}\"}],\"space\":{\"key\":\"${SPACE}\"},\"body\":{\"storage\":{\"value\":\"${CONTENT.replaceAll('"','\'')}\",\"representation\":\"storage\"}}}"
  def releaseResponce = httpRequest(
    url: postContentUrl,
    httpMode: 'POST',
    consoleLogResponseBody: DEBUG,
    contentType: 'APPLICATION_JSON',
    authentication: CREDENTIALS_ID,
    requestBody: newReleaseContent,
    quiet: !DEBUG
  )
  if (!LABEL.isEmpty()) {
    labelConfluencePage(
      ID: readJSON(text: releaseResponce.getContent()).id,
      LABEL: LABEL,
      CREDENTIALS_ID: CREDENTIALS_ID,
      DEBUG: DEBUG
    )
  }
  return 'https://confluence.spacebridge.com/pages/viewpage.action?pageId=' + readJSON(text: releaseResponce.getContent()).id
}