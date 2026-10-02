/**
 * Read a file of a GitLab repository, and the commit it was read at, via the REST API.
 *
 * Wraps GET /projects/:id/repository/files/:file_path?ref= through gitlabApiGet and
 * adds the decoded content to GitLab's answer.
 *
 * Args (Map):
 *  - filePath:       repo-relative path of the file, e.g. 'deploy/Manifest' (required)
 *  - ref:            branch, tag or commit SHA to read the file at (required)
 *  - gitUrl:         SSH or HTTPS repo URL (default: env.GIT_URL)
 *  - projectId:      GitLab numeric project ID, or the project path in either
 *                    form (default: derived from gitUrl)
 *  - gitlabBaseUrl:  GitLab base URL without /api/v4 (default: derived from gitUrl)
 *  - credentialsId:  Jenkins credentials ID with GitLab token
 *                    (default: 'gitlab-text-secret')
 *  - debug:          log the request URL and the response body (default: false)
 *
 * Returns Map - GitLab's answer as gitlabApiGet returned it (file_name, file_path,
 * size, encoding, content (base64), content_sha256, ref, blob_id, commit_id,
 * last_commit_id, ...) plus text: the content decoded from base64 as UTF-8, line
 * endings kept. commit_id is the commit 'ref' pointed to when the file was read;
 * last_commit_id is the last commit that changed the file.
 *
 * Fails through error(...) when filePath or ref is missing, before any request.
 * Every other failure (project, token, a missing file or ref answering 404)
 * surfaces from gitlabApiGet.
 */
/**
 * Decode GitLab's base64 file content as UTF-8. The MIME decoder accepts content
 * wrapped over several lines.
 */
@NonCPS
String decodeContent(Object content) {
  if (!content) {
    return ''
  }
  return new String(Base64.getMimeDecoder().decode(content.toString()), 'UTF-8')
}

def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (!args.filePath) {
    error("gitlabGetFile(): 'filePath' is required - the repo-relative path of the file, e.g. 'deploy/Manifest'.")
  }
  if (!args.ref) {
    error("gitlabGetFile(): 'ref' is required - the branch, tag or commit SHA to read the file at.")
  }

  String path = 'repository/files/' + URLEncoder.encode(args.filePath.toString(), 'UTF-8').replace('+', '%20')
  Map apiArgs = args.subMap(['gitUrl', 'projectId', 'gitlabBaseUrl', 'credentialsId', 'debug'])
  apiArgs.path = path
  apiArgs.query = [ref: args.ref]

  Map file = gitlabApiGet(apiArgs)
  return file + [text: decodeContent(file.content)]
}
