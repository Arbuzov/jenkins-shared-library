import groovy.json.JsonOutput

/** Atomically update existing files, each guarded by its last known file commit. */
def call(Map args = [:]) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)
    if (!args.gitUrl || !args.branch || !args.commitMessage) {
        error('gitlabCommitFiles: gitUrl, branch and commitMessage are required.')
    }
    if (!(args.files instanceof List) || !args.files || args.files.any {
        !(it instanceof Map) || !it.filePath || !(it.content instanceof CharSequence) || !it.lastCommitId
    }) {
        error('gitlabCommitFiles: files must contain filePath, content and lastCommitId for every update.')
    }
    if (args.files.collect { it.filePath }.unique().size() != args.files.size()) {
        error('gitlabCommitFiles: duplicate file paths.')
    }
    // This step deliberately accepts HTTP(S) repository URLs, including an API port.
    URL repo = new URL(args.gitUrl.toString())
    if (!(repo.protocol in ['http', 'https']) || repo.userInfo || repo.query || repo.ref || repo.path in ['', '/']) {
        error('gitlabCommitFiles: gitUrl must be an HTTP(S) repository URL without credentials, query or fragment.')
    }
    String origin = "${repo.protocol}://${repo.host}" + (repo.port > 0 ? ":${repo.port}" : '')
    String project = URLEncoder.encode(repo.path.substring(1).replaceAll(/\/$/, '').replaceAll(/\.git$/, ''), 'UTF-8')
    def payload = [branch: args.branch, commit_message: args.commitMessage, actions: args.files.collect {
        [action: 'update', file_path: it.filePath, content: it.content.toString(), last_commit_id: it.lastCommitId]
    }]
    def result
    withCredentials([string(credentialsId: args.credentialsId ?: 'gitlab-text-secret', variable: 'gitLabToken')]) {
        if (!gitLabToken) { error('gitlabCommitFiles: GitLab token is empty.') }
        def response = httpRequest(
            url: "${origin}/api/v4/projects/${project}/repository/commits",
            httpMode: 'POST', contentType: 'APPLICATION_JSON', acceptType: 'APPLICATION_JSON',
            requestBody: JsonOutput.toJson(payload),
            customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabToken, maskValue: true]],
            validResponseCodes: '201', consoleLogResponseBody: false, quiet: true
        )
        result = readJSON(text: response.content, returnPojo: true)
    }
    return result
}
