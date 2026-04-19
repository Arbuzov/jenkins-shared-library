String call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def nexusUrl = arg.nexusUrl ?: 'https://nexus.example.com'
  def repository = arg.repository ?: 'maven-snapshots'
  def extension = arg.extension ?: 'deb'
  def groupId = arg.groupId
  def artifactId = arg.artifactId
  def version = "${arg.version}-SNAPSHOT"
  def credentialsId = arg.credentialsId ?: 'jira-username-password'

  def apiUrl = "${nexusUrl}/service/rest/v1/search/assets?repository=${repository}&maven.groupId=${groupId}&maven.artifactId=${artifactId}&maven.baseVersion=${version}&maven.extension=${extension}"

  def response = httpRequest(
    url: apiUrl,
    httpMode: 'GET',
    contentType: 'APPLICATION_JSON',
    authentication: 'jira-username-password'
  )
  def json = readJSON(text: response.content)
  def items = json.items
  return items[0]?.maven2?.version?.toString()
}