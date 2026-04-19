import java.net.URI
import java.net.URLEncoder

def resolveConfluenceBaseUrl(String confluenceApi) {
    try {
        def uri = new URI(confluenceApi)
        String path = uri.path ?: ''
        int apiIndex = path.indexOf('/rest/api')
        String basePath = apiIndex >= 0 ? path.substring(0, apiIndex) : path
        return "${uri.scheme}://${uri.authority}${basePath}".replaceAll('/+$', '')
    } catch (Exception ignored) {
        return confluenceApi.replaceAll('/rest/api/?$', '').replaceAll('/+$', '')
    }
}

def resolvePageId(String confluenceApi, String pageId, String pageName, String space, String credentialsId, Boolean debug) {
    if (pageId) {
        return pageId
    }

    if (!pageName) {
        error('You must supply PAGE_ID or PAGE_NAME')
    }
    if (!space) {
        error('You must supply SPACE when PAGE_NAME is used')
    }

    if (debug) echo "downloadConfluenceAttachments(): Resolving PAGE_ID for title '${pageName}' in space '${space}'"
    def response = httpRequest(
        url: "${confluenceApi}/content?spaceKey=${space}&title=${URLEncoder.encode(pageName, 'UTF-8')}",
        httpMode: 'GET',
        authentication: credentialsId,
        consoleLogResponseBody: debug,
        quiet: !debug
    )
    def results = readJSON(text: response.content).results
    if (!results || results.isEmpty()) {
        error("No page found with title '${pageName}' in space '${space}'")
    }
    if (results.size() > 1) {
        error("Multiple pages found with title '${pageName}'. Supply PAGE_ID explicitly.")
    }
    return results[0].id as String
}

def normalizeAttachmentsSpec(def attachmentsSpec) {
    if (attachmentsSpec == null) {
        return null
    }

    if (attachmentsSpec instanceof String) {
        return [(attachmentsSpec): attachmentsSpec]
    }

    if (attachmentsSpec instanceof Map) {
        def normalized = [:]
        attachmentsSpec.each { sourceName, saveAsName ->
            if (!sourceName) {
                error('ATTACHMENTS map keys must be non-empty')
            }
            String source = sourceName.toString()
            String target = saveAsName ? saveAsName.toString() : source
            normalized[source] = target
        }
        return normalized
    }

    if (attachmentsSpec instanceof List) {
        def normalized = [:]
        attachmentsSpec.each { item ->
            if (item instanceof String) {
                normalized[item] = item
            } else if (item instanceof Map) {
                String source = (item.title ?: item.TITLE ?: item.name ?: item.NAME ?: '').toString()
                String target = (item.saveAs ?: item.SAVE_AS ?: item.as ?: item.AS ?: source).toString()
                if (!source) {
                    error("ATTACHMENTS list map items must include one of: title, TITLE, name, NAME. Got: ${item}")
                }
                normalized[source] = target
            } else {
                error("ATTACHMENTS list supports only String or Map values, got: ${item?.getClass()?.simpleName}")
            }
        }
        return normalized
    }

    error("ATTACHMENTS must be String, List, or Map. Got: ${attachmentsSpec.getClass().simpleName}")
}

def listPageAttachments(String confluenceApi, String pageId, String credentialsId, Boolean debug) {
    def attachments = []
    int start = 0
    int limit = 200

    while (true) {
        def response = httpRequest(
            url: "${confluenceApi}/content/${pageId}/child/attachment?start=${start}&limit=${limit}",
            httpMode: 'GET',
            authentication: credentialsId,
            acceptType: 'APPLICATION_JSON',
            consoleLogResponseBody: debug,
            quiet: !debug
        )
        def json = readJSON(text: response.content)
        def chunk = json.results ?: []
        attachments.addAll(chunk)

        if (!(json._links?.next) || chunk.isEmpty()) {
            break
        }
        start += limit
    }

    return attachments
}

def call(Map arg = [:]) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)

    String confluenceApi = arg.CONFLUENCE_API ?: 'https://confluence.example.com/rest/api'
    String credentialsId = arg.CREDENTIALS_ID ?: 'jira-username-password'
    String pageId = arg.PAGE_ID ?: ''
    String pageName = arg.PAGE_NAME ?: ''
    String space = arg.SPACE ?: ''
    String savePath = arg.SAVE_PATH ?: '.'
    Boolean allowMissing = arg.containsKey('ALLOW_MISSING') ? (arg.ALLOW_MISSING as Boolean) : false
    Boolean overwrite = arg.containsKey('OVERWRITE') ? (arg.OVERWRITE as Boolean) : true
    Boolean debug = arg.containsKey('DEBUG') ? (arg.DEBUG as Boolean) : false

    def requestedAttachments = normalizeAttachmentsSpec(arg.ATTACHMENTS)
    pageId = resolvePageId(confluenceApi, pageId, pageName, space, credentialsId, debug)

    def saveDir = new File(savePath)
    if (!saveDir.exists() && !saveDir.mkdirs()) {
        error("downloadConfluenceAttachments(): Failed to create SAVE_PATH '${savePath}'")
    }
    if (!saveDir.isDirectory()) {
        error("downloadConfluenceAttachments(): SAVE_PATH '${savePath}' is not a directory")
    }

    def availableAttachments = listPageAttachments(confluenceApi, pageId, credentialsId, debug)
    if (debug) echo "downloadConfluenceAttachments(): Found ${availableAttachments.size()} attachment(s) on page ${pageId}"

    def byTitle = [:]
    availableAttachments.each { item ->
        byTitle[item.title as String] = item
    }

    def targets = []
    def missing = []
    if (requestedAttachments == null) {
        availableAttachments.each { item ->
            targets.add([source: item.title as String, target: item.title as String, meta: item])
        }
    } else {
        requestedAttachments.each { sourceName, targetName ->
            def meta = byTitle[sourceName as String]
            if (!meta) {
                missing << (sourceName as String)
            } else {
                targets.add([source: sourceName as String, target: targetName as String, meta: meta])
            }
        }
    }

    if (!missing.isEmpty()) {
        String message = "downloadConfluenceAttachments(): Requested attachment(s) not found: ${missing.join(', ')}"
        if (allowMissing) {
            echo message
        } else {
            error(message)
        }
    }

    String baseUrl = resolveConfluenceBaseUrl(confluenceApi)
    def downloaded = []
    def skipped = []

    targets.each { item ->
        String downloadPath = item.meta?._links?.download as String
        if (!downloadPath) {
            error("downloadConfluenceAttachments(): Attachment '${item.source}' has no download link")
        }

        String sourceUrl = downloadPath.startsWith('http') ? downloadPath :
            (downloadPath.startsWith('/') ? "${baseUrl}${downloadPath}" : "${baseUrl}/${downloadPath}")

        def destinationFile = new File(saveDir, item.target as String)
        def parentDir = destinationFile.parentFile
        if (parentDir && !parentDir.exists() && !parentDir.mkdirs()) {
            error("downloadConfluenceAttachments(): Failed to create directory '${parentDir.path}'")
        }

        if (!overwrite && destinationFile.exists()) {
            skipped << [source: item.source, path: destinationFile.path, reason: 'already exists']
            if (debug) echo "downloadConfluenceAttachments(): Skip '${item.source}' -> '${destinationFile.path}' (exists)"
        } else {
            httpRequest(
                url: sourceUrl,
                httpMode: 'GET',
                authentication: credentialsId,
                outputFile: destinationFile.path,
                consoleLogResponseBody: false,
                quiet: !debug
            )
            downloaded << [source: item.source, path: destinationFile.path, url: sourceUrl]
            if (debug) echo "downloadConfluenceAttachments(): Downloaded '${item.source}' -> '${destinationFile.path}'"
        }
    }

    return [
        pageId     : pageId,
        savePath   : saveDir.path,
        downloaded : downloaded,
        skipped    : skipped,
        missing    : missing
    ]
}
