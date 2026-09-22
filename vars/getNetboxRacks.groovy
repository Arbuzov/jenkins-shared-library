def call(Map arg = [:]) {
  def netboxUrl = (arg.netboxUrl ?: 'https://netbox.sandbox.asatnet.net/netbox').replaceAll('/+$', '')
  def credentialsId = arg.credentialsId ?: 'netbox'
  def limit = arg.limit ?: 500

  withCredentials([string(credentialsId: credentialsId, variable: 'TOKEN')]) {
    def url = "${netboxUrl}/api/dcim/racks/"
    def response = httpRequest(
      url: url,
      httpMode: 'GET',
      customHeaders: [[name: "Authorization", value: "Token ${TOKEN}"]],
      acceptType: 'APPLICATION_JSON',
      ignoreSslErrors: true
    )
    def jsonResponse = readJSON(text: response.content)
    return jsonResponse.results
  }
}
