/**
 * Read one resource of a GitLab project via the REST API.
 *
 * Wraps an authenticated GET /projects/:id/<path>. It sends one request and does
 * not walk pages: a list endpoint answers with its first page, so ask for a
 * bigger or a later one through 'query' (per_page, page).
 *
 * Args (Map):
 *  - path:           resource path under /api/v4/projects/:id/, without a leading
 *                    slash and without a query string; its segments are already
 *                    encoded by the caller, e.g. 'repository/files/' + encoded
 *                    file path (required)
 *  - query:          query parameters, encoded and sent in the Map's order
 *                    (default: none)
 *  - gitUrl:         SSH or HTTPS repo URL (default: env.GIT_URL)
 *  - projectId:      GitLab numeric project ID, or the project path in either
 *                    form - 'group/project' is encoded here, 'group%2Fproject'
 *                    is passed through (default: derived from gitUrl)
 *  - gitlabBaseUrl:  GitLab base URL without /api/v4 (default: derived from gitUrl)
 *  - credentialsId:  Jenkins credentials ID with GitLab token
 *                    (default: 'gitlab-text-secret')
 *  - debug:          log the request URL and the response body (default: false)
 *
 * Returns the parsed JSON body as plain objects - a Map or a List, as the
 * endpoint answers.
 *
 * Fails through error(...) when 'path' is missing, when a query value is null,
 * when no project can be addressed, when the credentials hold no token and
 * when the API answers >= 400.
 */
/**
 * Render a project reference for the /projects/:id path segment. A numeric ID and
 * an already-encoded path are passed through; a plain 'group/project' is encoded,
 * so both spellings address the same project.
 */
def encodeProjectReference(Object projectId) {
  String reference = projectId.toString()
  return reference.contains('/') ? URLEncoder.encode(reference, 'UTF-8') : reference
}

@NonCPS
String queryString(Map query) {
  return query.collect { key, value ->
    URLEncoder.encode(key.toString(), 'UTF-8') + '=' + URLEncoder.encode(value.toString(), 'UTF-8')
  }.join('&')
}

def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (!args.path) {
    error("gitlabApiGet(): 'path' is required - the resource under /api/v4/projects/:id/, e.g. 'repository/branches'.")
  }
  String gitUrl = args.gitUrl ?: env.GIT_URL
  if (!gitUrl && !args.projectId) {
    error("gitlabApiGet(): provide 'gitUrl' or 'projectId' (env.GIT_URL is unset).")
  }

  String credentialsId = args.credentialsId ?: 'gitlab-text-secret'
  Boolean debug = args.debug ?: false

  String base
  String projectRef
  if (gitUrl) {
    // The scp-style form 'git@host:group/repo.git' has no scheme, so it is the one
    // that needs rewriting. Matching ':' by what follows it would also rewrite the
    // ':' of a port.
    String normalized = gitUrl.trim()
    String lowerCased = normalized.toLowerCase()
    // Only an http(s) clone URL carries the port the API answers on. The port of
    // an ssh:// URL belongs to the SSH daemon, so it is dropped rather than
    // carried into an https request.
    boolean portIsTheApiPort = lowerCased.startsWith('http://') || lowerCased.startsWith('https://')
    if (lowerCased.startsWith('ssh://')) {
      normalized = 'https://' + normalized.substring(6)
    } else if (!normalized.contains('://')) {
      normalized = 'https://' + normalized.replaceFirst(/:/, '/')
    }
    URL url = new URL(normalized)
    // Host and port explicitly, never getAuthority(): the user info of
    // 'git@host' or of an https URL carrying a token would otherwise end up in
    // every request URL, and in the build log whenever debug is on.
    String origin = (portIsTheApiPort && url.getPort() > 0) ?
      "${url.getProtocol()}://${url.getHost()}:${url.getPort()}" :
      "${url.getProtocol()}://${url.getHost()}"
    base = args.gitlabBaseUrl ?: origin
    if (args.projectId) {
      projectRef = encodeProjectReference(args.projectId)
    } else {
      String projectPath = url.getPath().substring(1).replaceAll(/\.git$/, '')
      projectRef = URLEncoder.encode(projectPath, 'UTF-8')
    }
  } else {
    if (!args.gitlabBaseUrl) {
      error("gitlabApiGet(): 'gitlabBaseUrl' is required when 'projectId' is given without 'gitUrl'.")
    }
    base = args.gitlabBaseUrl
    projectRef = encodeProjectReference(args.projectId)
  }

  String requestUrl = "${base}/api/v4/projects/${projectRef}/${args.path}"
  if (args.query) {
    def keysWithoutValue = args.query.keySet().findAll { args.query[it] == null }
    if (keysWithoutValue) {
      error("gitlabApiGet(): 'query' has no value for ${keysWithoutValue.join(', ')}.")
    }
    requestUrl += '?' + queryString(args.query as Map)
  }

  def result = null
  withCredentials([string(credentialsId: credentialsId, variable: 'gitLabToken')]) {
    if (gitLabToken == null) {
      error("gitlabApiGet(): GitLab token from credentials '${credentialsId}' is undefined.")
    }
    if (debug) {
      echo("[gitlabApiGet] GET ${requestUrl}")
    }
    def response = httpRequest(
      url: requestUrl,
      httpMode: 'GET',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabToken]],
      validResponseCodes: '100:599',
      consoleLogResponseBody: debug,
      quiet: !debug,
    )
    int status = (response?.status ?: 0) as Integer
    if (status >= 400) {
      error("gitlabApiGet(): GET ${requestUrl} answered ${status}. " +
            "Check the project, the path and the token of credentials '${credentialsId}'.")
    }
    result = readJSON(text: response?.content, returnPojo: true)
  }
  return result
}
