/**
 * Delete old GitLab tags in the current project, keeping only the N most
 * recent by creation date.
 *
 * INTERNAL / HOUSEKEEPING ONLY — not intended for general shared-library
 * consumers. Intended for development pipelines where function-testing jobs
 * produce many throwaway tags. Do NOT call from production release flows.
 *
 * Args (Map):
 *  - keep:           number of most recent tags to keep (default: 3)
 *  - gitUrl:         SSH or HTTPS repo URL (default: env.GIT_URL)
 *  - projectId:      GitLab numeric project ID or URL-encoded path
 *                    (default: resolved from gitUrl)
 *  - gitlabBaseUrl:  GitLab base URL without /api/v4 (default: derived from gitUrl)
 *  - credentialsId:  Jenkins credentials ID with GitLab token
 *                    (default: 'gitlab-text-secret')
 *  - dryRun:         if true, only logs what would be deleted (default: false)
 *
 * Returns Map with:
 *  - kept:    List<String> tag names retained
 *  - deleted: List<String> tag names deleted
 *  - skipped: List<String> tag names skipped (dryRun or delete failure)
 */
def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  int keep = (args.keep != null ? args.keep : 3) as Integer
  if (keep < 0) {
    throw new Exception("gitlabCleanupOldTags(): 'keep' must be >= 0. Got ${keep}.")
  }

  String gitUrl = args.gitUrl ?: env.GIT_URL
  if (!gitUrl && !args.projectId) {
    throw new Exception("gitlabCleanupOldTags(): provide 'gitUrl' or 'projectId' (env.GIT_URL is unset).")
  }

  String credentialsId = args.credentialsId ?: 'gitlab-text-secret'
  Boolean dryRun = args.dryRun ?: false

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
      throw new Exception("gitlabCleanupOldTags(): 'gitlabBaseUrl' is required when 'projectId' is given without 'gitUrl'.")
    }
    base = args.gitlabBaseUrl
    projectRef = args.projectId.toString()
  }

  String apiBase = "${base}/api/v4/projects/${projectRef}/repository/tags"

  def result = [kept: [], deleted: [], skipped: []]

  withCredentials([string(credentialsId: credentialsId, variable: 'gitLabToken')]) {
    def tags = []
    int page = 1
    int perPage = 100
    while (page <= 100) {
      def resp = httpRequest(
        url: "${apiBase}?per_page=${perPage}&page=${page}&order_by=updated&sort=desc",
        httpMode: 'GET',
        consoleLogResponseBody: false,
        contentType: 'APPLICATION_JSON',
        customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabToken]],
        quiet: true
      )
      def batch = readJSON(text: resp.getContent())
      if (!batch) break
      tags.addAll(batch)
      if (batch.size() < perPage) break
      page++
    }

    // GitLab returns tags in order_by=updated&sort=desc (newest first), so we
    // rely on server-side ordering. Client-side sort with a closure trips the
    // Jenkins CPS sandbox, so we avoid it here.
    int total = tags.size()
    int splitAt = Math.min(keep, total)
    def toKeep = total == 0 ? [] : tags.subList(0, splitAt)
    def toDelete = splitAt >= total ? [] : tags.subList(splitAt, total)

    result.kept = toKeep.collect { it.name }

    if (toDelete.isEmpty()) {
      echo "gitlabCleanupOldTags: nothing to delete. total=${tags.size()}, keep=${keep}."
      return result
    }

    echo "gitlabCleanupOldTags: keeping ${toKeep.size()} most recent, deleting ${toDelete.size()} (dryRun=${dryRun})."

    toDelete.each { tag ->
      String tagName = tag.name
      if (dryRun) {
        echo "  [dryRun] would delete ${tagName}"
        result.skipped << tagName
        return
      }
      String encoded = URLEncoder.encode(tagName, 'UTF-8')
      def resp = httpRequest(
        url: "${apiBase}/${encoded}",
        httpMode: 'DELETE',
        consoleLogResponseBody: false,
        validResponseCodes: '100:499',
        contentType: 'APPLICATION_JSON',
        customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabToken]],
        quiet: true
      )
      int status = (resp?.status ?: 0) as Integer
      if (status >= 200 && status < 300) {
        result.deleted << tagName
        echo "  deleted ${tagName}"
      } else {
        result.skipped << tagName
        echo "  skipped ${tagName} (status=${status})"
      }
    }
  }
  return result
}
