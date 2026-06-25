import groovy.json.JsonOutput

/**
 * Update a single existing file in a GitLab repository via the REST API,
 * producing one commit on the target branch.
 *
 * Wraps PUT /projects/:id/repository/files/:file_path. The file must already
 * exist on the branch — GitLab answers 400 otherwise (use the GitLab UI or a
 * create call for brand-new files).
 *
 * Args (Map):
 *  - filePath:       repo-relative path of the file to update (required)
 *  - content:        new file content (required; may be empty string)
 *  - branch:         branch to commit on (default: env.BRANCH_NAME, else 'master')
 *  - commitMessage:  commit message (default: "Update <filePath>")
 *  - gitUrl:         SSH or HTTPS repo URL (default: env.GIT_URL)
 *  - projectId:      GitLab numeric project ID or URL-encoded path
 *                    (default: derived from gitUrl)
 *  - gitlabBaseUrl:  GitLab base URL without /api/v4 (default: derived from gitUrl)
 *  - credentialsId:  Jenkins credentials ID with GitLab token
 *                    (default: 'gitlab-text-secret')
 *  - authorName:     commit author name override (optional)
 *  - authorEmail:    commit author email override (optional)
 *  - startBranch:    create `branch` off this branch if it does not exist (optional)
 *  - debug:          log request URL / response body (default: false)
 *
 * Returns Map - GitLab API response, e.g. [file_path: ..., branch: ...].
 */
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String filePath = args.filePath
  if (!filePath) {
    error("gitlabUpdateFile(): 'filePath' is required.")
  }
  if (args.content == null) {
    error("gitlabUpdateFile(): 'content' is required (pass '' to empty the file).")
  }
  String content = args.content

  String gitUrl = args.gitUrl ?: env.GIT_URL
  if (!gitUrl && !args.projectId) {
    error("gitlabUpdateFile(): provide 'gitUrl' or 'projectId' (env.GIT_URL is unset).")
  }

  String branch = args.branch ?: env.BRANCH_NAME ?: 'master'
  String commitMessage = args.commitMessage ?: "Update ${filePath}"
  String credentialsId = args.credentialsId ?: 'gitlab-text-secret'
  Boolean debug = args.debug ?: false

  String base
  String projectRef
  if (gitUrl) {
    URL url = new URL(gitUrl.replaceFirst(/:(?=[a-z])/, '/').replace('git@', 'https://'))
    base = args.gitlabBaseUrl ?: ("https://" + url.getHost())
    if (args.projectId) {
      projectRef = args.projectId.toString()
    } else {
      String path = url.getPath().substring(1).replace('.git', '')
      projectRef = URLEncoder.encode(path, 'UTF-8')
    }
  } else {
    if (!args.gitlabBaseUrl) {
      error("gitlabUpdateFile(): 'gitlabBaseUrl' is required when 'projectId' is given without 'gitUrl'.")
    }
    base = args.gitlabBaseUrl
    projectRef = args.projectId.toString()
  }

  String encodedPath = URLEncoder.encode(filePath, 'UTF-8')
  String apiUrl = "${base}/api/v4/projects/${projectRef}/repository/files/${encodedPath}"

  def payload = [branch: branch, content: content, commit_message: commitMessage]
  if (args.authorName)  payload.author_name = args.authorName
  if (args.authorEmail) payload.author_email = args.authorEmail
  if (args.startBranch) payload.start_branch = args.startBranch

  withCredentials([string(credentialsId: credentialsId, variable: 'gitLabToken')]) {
    if (gitLabToken == null) {
      error("gitlabUpdateFile(): GitLab token from credentials '${credentialsId}' is undefined.")
    }
    if (debug) {
      echo "[gitlabUpdateFile] PUT ${apiUrl} branch=${branch}"
    }
    def response = httpRequest(
      url: apiUrl,
      httpMode: 'PUT',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      requestBody: JsonOutput.toJson(payload),
      customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabToken]],
      validResponseCodes: '100:399',
      consoleLogResponseBody: debug,
      quiet: !debug,
    )
    return readJSON(text: response?.getContent())
  }
}
