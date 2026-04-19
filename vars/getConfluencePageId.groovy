import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private String decodeValue(String value) {
    if (value == null) {
        return null
    }
    return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}

private String extractPageIdFromQuery(String query) {
    if (!query) {
        return null
    }

    for (String param : query.split(/[&;]/)) {
        if (!param) {
            continue
        }

        def pair = param.split('=', 2)
        def key = decodeValue(pair[0])
        if (key?.equalsIgnoreCase('pageId')) {
            def value = pair.length > 1 ? decodeValue(pair[1]) : ''
            return value ?: null
        }
    }

    return null
}

private Map extractDisplayPageInfo(String rawPath) {
    if (!rawPath) {
        return null
    }

    def path = rawPath.replaceAll('/+$', '')
    def segments = path.tokenize('/')
    int displayIndex = segments.findIndexOf { it == 'display' }

    if (displayIndex < 0 || segments.size() <= displayIndex + 2) {
        return null
    }

    String spaceKey = decodeValue(segments[displayIndex + 1])
    String title = decodeValue(segments[displayIndex + 2])

    if (!spaceKey || !title) {
        return null
    }

    return [spaceKey: spaceKey, title: title]
}

private String resolveConfluenceApi(String explicitApi, URI uri) {
    if (explicitApi) {
        return explicitApi
    }

    if (!uri?.scheme || !uri?.authority) {
        return 'https://confluence.example.com/rest/api'
    }

    String rawPath = uri.rawPath ?: uri.path ?: ''
    int displayIndex = rawPath.indexOf('/display/')
    String contextPath = displayIndex > 0 ? rawPath.substring(0, displayIndex).replaceAll('/+$', '') : ''
    String apiPath = contextPath ? "${contextPath}/rest/api" : '/rest/api'
    return "${uri.scheme}://${uri.authority}${apiPath}"
}

private String resolvePageIdByDisplayPath(Map arg, URI uri, String input, String rawPath) {
    def pageInfo = extractDisplayPageInfo(rawPath)
    if (!pageInfo) {
        return null
    }

    String confluenceApi = resolveConfluenceApi(
        (arg.CONFLUENCE_API ?: arg.confluenceApi ?: '').toString(),
        uri
    )
    String credentialsId = (arg.CREDENTIALS_ID ?: arg.credentialsId ?: 'jira-username-password').toString()
    Boolean debug = arg.containsKey('DEBUG') ? (arg.DEBUG as Boolean) : (arg.containsKey('debug') ? (arg.debug as Boolean) : false)

    if (debug) {
        echo "Resolving Confluence page ID by path: space='${pageInfo.spaceKey}', title='${pageInfo.title}', api='${confluenceApi}'"
    }

    def response = httpRequest(
        url: "${confluenceApi}/content?spaceKey=${URLEncoder.encode(pageInfo.spaceKey, 'UTF-8')}&title=${URLEncoder.encode(pageInfo.title, 'UTF-8')}",
        httpMode: 'GET',
        authentication: credentialsId,
        consoleLogResponseBody: debug,
        quiet: !debug
    )

    def results = readJSON(text: response.content)?.results
    if (!results || results.isEmpty()) {
        return null
    }

    if (results.size() > 1 && debug) {
        echo "Multiple pages found for '${input}', using first result id='${results[0].id}'"
    }

    return results[0]?.id?.toString()
}

def call(Map arg = [:]) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    
    String input = (arg.url ?: arg.URL ?: arg.path ?: arg.PATH ?: '').toString()
    if (!input) return null
    
    try {
        def uri = new URI(input)
        def query = uri.getRawQuery() ?: uri.getQuery()
        def pageId = extractPageIdFromQuery(query)
        if (pageId) {
            return pageId
        }

        String rawPath = uri.rawPath ?: uri.path ?: input
        return resolvePageIdByDisplayPath(arg, uri, input, rawPath)
    } catch (Exception e) {
        echo "Failed to parse URL/path: ${input}, error: ${e.message}"
    }
    return null
}

def call(String urlOrPath) {
    return call([url: urlOrPath])
}
