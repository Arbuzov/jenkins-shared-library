import groovy.json.JsonOutput

/**
 * Maintain a single merge-request note identified by a hidden marker, so pipeline
 * re-runs keep ONE comment instead of stacking duplicates (unlike noteToMrGitlab,
 * whose skipIfExist parameter is not implemented). The existing marker note is
 * DELETED and a fresh one is posted — the note always appears as the most recent
 * comment in the MR discussion (a PUT-updated note would stay buried at its
 * original position). The note id therefore changes on every call.
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
 *  - onlyUpdate:    when true, replace an existing marker note but do NOT create a
 *                   new one when none exists (default: false)
 *  - credentialsId: Jenkins secret-text credentials with the GitLab token
 *                   (default: 'gitlab-text-secret')
 *  - debug:         log request URLs / response bodies (default: false)
 *
 * Returns the new note id (Integer), or null when nothing was posted.
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
    def existingNotes = readJSON(text: listResponse.getContent()).findAll {
      it.body?.contains(args.marker)
    }

    if (!existingNotes && args.onlyUpdate) {
      if (debug) {
        echo "[upsertNoteToMrGitlab] No marker note found and onlyUpdate=true — nothing to do."
      }
      return null
    }

    // Delete the old note(s) — all of them, in case duplicates ever crept in —
    // so the fresh note below lands as the newest comment in the discussion.
    // A failed delete is logged, not fatal: the new note is still posted, and
    // the next run deletes every marker note it finds, so leftovers self-heal.
    for (note in existingNotes) {
      try {
        if (debug) {
          echo "[upsertNoteToMrGitlab] DELETE ${notesUrl}/${note.id}"
        }
        httpRequest(
          url: "${notesUrl}/${note.id}",
          httpMode: 'DELETE',
          customHeaders: headers,
          consoleLogResponseBody: debug,
          quiet: !debug,
          validResponseCodes: '100:399,404', // already gone is fine
        )
      } catch (Exception e) {
        echo "[upsertNoteToMrGitlab] WARNING: failed to delete note ${note.id}: ${e?.message ?: e}"
      }
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
