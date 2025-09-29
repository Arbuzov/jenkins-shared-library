import java.net.URLDecoder
import java.nio.charset.StandardCharsets

def call(Map arg = [:]) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    
    String url = arg.url
    if (!url) return null
    
    try {
        def uri = new URI(url)
        def query = uri.getRawQuery() ?: uri.getQuery()

        if (!query) {
            return null
        }

        for (String param : query.split(/[&;]/)) {
            if (!param) {
                continue
            }

            def pair = param.split('=', 2)
            def key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name())

            if (key?.equalsIgnoreCase('pageId')) {
                def value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name()) : ''
                return value ?: null
            }
        }
    } catch (Exception e) {
        echo "Failed to parse URL: ${url}, error: ${e.message}"
    }
    return null
}