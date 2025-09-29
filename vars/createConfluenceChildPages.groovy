def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
  String CONFLUENCE_API = arg.CONFLUENCE_API ?: 'https://confluence.spacebridge.com/rest/api'
  String SPACE = arg.SPACE ?: 'SPACE'
  String ROOT = arg.ROOT ?: ''
  String ROOT_NAME = arg.ROOT_NAME ?: ''
  String CREDENTIALS_ID = arg.CREDENTIALS_ID ?: 'jira-username-password'
  Boolean DEBUG = arg.DEBUG ?: false
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
    }  def lastPageUrl = null
  def fallbackContent = libraryResource('com/spacebridge/confluence/children.gtpl')
  def createdCount = 0
  
  for (child in CHILDREN) {
    def pageTitle = child.title ?: 'New Child Page'
    def pageContent = child.content ?: fallbackContent
    def pageLabel = child.label ?: ''
    
    if (DEBUG) echo "Creating child page: ${pageTitle}"
    
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
  }
  
  if (DEBUG) echo "Created ${createdCount} child pages out of ${CHILDREN.size()} requested"
  return lastPageUrl
}