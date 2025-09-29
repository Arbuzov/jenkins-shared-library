def call(Map arg = [:]) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    
    String url = arg.url
    if (!url) return null
    
    try {
        def uri = new URI(url)
        def query = uri.getQuery()
        
        if (query) {
            def params = query.split('&').collectEntries { param ->
                def pair = param.split('=', 2)
                [(pair[0]): pair.length > 1 ? pair[1] : '']
            }
            return params.pageId
        }
    } catch (Exception e) {
        echo "Failed to parse URL: ${url}, error: ${e.message}"
    }
    return null
}