/**
 * Find assets in Nexus 3 via GET /service/rest/v1/search/assets.
 *
 * Walks every page: while a page carries a continuationToken, the same search
 * is repeated with '&continuationToken=<token>', so a search matching more
 * assets than one page holds is returned whole instead of silently cut.
 *
 * Args (Map):
 *  - query:          required, non-empty Map of Nexus search parameters, sent
 *                    in the Map's order with keys and values URL-encoded, e.g.
 *                    [repository: 'maven-releases', 'maven.artifactId': 'nms-ng']
 *  - nexusUrl:       Nexus base URL (default: 'https://nexus.asatnet.net')
 *  - credentialsId:  Jenkins username/password credentials ID used as the
 *                    httpRequest authentication (default: 'jira-username-password')
 *  - maxPages:       page walk limit, >= 1 (default: 100)
 *  - debug:          log request URLs and response bodies (default: false)
 *
 * Returns List<Map> - the items of every page, in Nexus's order and shape
 * (path, downloadUrl, repository, format, maven2, lastModified, ...);
 * [] when nothing matches.
 *
 * Fails through error(...) when query is missing, empty or has a null value,
 * when maxPages < 1, when Nexus answers >= 400, and when a continuationToken is
 * still present after maxPages pages.
 */
/** Map entries as 'key=value' pairs joined with '&', keys and values URL-encoded. */
@NonCPS
String queryString(Map query) {
  return query.collect { key, value ->
    URLEncoder.encode(key.toString(), 'UTF-8') + '=' + URLEncoder.encode(value.toString(), 'UTF-8')
  }.join('&')
}

def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (!(args.query instanceof Map) || !args.query) {
    error("nexusSearchAssets(): 'query' is required: a non-empty Map of search parameters, " +
          "e.g. [repository: 'maven-releases', 'maven.artifactId': 'nms-ng'].")
  }
  def keysWithoutValue = args.query.keySet().findAll { args.query[it] == null }
  if (keysWithoutValue) {
    error("nexusSearchAssets(): 'query' has no value for ${keysWithoutValue.join(', ')}.")
  }

  String nexusUrl = args.nexusUrl ?: 'https://nexus.asatnet.net'
  String credentialsId = args.credentialsId ?: 'jira-username-password'
  Boolean debug = args.debug ?: false

  int maxPages = (args.maxPages != null ? args.maxPages : 100) as Integer
  if (maxPages < 1) {
    error("nexusSearchAssets(): 'maxPages' must be >= 1. Got ${maxPages}.")
  }

  String firstUrl = "${nexusUrl}/service/rest/v1/search/assets?${queryString(args.query as Map)}"

  def assets = []
  String pageUrl = firstUrl
  int page = 0
  while (pageUrl) {
    if (debug) {
      echo("[nexusSearchAssets] GET ${pageUrl}")
    }
    def response = httpRequest(
      url: pageUrl,
      httpMode: 'GET',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: credentialsId,
      validResponseCodes: '100:599',
      consoleLogResponseBody: debug,
      quiet: !debug,
    )
    int status = (response?.status ?: 0) as Integer
    if (status >= 400) {
      error("nexusSearchAssets(): GET ${pageUrl} answered ${status}. " +
            "Check the query and the credentials '${credentialsId}'.")
    }
    def body = readJSON(text: response?.content, returnPojo: true)
    if (body?.items) {
      assets.addAll(body.items)
    }
    page++
    def continuationToken = body?.continuationToken
    if (continuationToken && page >= maxPages) {
      error("nexusSearchAssets(): more than ${maxPages} pages for ${firstUrl}. " +
            "Narrow the query, or raise 'maxPages'.")
    }
    pageUrl = continuationToken ? firstUrl + '&' + queryString([continuationToken: continuationToken]) : null
  }
  return assets
}
