
/**
 * Find MR by merge commit SHA. The function fetches the Merge Requests associated with the merge commit.
 *
 * @param gitlabBaseUrl GitLab base URL
 * @param projectId Project ID
 * @param privateToken GitLab API token
 * @return MR object if found, otherwise null
 */
def gitFindMRbyMergeCommitSha(String gitlabBaseUrl, String projectId, String privateToken){
  // GITLAB_AFTER is set by the GitLab plugin and contains the merge commit SHA
  // If it's not set, it will not be possible to find the original MR
  if (!env.GITLAB_AFTER) {
    return null
  }
  
  def url = "${gitlabBaseUrl}/projects/${projectId}/repository/commits/${env.GITLAB_AFTER}/merge_requests?state=merged"
  try{
    def response = null;
    response = httpRequest(
      url: url,
      customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
      acceptType: 'APPLICATION_JSON',
      quiet: true,
      consoleLogResponseBody: false,
    )
    def mrList = readJSON text: response?.getContent() ?: '[]'
    if (mrList && mrList.size() > 0) {
      return mrList[0]
    }
    return null
  } catch (Exception e) {
    return null
  }
}

/**
 * Find MR by change ID/MR IID
 *
 * @param gitlabBaseUrl GitLab base URL
 * @param projectId Project ID
 * @param privateToken GitLab API token
 * @return MR object if found, otherwise null
 */
def gitFindMRbyChangeID(String gitlabBaseUrl, String projectId, String privateToken) {
  def url = "${gitlabBaseUrl}/projects/${projectId}/merge_requests/${env.CHANGE_ID}"
  try{
    def response = null;
    response = httpRequest(
      url: url,
      customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
      acceptType: 'APPLICATION_JSON',
      quiet: true,
      consoleLogResponseBody: false,
    )
    def mr = readJSON text: response?.getContent()
    if (mr) {
      return mr
    }
    return null
  } catch (Exception e) {
    return null
  }
}

/**
 * Get GitLab base URL from GITLAB_PROJECT_HTTP_URL environment variable
 */
def gitlabBaseUrl() {
  try {
    URL url = new URL(env.GITLAB_PROJECT_HTTP_URL)
    return "https://"+url.getHost()
  } catch (Exception e) {
    return null
  }
}

def call (Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String gitlabBaseUrl = args.gitlabBaseUrl ?: gitlabBaseUrl()
  if (gitlabBaseUrl == null) {
    return null
  }
  gitlabBaseUrl = gitlabBaseUrl + "/api/v4"

  def projectId = args.projectId ?: env.GITLAB_PROJECT_ID
  if (projectId == null) {
    return null
  }
  // Sanitize projectId to be a number
  if (!projectId.matches(/^\d+$/)) {
    return null
  }

  def gitlabCredentialsId = args.gitlabCredentialsId ?: 'gitlab-text-secret'

  withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
    if (env.CHANGE_ID) {
      return gitFindMRbyChangeID(gitlabBaseUrl, projectId, privateToken)
    } else {
      return gitFindMRbyMergeCommitSha(gitlabBaseUrl, projectId, privateToken)
    }
  }
}