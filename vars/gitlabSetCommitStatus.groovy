/**
 * Post a commit status (pipeline check) to GitLab via the REST API.
 *
 * Produces a separate named status on the target commit, visible as a check
 * next to any other pipelines on the MR / commit in GitLab.
 *
 * Args:
 *  - state: 'pending', 'running', 'success', 'failed', 'canceled', 'skipped' (required)
 *  - name: status context name shown in GitLab (default: env.JOB_NAME)
 *  - sha: commit SHA (default: env.GITLAB_COMMIT_SHA, env.gitlabMergeRequestLastCommit, env.GIT_COMMIT)
 *  - projectId: GitLab project ID (default: env.GITLAB_PROJECT_ID, else resolved via API)
 *  - targetUrl: URL to link the check to (default: env.BUILD_URL)
 *  - description: short description shown in GitLab (optional)
 *  - ref: branch or tag name (optional)
 *  - coverage: coverage percentage (optional)
 *  - gitlabBaseUrl: GitLab base URL without /api/v4 (default: derived from env)
 *  - gitlabCredentialsId: Jenkins credentials ID with GitLab token (default: 'gitlab-text-secret')
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

  def name = args.name ?: env.JOB_NAME
  def targetUrl = args.targetUrl ?: env.BUILD_URL
  def gitlabCredentialsId = args.gitlabCredentialsId ?: 'gitlab-text-secret'

  String gitlabBaseUrl = args.gitlabBaseUrl ?: gitlabBaseUrl()
  if (gitlabBaseUrl == null) {
    throw new Exception("gitlabSetCommitStatus(): Unable to determine GitLab base URL.")
  }
  gitlabBaseUrl = gitlabBaseUrl + "/api/v4"

  def projectId = args.projectId ?: env.GITLAB_PROJECT_ID
  if (projectId == null && gitlabProjectNameEscaped()) {
    withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
      def response = httpRequest(
        url: "${gitlabBaseUrl}/projects/${gitlabProjectNameEscaped()}",
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
    throw new Exception("gitlabSetCommitStatus(): Unable to determine project ID.")
  }
  if (!projectId.toString().matches(/^\d+$/)) {
    throw new Exception("gitlabSetCommitStatus(): projectId must be numeric. Got '${projectId}'.")
  }

  def query = [state: state]
  if (name) query.name = name
  if (targetUrl) query.target_url = targetUrl
  if (args.description) query.description = args.description
  if (args.ref) query.ref = args.ref
  if (args.coverage != null) query.coverage = args.coverage
  def queryString = query.collect { k, v -> "${k}=${URLEncoder.encode(v.toString(), 'UTF-8')}" }.join('&')

  withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
    def url = "${gitlabBaseUrl}/projects/${projectId}/statuses/${sha}?${queryString}"
    def response = httpRequest(
      url: url,
      httpMode: 'POST',
      customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
      acceptType: 'APPLICATION_JSON',
      validResponseCodes: '100:399',
      quiet: true,
      consoleLogResponseBody: false,
    )
    return readJSON(text: response?.getContent())
  }
}

/**
 * Get GitLab base URL from GITLAB_PROJECT_HTTP_URL or GIT_URL environment variables.
 */
def gitlabBaseUrl() {
  try {
    URL url
    if (env.GITLAB_PROJECT_HTTP_URL) {
      url = new URL(env.GITLAB_PROJECT_HTTP_URL)
    } else if (env.GIT_URL) {
      def gitUrl = env.GIT_URL?.trim()
      if (gitUrl?.startsWith('http')) {
        url = new URL(gitUrl)
      } else if (gitUrl?.startsWith('git@')) {
        def hostAndPath = gitUrl.substring(4).replace(':', '/')
        url = new URL("https://${hostAndPath}")
      } else {
        return 'https://gitlab.example.com'
      }
    } else {
      return 'https://gitlab.example.com'
    }
    return "https://" + url.getHost()
  } catch (Exception e) {
    return 'https://gitlab.example.com'
  }
}

def gitlabProjectNameEscaped() {
  if (!env.GIT_URL || !env.GIT_URL.startsWith('git@') || !env.GIT_URL.contains(':')) {
    return null
  }
  def gitUrl = env.GIT_URL?.trim()
  def ssh = gitUrl - '.git'
  return ssh.split(':')[1].replaceAll('/', '%2F')
}
