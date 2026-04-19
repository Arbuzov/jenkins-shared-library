def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
  String SPACE = arg.SPACE ?: 'SPACE'
  String ROOT = arg.ROOT ?: ''
  String ROOT_NAME = arg.ROOT_NAME ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  Boolean DEBUG = arg.DEBUG ?: false
  Boolean FLATTEN = arg.FLATTEN ?: false
  def CHILDREN = arg.CHILDREN ?: []

  // Validate CHILDREN parameter type
  if (CHILDREN instanceof String) {
    error("CHILDREN parameter must be a List, but received a String: '${CHILDREN}'. Please provide an array of child page definitions.")
  }

  if (!(CHILDREN instanceof List)) {
    error("CHILDREN parameter must be a List, but received: ${CHILDREN?.getClass()?.getSimpleName()}. Please provide an array of child page definitions.")
  }

  if (!CHILDREN || CHILDREN.isEmpty()) {
    if (DEBUG) echo "No child pages to create"
    return null
  }
  def lastPageUrl = null
  def fallbackContent = libraryResource('com/example/confluence/children.gtpl')
  def createdCount = 0
  def rootPageId = ROOT
  def rootPageName = ROOT_NAME

  for (child in CHILDREN) {
    def pageTitle = child.title ?: 'New Child Page'
    def pageContent = child.content ?: fallbackContent
    def pageLabel = child.label ?: ''

    if (DEBUG) echo "Creating child page: ${pageTitle}"

    try {
      def pageUrl = createConfluencePage([
        CONFLUENCE_API: CONFLUENCE_API,
        TITLE: pageTitle,
        SPACE: SPACE,
        ROOT: rootPageId,
        ROOT_NAME: rootPageName,
        CONTENT: pageContent,
        CREDENTIALS_ID: CREDENTIALS_ID,
        DEBUG: DEBUG,
        LABEL: pageLabel
      ])

      lastPageUrl = pageUrl
      if (!pageUrl) {
        error "Failed to create confluence page '${pageTitle}': No URL returned from createConfluencePage"
      }
      if (!FLATTEN) {
        rootPageId = getConfluencePageId(url: pageUrl)
        if (!rootPageId) {
          error "Failed to extract page ID from URL '${pageUrl}'"
        }
        rootPageName = '' // Clear root name the rootPageId is enough to create child pages
      }
      createdCount++
      
      if (DEBUG) echo "Successfully created page: ${pageTitle} -> ${pageUrl}"
      
    } catch (Exception e) {
      def errorMsg = "Failed to create child page '${pageTitle}': ${e.message}"
      echo errorMsg
      if (DEBUG) {
        echo "Stack trace: ${e}"
      }
      error(errorMsg)
    }
  }
  
  if (DEBUG) echo "Created ${createdCount} child pages out of ${CHILDREN.size()} requested"
  return lastPageUrl
}