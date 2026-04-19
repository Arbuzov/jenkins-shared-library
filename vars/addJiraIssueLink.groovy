def call(issueId, link, title = '', credentialsId = 'jira-username-password') {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    def issueInfo = getJiraIssueInfo(issueId, credentialsId)
    def linksUrl  = issueInfo.self + '/remotelink'

    if (title.equals('')) {
       title = link.tokenize('/')[-1]
    }
    def data = "{\"object\":{\"url\":\"${link}\",\"title\":\"${title}\"}}"
    httpRequest(
      url: linksUrl,
      httpMode: 'POST',
      consoleLogResponseBody: false,
      contentType: 'APPLICATION_JSON',
      authentication: credentialsId,
      requestBody: data,
      quiet: true
    )
}