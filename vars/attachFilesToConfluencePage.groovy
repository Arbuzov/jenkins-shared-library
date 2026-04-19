def call(Map arg) {
    String CONFLUENCE_API  = arg.CONFLUENCE_API  ?: 'https://confluence.example.com/rest/api'
    String SPACE           = arg.SPACE           ?: ''
    String PAGE_ID         = arg.PAGE_ID        ?: ''
    String PAGE_NAME       = arg.PAGE_NAME      ?: ''
    def    files           = (arg.FILES instanceof List
                                ? arg.FILES
                                : (arg.FILES ? [arg.FILES] : [])).findAll { it }
    String CREDENTIALS_ID  = arg.CREDENTIALS_ID ?: 'jira-username-password'
    Boolean DEBUG          = arg.DEBUG          ?: false

    reportUsage(getClass().protectionDomain.codeSource.location.path)

    if (DEBUG) {
        echo "attachFilesToConfluencePage: DEBUG mode enabled"
    }

    if (!PAGE_ID && PAGE_NAME) {
        if (DEBUG) echo "Looking up Confluence page ID for title '${PAGE_NAME}' in space '${SPACE}'"
        def lookup = httpRequest(
            url: "${CONFLUENCE_API}/content?spaceKey=${SPACE}&title=${URLEncoder.encode(PAGE_NAME, 'UTF-8')}",
            httpMode: 'GET',
            authentication: CREDENTIALS_ID,
            consoleLogResponseBody: DEBUG,
            quiet: !DEBUG
        )
        def results = readJSON(text: lookup.content).results
        if (!results || results.isEmpty()) {
            error("No page found with title '${PAGE_NAME}' in space '${SPACE}'")
        }
        if (results.size() > 1) {
            error("Multiple pages found titled '${PAGE_NAME}'. Please specify PAGE_ID instead.")
        }
        PAGE_ID = results[0].id
        if (DEBUG) echo "Resolved page ID: ${PAGE_ID}"
    }

    files.each { filePath ->
        def fileName = filePath.tokenize('/')[-1]
        try {
            def response = httpRequest(
                httpMode:               'POST',
                url:                    "${CONFLUENCE_API}/content/${PAGE_ID}/child/attachment",
                authentication:         CREDENTIALS_ID,
                customHeaders:          [[name: 'X-Atlassian-Token', value: 'no-check']],
                multipartName:          'file',
                uploadFile:             filePath,
                wrapAsMultipart:        true,
                consoleLogResponseBody: DEBUG,
                quiet:                  !DEBUG,
                validResponseCodes:     '100:499'
            )
            if (response.status == 400 && response.content.contains('same file name')) {
                echo "File '${fileName}' already exists – skipping upload."
            } else if (response.status >= 200 && response.status < 300) {
                echo "Successfully attached '${fileName}' to Confluence page ID ${PAGE_ID}"
            } else {
                error("Failed to attach '${fileName}': status=${response.status}, body=${response.content}")
            }
        } catch (java.io.FileNotFoundException e) {
            echo "File '${fileName}' not found – skipping."
        } catch (Exception e) {
            error("Unexpected error attaching file '${fileName}': ${e.message}")
        }
    }
}
