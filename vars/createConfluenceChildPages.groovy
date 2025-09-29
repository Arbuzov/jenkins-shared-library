def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.spacebridge.com/rest/api'
  String SPACE = arg.SPACE ?: 'SPACE'
  String ROOT = arg.ROOT ?: ''
  String ROOT_NAME = arg.ROOT_NAME ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  Boolean DEBUG = arg.DEBUG ?: false
  def CHILDREN = arg.CHILDREN ?: []
  
  if (!CHILDREN || CHILDREN.isEmpty()) {
    if (DEBUG) echo "No child pages to create"
    return []
  }
  
  def lastPageUrl = null
  def fallbackContent = libraryResource('com/spacebridge/confluence/children.gtpl')
  def createdCount = 0
  
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
        ROOT: ROOT,
        ROOT_NAME: ROOT_NAME,
        CONTENT: pageContent,
        CREDENTIALS_ID: CREDENTIALS_ID,
        DEBUG: DEBUG,
        LABEL: pageLabel
      ])
      
      lastPageUrl = pageUrl
      createdCount++
      
      if (DEBUG) echo "Successfully created page: ${pageTitle} -> ${pageUrl}"
      
    } catch (Exception e) {
      echo "Failed to create child page '${pageTitle}': ${e.message}"
      if (DEBUG) {
        echo "Stack trace: ${e}"
      }
    }
  }
  
  if (DEBUG) echo "Created ${createdCount} child pages out of ${CHILDREN.size()} requested"
  return lastPageUrl
}