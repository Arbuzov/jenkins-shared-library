import net.sf.json.JSONNull

/**
 * Fetch hubtest test suite file names from Nexus.
 *
 * @param baseUrl     Nexus search API URL (default: https://nexus.asatnet.net/service/rest/v1/search/assets)
 * @param repository  Nexus repository name (default: store)
 * @param namePattern File name glob (default: *.xlsx)
 * @param pathPrefix  Path prefix filter (default: /v_v/hub-test-tools/suite/)
 * @return List of file names matching the criteria.
 */
def call (Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def baseUrl     = arg.baseUrl     ?: "https://nexus.asatnet.net/service/rest/v1/search/assets"
  def repository  = arg.repository  ?: "store"
  def namePattern = arg.namePattern ?: "*.xlsx"
  def pathPrefix  = arg.pathPrefix  ?: "/v_v/hub-test-tools/suite/"

  def baseQuery   = "${baseUrl}?repository=${repository}&name=${namePattern}"
  def continuation = null
  def fileNames = []

  while (true) {
    def url = continuation ? "${baseQuery}&continuationToken=${continuation}" : baseQuery

    def response = httpRequest(
      url: url,
      httpMode: 'GET',
      consoleLogResponseBody: false,
      contentType: 'APPLICATION_JSON',
      authentication: 'jira-username-password',
      quiet: true
    )

    def json = readJSON(text: response.content)

    json?.items?.each { item ->
      def path = item.path
      def fileName = path?.tokenize('/')?.last()
      if (!path || !fileName) { return }
      if (!path.startsWith(pathPrefix)) { return }

      fileNames.add(fileName)
    }

    def rawToken = json?.continuationToken
    if (!rawToken || rawToken instanceof JSONNull) {
      break
    }
    continuation = rawToken
  }

  return fileNames
}

