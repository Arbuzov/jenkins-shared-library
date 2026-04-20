/**
 * Post a commit status (pipeline check) to GitLab via the REST API.
 *
 * Produces a separate named status on the target commit, visible as a check
 * next to any other pipelines on the MR / commit in GitLab. To target a
 * project different from the current one (e.g. the upstream project that
 * triggered this build), pass an explicit `gitUrl` (used for project-path
 * derivation) or a numeric `projectId`.
 *
 * Args:
 *  - state: 'pending', 'running', 'success', 'failed', 'canceled', 'skipped' (required)
 *  - name: status context name shown in GitLab (default: env.JOB_NAME)
 *  - sha: commit SHA (default: env.GITLAB_COMMIT_SHA, env.gitlabMergeRequestLastCommit, env.GIT_COMMIT)
 *  - projectId: numeric GitLab project ID; takes precedence over gitUrl (default: env.GITLAB_PROJECT_ID, else resolved via API)
 *  - gitUrl: git URL used to derive the project path for API lookup (default: env.GIT_URL)
 *  - mergeRequestIid: when the resolved projectId is the target project of an MR
 *                     from a fork, commits only exist in the source project, so
 *                     GitLab rejects /projects/:target/statuses/:sha. If this
 *                     arg is set, the helper looks up the MR on projectId and
 *                     replaces projectId with MR.source_project_id. Commit SHA
 *                     is left as-is (caller is expected to pass the source head).
 *  - targetUrl: URL to link the check to (default: env.BUILD_URL)
 *  - description: short description shown in GitLab (optional)
 *  - ref: branch or tag name (optional)
 *  - coverage: coverage percentage (optional)
 *  - gitlabBaseUrl: GitLab base URL without /api/v4 (default: derived from gitUrl/env)
 *  - gitlabCredentialsId: Jenkins credentials ID with GitLab token (default: 'gitlab-text-secret')
 *  - debug: log request/response details to the build console (default: false)
 */
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def state = args.state
  if (!state) {
    throw new Exception("gitlabSetCommitStatus(): 'state' is required.")
  }
  def allowedStates = ['pending', 'running', 'success', 'failed', 'canceled', 'skipped']
  if (!(state in allowedStates)) {
    throw new Exception("gitlabSetCommitStatus(): Unsupported state '${state}'. Allowed: ${allowedStates.join(', ')}.")
  }

  def sha = args.sha ?: env.GITLAB_COMMIT_SHA ?: env.gitlabMergeRequestLastCommit ?: env.GIT_COMMIT
  if (!sha) {
    throw new Exception("gitlabSetCommitStatus(): Must provide sha or call with commit context (GITLAB_COMMIT_SHA / gitlabMergeRequestLastCommit / GIT_COMMIT).")
  }

  def effectiveGitUrl = args.gitUrl ?: env.GIT_URL
  def name = args.name ?: env.JOB_NAME
  def targetUrl = args.targetUrl ?: env.BUILD_URL
  def gitlabCredentialsId = args.gitlabCredentialsId ?: 'gitlab-text-secret'
  def debug = args.debug ?: false

  String gitlabBaseUrl = args.gitlabBaseUrl ?: gitlabBaseUrl(effectiveGitUrl)
  if (gitlabBaseUrl == null) {
    throw new Exception("gitlabSetCommitStatus(): Unable to determine GitLab base URL.")
  }
  gitlabBaseUrl = gitlabBaseUrl + "/api/v4"

  def projectId = args.projectId ?: env.GITLAB_PROJECT_ID
  def projectPath = gitlabProjectNameEscaped(effectiveGitUrl)
  if (projectId == null && projectPath) {
    withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
      def response = httpRequest(
        url: "${gitlabBaseUrl}/projects/${projectPath}",
        customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
        acceptType: 'APPLICATION_JSON',
        quiet: true,
        consoleLogResponseBody: false,
      )
      def project = readJSON(text: response?.getContent())
      projectId = project?.id
    }
  }
  if (projectId == null) {
    throw new Exception("gitlabSetCommitStatus(): Unable to determine project ID (sha=${sha}, gitUrl=${effectiveGitUrl}).")
  }
  if (!projectId.toString().matches(/^\d+$/)) {
    throw new Exception("gitlabSetCommitStatus(): projectId must be numeric. Got '${projectId}'.")
  }

  if (args.mergeRequestIid) {
    withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
      def mrResponse = httpRequest(
        url: "${gitlabBaseUrl}/projects/${projectId}/merge_requests/${args.mergeRequestIid}",
        customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
        acceptType: 'APPLICATION_JSON',
        quiet: !debug,
        consoleLogResponseBody: debug,
      )
      def mr = readJSON(text: mrResponse?.getContent())
      if (mr?.source_project_id) {
        if (debug) echo "[gitlabSetCommitStatus] MR !${args.mergeRequestIid}: source_project_id=${mr.source_project_id} (target=${projectId})"
        projectId = mr.source_project_id
      }
    }
  }

  def query = [state: state]
  if (name) query.name = name
  if (targetUrl) query.target_url = targetUrl
  if (args.description) query.description = args.description
  if (args.ref) query.ref = args.ref
  if (args.coverage != null) query.coverage = args.coverage
  def queryString = query.collect { k, v -> "${k}=${URLEncoder.encode(v.toString(), 'UTF-8')}" }.join('&')

  def url = "${gitlabBaseUrl}/projects/${projectId}/statuses/${sha}?${queryString}"
  if (debug) {
    echo "[gitlabSetCommitStatus] POST ${url}"
  }
  withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
    def response = httpRequest(
      url: url,
      httpMode: 'POST',
      customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
      acceptType: 'APPLICATION_JSON',
      validResponseCodes: '100:399',
      quiet: !debug,
      consoleLogResponseBody: debug,
    )
    return readJSON(text: response?.getContent())
  }
}

/**
 * Derive a GitLab base URL (scheme + host) from the given git URL, or from
 * env.GITLAB_PROJECT_HTTP_URL / env.GIT_URL as fallbacks.
 */
def gitlabBaseUrl(String sourceGitUrl = null) {
  try {
    URL url
    def candidate = sourceGitUrl ?: env.GITLAB_PROJECT_HTTP_URL ?: env.GIT_URL
    if (!candidate) return 'https://gitlab.asatnet.net'
    def gitUrl = candidate.trim()
    if (gitUrl.startsWith('http')) {
      url = new URL(gitUrl)
    } else if (gitUrl.startsWith('git@')) {
      def hostAndPath = gitUrl.substring(4).replace(':', '/')
      url = new URL("https://${hostAndPath}")
    } else {
      return 'https://gitlab.asatnet.net'
    }
    return "https://" + url.getHost()
  } catch (Exception e) {
    return 'https://gitlab.asatnet.net'
  }
}

/**
 * Extract the URL-encoded `namespace/path` of a git URL (ssh or http form),
 * suitable for use in GitLab REST API `projects/:id` routes.
 */
def gitlabProjectNameEscaped(String sourceGitUrl = null) {
  def candidate = sourceGitUrl ?: env.GIT_URL
  if (!candidate) return null
  def gitUrl = candidate.trim()
  def path
  if (gitUrl.startsWith('git@') && gitUrl.contains(':')) {
    path = gitUrl.substring(gitUrl.indexOf(':') + 1)
  } else if (gitUrl.startsWith('http')) {
    try {
      path = new URL(gitUrl).getPath()?.replaceFirst('^/', '')
    } catch (Exception e) {
      return null
    }
  } else {
    return null
  }
  if (!path) return null
  path = path - '.git'
  return path.replaceAll('/', '%2F')
}
