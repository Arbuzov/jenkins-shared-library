/**
 * Approve or unapprove a merge request in GitLab.
 *
 * Args:
 *  - action: 'approve' or 'unapprove' (default: 'approve')
 *  - approve: Boolean (if false -> unapprove, overrides action)
 *  - mrIid: merge request IID (default: env.CHANGE_ID)
 *  - projectId: GitLab project ID (default: env.GITLAB_PROJECT_ID)
 *  - gitlabBaseUrl: GitLab base URL without /api/v4 (default: derived from env)
 *  - gitlabCredentialsId: Jenkins credentials ID with GitLab token (default: 'gitlab-text-secret')
 */
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def approveArg = args.containsKey('approve') ? args.approve : null
  def action = args.action ?: (approveArg == false ? 'unapprove' : 'approve')
  if (!(action in ['approve', 'unapprove'])) {
    throw new Exception("gitlabSetMrApproval(): Unsupported action '${action}'. Use 'approve' or 'unapprove'.")
  }

  def gitlabCredentialsId = args.gitlabCredentialsId ?: 'gitlab-text-secret'
  def mrIid = args.mrIid ?: env.CHANGE_ID
  if (!mrIid) {
    throw new Exception("gitlabSetMrApproval(): Must provide mrIid or call within a MR context (CHANGE_ID).")
  }

  String gitlabBaseUrl = args.gitlabBaseUrl ?: gitlabBaseUrl()
  if (gitlabBaseUrl == null) {
    throw new Exception("gitlabSetMrApproval(): Unable to determine GitLab base URL.")
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
    throw new Exception("gitlabSetMrApproval(): Unable to determine project ID.")
  }
  if (!projectId.toString().matches(/^\d+$/)) {
    throw new Exception("gitlabSetMrApproval(): projectId must be numeric. Got '${projectId}'.")
  }

  withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
    def url = "${gitlabBaseUrl}/projects/${projectId}/merge_requests/${mrIid}/${action}"
    def validCodes = action == 'unapprove' ? '100:399,404' : '100:399'
    def response = httpRequest(
      url: url,
      httpMode: 'POST',
      customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
      acceptType: 'APPLICATION_JSON',
      validResponseCodes: validCodes,
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
