/**
 * List the files and directories of a GitLab repository via the REST API.
 *
 * Wraps GET /projects/:id/repository/tree and walks every page, so a tree
 * larger than one page is returned whole instead of silently cut at the page
 * size. Each entry carries GitLab's own shape:
 * [id: <blob/tree sha>, name: ..., type: 'blob'|'tree', path: ..., mode: ...].
 *
 * Args (Map):
 *  - path:           subdirectory to list, repo-relative (default: repository root)
 *  - ref:            branch, tag or commit SHA (default: the project's default branch)
 *  - recursive:      descend into subdirectories (default: false)
 *  - gitUrl:         SSH or HTTPS repo URL (default: env.GIT_URL)
 *  - projectId:      GitLab numeric project ID, or the project path in either
 *                    form - 'group/project' is encoded here, 'group%2Fproject'
 *                    is passed through (default: derived from gitUrl)
 *  - gitlabBaseUrl:  GitLab base URL without /api/v4 (default: derived from gitUrl)
 *  - credentialsId:  Jenkins credentials ID with GitLab token
 *                    (default: 'gitlab-text-secret')
 *  - pageSize:       entries requested per API call, 1..100 (default: 100)
 *  - maxPages:       page walk limit: at most pageSize * maxPages entries are
 *                    returned, which also bounds the walk at maxPages + 1
 *                    requests (default: 100)
 *  - debug:          log request URLs and response bodies (default: false)
 *
 * Returns List<Map> - every tree entry, in GitLab's order.
 *
 * Fails through error(...) when the API answers >= 400 (a missing ref or path
 * answers 404) and when the tree is longer than pageSize * maxPages entries.
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

def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  String gitUrl = args.gitUrl ?: env.GIT_URL
  if (!gitUrl && !args.projectId) {
    error("gitlabGetRepositoryTree(): provide 'gitUrl' or 'projectId' (env.GIT_URL is unset).")
  }

  String credentialsId = args.credentialsId ?: 'gitlab-text-secret'
  Boolean recursive = args.recursive ?: false
  Boolean debug = args.debug ?: false

  int pageSize = (args.pageSize != null ? args.pageSize : 100) as Integer
  if (pageSize < 1 || pageSize > 100) {
    error("gitlabGetRepositoryTree(): 'pageSize' must be between 1 and 100. Got ${pageSize}.")
  }
  int maxPages = (args.maxPages != null ? args.maxPages : 100) as Integer
  if (maxPages < 1) {
    error("gitlabGetRepositoryTree(): 'maxPages' must be >= 1. Got ${maxPages}.")
  }

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
      error("gitlabGetRepositoryTree(): 'gitlabBaseUrl' is required when 'projectId' is given without 'gitUrl'.")
    }
    base = args.gitlabBaseUrl
    projectRef = encodeProjectReference(args.projectId)
  }

  String query = "per_page=${pageSize}&recursive=${recursive}"
  if (args.path) {
    query += "&path=" + URLEncoder.encode(args.path.toString(), 'UTF-8')
  }
  if (args.ref) {
    query += "&ref=" + URLEncoder.encode(args.ref.toString(), 'UTF-8')
  }
  String apiBase = "${base}/api/v4/projects/${projectRef}/repository/tree"

  def entries = []
  withCredentials([string(credentialsId: credentialsId, variable: 'gitLabToken')]) {
    if (gitLabToken == null) {
      error("gitlabGetRepositoryTree(): GitLab token from credentials '${credentialsId}' is undefined.")
    }
    int page = 1
    boolean lastPageSeen = false
    int entryLimit = pageSize * maxPages
    while (!lastPageSeen) {
      String pageUrl = "${apiBase}?${query}&page=${page}"
      if (debug) {
        echo "[gitlabGetRepositoryTree] GET ${pageUrl}"
      }
      def response = httpRequest(
        url: pageUrl,
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
        error("gitlabGetRepositoryTree(): GET ${pageUrl} answered ${status}. " +
              "Check the project, the ref and the token of credentials '${credentialsId}'.")
      }
      def batch = readJSON(text: response?.content ?: '[]')
      if (!batch || batch.size() < pageSize) {
        lastPageSeen = true
      }
      if (batch) {
        if (entries.size() + batch.size() > entryLimit) {
          error("gitlabGetRepositoryTree(): more than ${entryLimit} entries at " +
                "'${args.path ?: '/'}'. Narrow it with 'path', or raise 'maxPages'.")
        }
        entries.addAll(batch)
      }
      page++
    }
  }
  return entries
}
