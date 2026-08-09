import groovy.json.JsonOutput;

def call (String gitUrl, String mrIid, String body = '', Boolean skipIfExist = true, String apiSecretId = gitLabAPISecret, Boolean debug = false) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  if (gitLabAPISecret == null) throw new Exception("Variable 'gitLabAPISecret' is undefined. You must set this variable before function call.");
  
  println "======== Comment to GitLab: " + body.toString() + " ========"
  if (mrIid == null) {
    return;
  }
  if (mrIid.equals('') || mrIid.equals('null')) {
    return;
  }
  if (gitUrl == null) {
    return;
  }
  if (gitUrl.equals('') || gitUrl.equals('null')) {
    return;
  }

  URL url = new URL(gitUrl.replaceFirst(/:(?=\w)/,'/').replace('git@', 'https://'));
  String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
  String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
  URL mrUrl = new URL (gitLabAPIUrl+"/projects/"+usrEncodedProject+"/merge_requests/"+mrIid+"/notes/?private_token=" + gitLabAPISecret);

  println "Leaving comment to URL: " + mrUrl.toString()

  httpRequest(
    url: mrUrl.toString(),
    httpMode: 'POST',
    consoleLogResponseBody: debug,
    contentType: 'APPLICATION_JSON',
    requestBody: JsonOutput.toJson([body: body])
  )
}

// A failed delete is logged, not fatal: the new note is still posted, and the next
// run deletes every marker note it finds, so leftovers self-heal.
def deleteNotes(String notesUrl, List notes, List headers, Boolean debug) {
  for (note in notes) {
    try {
      if (debug) {
        echo "[noteToMrGitlab] DELETE ${notesUrl}/${note.id}"
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
      echo "[noteToMrGitlab] WARNING: failed to delete note ${note.id}: ${e?.message ?: e}"
    }
  }
}

/**
 * Post a comment (note) to a GitLab merge request. With 'marker', maintain a single
 * identifiable note across pipeline re-runs instead of stacking duplicates.
 *
 * Outside a merge-request build (no MR IID / git URL) the step silently does
 * nothing, so it is safe to call from post {} blocks.
 *
 * Args (Map):
 *  - comment:        note body in GitLab markdown (required)
 *  - mrIid:          merge request IID (default: env.CHANGE_ID)
 *  - gitUrl:         SSH or HTTPS repo URL (default: env.GITLAB_OA_TARGET_GIT_SSH_URL)
 *  - apiSecretId:    Jenkins secret-text credentials with the GitLab token
 *                    (default: 'gitlab-text-secret')
 *  - marker:         unique identifying string, e.g. '<!-- my-pipeline:check -->'
 *                    (optional). Appended to the note body as an HTML comment —
 *                    invisible in rendered GitLab markdown — and searched for among
 *                    the MR's existing notes; when found, the existing note is
 *                    replaced instead of a new one added.
 *  - bump:           marker mode only (default: true). true: delete the old note and
 *                    post a fresh one, so the note lands as the newest comment in the
 *                    discussion (the note id changes). false: edit the existing note
 *                    in place — it keeps its id and its original (possibly buried)
 *                    position.
 *  - updateIfExists: marker mode only (default: false). When true, replace an
 *                    existing marker note but do NOT create a new one when none exists.
 *  - debug:          log request URLs / response bodies (default: false)
 *  - skipIfExist:    accepted for backward compatibility, ignored (never implemented)
 *
 * Returns the note id (Integer): a new id when posted/bumped, the same id when edited
 * in place, or null when nothing was posted.
 */
def call (Map args) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String comment = args.comment ?: ''
  if (!comment) {
    throw new Exception("Variable 'comment' is empty or undefined.")
  }
  Boolean debug = args.debug != null ? args.debug : false
  String apiSecretId = args.apiSecretId ?: 'gitlab-text-secret'
  String marker = args.marker ?: null
  Boolean bump = args.bump != null ? args.bump : true
  Boolean updateIfExists = args.updateIfExists != null ? args.updateIfExists : false

  String mrIid = (args.mrIid ?: env.CHANGE_ID)?.toString()
  String gitUrl = args.gitUrl ?: env.GITLAB_OA_TARGET_GIT_SSH_URL
  if (!mrIid || mrIid == 'null' || !gitUrl || gitUrl == 'null') {
    return null
  }

  URL url = new URL(gitUrl.replaceFirst(/:(?=\w)/, '/').replace('git@', 'https://'))
  String projectRef = URLEncoder.encode(url.getPath().substring(1).replace('.git', ''), 'UTF-8')
  String notesUrl = "https://${url.getHost()}/api/v4/projects/${projectRef}/merge_requests/${mrIid}/notes"
  String body = marker ? "${comment}\n\n${marker}" : comment

  return withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabToken')]) {
    if (gitLabToken == null) {
      throw new Exception("GitLab token from credentials '${apiSecretId}' is undefined.")
    }
    def headers = [[name: 'PRIVATE-TOKEN', value: gitLabToken]]

    def existingNotes = []
    if (marker) {
      if (debug) {
        echo "[noteToMrGitlab] GET ${notesUrl}"
      }
      def listResponse = httpRequest(
        url: "${notesUrl}?per_page=100",
        httpMode: 'GET',
        acceptType: 'APPLICATION_JSON',
        customHeaders: headers,
        consoleLogResponseBody: debug,
        quiet: !debug,
      )
      existingNotes = readJSON(text: listResponse.getContent()).findAll {
        it.body?.contains(marker)
      }

      if (!existingNotes && updateIfExists) {
        echo "[noteToMrGitlab] No existing marker note found and updateIfExists=true — comment not added."
        return null
      }
    }

    echo "======== Comment to GitLab: " + comment + " ========"

    if (marker) {
      if (existingNotes && !bump) {
        // Edit in place: the newest note keeps its id and position; duplicates
        // that crept in earlier are removed
        def newest = existingNotes[0]
        for (note in existingNotes) {
          if (note.id > newest.id) {
            newest = note
          }
        }
        deleteNotes(notesUrl, existingNotes.findAll { it.id != newest.id }, headers, debug)
        if (debug) {
          echo "[noteToMrGitlab] PUT ${notesUrl}/${newest.id}"
        }
        def editResponse = httpRequest(
          url: "${notesUrl}/${newest.id}",
          httpMode: 'PUT',
          contentType: 'APPLICATION_JSON',
          acceptType: 'APPLICATION_JSON',
          requestBody: JsonOutput.toJson([body: body]),
          customHeaders: headers,
          consoleLogResponseBody: debug,
          quiet: !debug,
        )
        return readJSON(text: editResponse.getContent()).id
      }

      // bump (the default): delete the old note(s) so the fresh note below lands
      // as the newest comment in the discussion
      deleteNotes(notesUrl, existingNotes, headers, debug)
    }

    if (debug) {
      echo "[noteToMrGitlab] POST ${notesUrl}"
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
