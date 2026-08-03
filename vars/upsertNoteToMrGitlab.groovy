import groovy.json.JsonOutput

/**
 * Create or update a single merge-request note identified by a hidden marker, so
 * pipeline re-runs maintain ONE comment instead of stacking duplicates (unlike
 * noteToMrGitlab, whose skipIfExist parameter is not implemented).
 *
 * The marker — an HTML comment, invisible in rendered GitLab markdown — is appended
 * to the note body and searched for among the MR's existing notes.
 *
 * Args (Map):
 *  - comment:       note body in GitLab markdown (required)
 *  - marker:        unique identifying string, e.g. '<!-- my-pipeline:check -->' (required)
 *  - mrIid:         merge request IID (default: env.CHANGE_ID)
 *  - gitUrl:        SSH or HTTPS repo URL
 *                   (default: env.GIT_URL ?: env.GITLAB_OA_TARGET_GIT_SSH_URL)
 *  - projectId:     GitLab numeric project ID or URL-encoded path
 *                   (default: derived from gitUrl)
 *  - gitlabBaseUrl: GitLab base URL without /api/v4 (default: derived from gitUrl)
 *  - onlyUpdate:    when true, update an existing marker note but do NOT create a
 *                   new one when none exists (default: false)
 *  - credentialsId: Jenkins secret-text credentials with the GitLab token
 *                   (default: 'gitlab-text-secret')
 *  - debug:         log request URLs / response bodies (default: false)
 *
 * Returns the note id (Integer), or null when nothing was posted or updated.
 */
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  if (!args.comment) {
    error("upsertNoteToMrGitlab(): 'comment' is required.")
  }
  if (!args.marker) {
    error("upsertNoteToMrGitlab(): 'marker' is required.")
  }
  String mrIid = (args.mrIid ?: env.CHANGE_ID)?.toString()
  if (!mrIid) {
    error("upsertNoteToMrGitlab(): 'mrIid' is required outside a merge-request build.")
  }
  String gitUrl = args.gitUrl ?: env.GIT_URL ?: env.GITLAB_OA_TARGET_GIT_SSH_URL
  if (!gitUrl && !(args.projectId && args.gitlabBaseUrl)) {
    error("upsertNoteToMrGitlab(): provide 'gitUrl', or 'projectId' + 'gitlabBaseUrl'.")
  }
  Boolean debug = args.debug ?: false
  String credentialsId = args.credentialsId ?: 'gitlab-text-secret'

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
    base = args.gitlabBaseUrl
    projectRef = args.projectId.toString()
  }

  String notesUrl = "${base}/api/v4/projects/${projectRef}/merge_requests/${mrIid}/notes"
  String body = "${args.comment}\n\n${args.marker}"

  withCredentials([string(credentialsId: credentialsId, variable: 'gitLabToken')]) {
    if (gitLabToken == null) {
      error("upsertNoteToMrGitlab(): GitLab token from credentials '${credentialsId}' is undefined.")
    }
    def headers = [[name: 'PRIVATE-TOKEN', value: gitLabToken]]

    if (debug) {
      echo "[upsertNoteToMrGitlab] GET ${notesUrl}"
    }
    def listResponse = httpRequest(
      url: "${notesUrl}?per_page=100",
      httpMode: 'GET',
      acceptType: 'APPLICATION_JSON',
      customHeaders: headers,
      consoleLogResponseBody: debug,
      quiet: !debug,
    )
    def existing = readJSON(text: listResponse.getContent()).find {
      it.body?.contains(args.marker)
    }

    if (existing) {
      if (existing.body == body) {
        if (debug) {
          echo "[upsertNoteToMrGitlab] Note ${existing.id} is already up to date."
        }
        return existing.id
      }
      if (debug) {
        echo "[upsertNoteToMrGitlab] PUT ${notesUrl}/${existing.id}"
      }
      httpRequest(
        url: "${notesUrl}/${existing.id}",
        httpMode: 'PUT',
        contentType: 'APPLICATION_JSON',
        acceptType: 'APPLICATION_JSON',
        requestBody: JsonOutput.toJson([body: body]),
        customHeaders: headers,
        consoleLogResponseBody: debug,
        quiet: !debug,
      )
      return existing.id
    }

    if (args.onlyUpdate) {
      if (debug) {
        echo "[upsertNoteToMrGitlab] No marker note found and onlyUpdate=true — nothing to do."
      }
      return null
    }

    if (debug) {
      echo "[upsertNoteToMrGitlab] POST ${notesUrl}"
    }
    def createResponse = httpRequest(
      url: notesUrl,
      httpMode: 'POST',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      requestBody: JsonOutput.toJson([body: body]),
      customHeaders: headers,
      consoleLogResponseBody: debug,
      quiet: !debug,
    )
    return readJSON(text: createResponse.getContent()).id
  }
}
