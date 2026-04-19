ArrayList call(String artifactsPattern, String repoPath, Boolean flattern = false, String credentialsId = 'jira-username-password') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def fileList = findFiles(glob: artifactsPattern).toList()
  ArrayList selectedFiles = []
  findFiles(glob: artifactsPattern).toList().each{
    httpRequest(
      url: repoPath+(flattern?it.name:it.path),
      httpMode: 'PUT',
      ignoreSslErrors: true,
      contentType: 'APPLICATION_OCTETSTREAM',
      authentication: credentialsId,
      multipartName: it.name,
      uploadFile: it.path,
      responseHandle: 'NONE',
//      validResponseCodes: '100:599',
//      consoleLogResponseBody: true,
      wrapAsMultipart: false
    )
    selectedFiles.add(repoPath+(flattern?it.name:it.path))
  }
  return selectedFiles
}