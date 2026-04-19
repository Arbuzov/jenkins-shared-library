
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
    URL url
    
    if(env.GITLAB_PROJECT_HTTP_URL) {
      url = new URL(env.GITLAB_PROJECT_HTTP_URL)
    } else if(env.GIT_URL) {
      // GIT_URL is in the form git@gitlab.com:project/path.git
      // Remove leading http[s]:// just in case
      // Substitute : with /
      // Substitute git@ with https://
      // Remove .git
      // Prepend with https://
      def gitUrl = env.GIT_URL?.trim()
      if (gitUrl?.startsWith('http')) {
        url = new URL(gitUrl)
      } else if (gitUrl?.startsWith('git@')) {
        def ssh = gitUrl - '.git'
        def hostAndPath = gitUrl.substring(4).replace(':', '/')
        url = new URL("https://${hostAndPath}")
      } else {
        return null
      }

    } else {
      return null
    }
    return "https://"+url.getHost()
  } catch (Exception e) {
    return null
  }
}

def gitlabProjectNameEscaped() {
  if(!env.GIT_URL || !env.GIT_URL.startsWith('git@') || !env.GIT_URL.contains(':')) {
    return null
  }
  def gitUrl = env.GIT_URL?.trim()
  def ssh = gitUrl - '.git'
  return ssh.split(':')[1].replaceAll('/', '%2F')
}

def call (Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def gitlabCredentialsId = args.gitlabCredentialsId ?: 'gitlab-text-secret'

  String gitlabBaseUrl = args.gitlabBaseUrl ?: gitlabBaseUrl()
  if (gitlabBaseUrl == null) {
    return null
  }
  gitlabBaseUrl = gitlabBaseUrl + "/api/v4"

  def projectId = args.projectId ?: env.GITLAB_PROJECT_ID
  if (projectId == null && gitlabProjectNameEscaped()) {
    try {
      withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
        def response = httpRequest(
          url: "${gitlabBaseUrl}/projects/${gitlabProjectNameEscaped()}",
          customHeaders: [[name: 'PRIVATE-TOKEN', value: privateToken]],
          acceptType: 'APPLICATION_JSON',
          quiet: true,
          consoleLogResponseBody: false,
        )
        def project = readJSON text: response?.getContent()
        projectId = project.id
      }
    } catch (Exception e) {
      return null
    }
  }
  if (projectId == null) {
    return null
  }
  // Sanitize projectId to be a number
  if (!projectId.matches(/^\d+$/)) {
    return null
  }

  

  withCredentials([string(credentialsId: gitlabCredentialsId, variable: 'privateToken')]) {
    if (env.CHANGE_ID) {
      return gitFindMRbyChangeID(gitlabBaseUrl, projectId, privateToken)
    } else {
      return gitFindMRbyMergeCommitSha(gitlabBaseUrl, projectId, privateToken)
    }
  }
}