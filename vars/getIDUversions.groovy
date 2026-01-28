import java.time.LocalDate
import net.sf.json.JSONNull

/**
 * Fetch SIDU build artifacts from Nexus and return a map: version -> full path.
 *
 * @param baseUrl     Nexus search API URL (default: https://nexus.asatnet.net/service/rest/v1/search/assets)
 * @param repository  Nexus repository name (default: store)
 * @param namePattern File name glob (default: *.sit)
 * @param pathPrefix  Path prefix filter (default: /idu/sdk-s8000/ING/)
 * @param daysBack    Only include artifacts newer than today - daysBack (default: 30)
 * @return Map where key is artifact version (without .sit) and value is full Nexus path.
 */
def call (Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def baseUrl     = arg.baseUrl     ?: "https://nexus.asatnet.net/service/rest/v1/search/assets"
  def repository  = arg.repository  ?: "store"
  def namePattern = arg.namePattern ?: "*.sit"
  def pathPrefix  = arg.pathPrefix  ?: "/idu/sdk-s8000/ING/"
  def daysBack    = (arg.daysBack   ?: 30) as int

  def baseQuery   = "${baseUrl}?repository=${repository}&name=${namePattern}"
  def continuation = null
  def artifacts = [:]
  def cutoffDateStr = LocalDate.now().minusDays(daysBack).toString()

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

      if (!fileName.endsWith('.sit')) { return }
      def matcher = path =~ /\d{4}-\d{2}-\d{2}/
      if (!matcher.find()) { return }
      def dateStr = matcher.group(0)
      if (dateStr < cutoffDateStr) { return }

      def version = fileName[0..-5] // Remove the '.sit' extension
      artifacts[version] = path
    }

    def rawToken = json?.continuationToken
    if (!rawToken || rawToken instanceof JSONNull) {
      break
    }
    continuation = rawToken
  }

  return artifacts
}
