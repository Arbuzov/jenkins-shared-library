def call(Map arg = [:]) {
  def netboxUrl = arg.netboxUrl ?: 'http://192.168.67.56/netbox/'
  def credentialsId = arg.credentialsId ?: 'netbox'
  def role = arg.role ?: false
  def tenant = arg.tenant ?: false
  def limit = arg.limit ?: 500

  withCredentials([string(credentialsId: credentialsId, variable: 'TOKEN')]) {
    def url = "${netboxUrl}/api/dcim/devices"
    def search = []
    if (role) {
      search.add("role=${role}")
    }
    if (tenant) {
      search.add("tenant=${tenant}")
    }
    search.add("limit=${limit}")
    if (search.size() > 0) {
      url += "?${search.join('&')}"
    }
    def response = httpRequest(
      url: url,
      httpMode: 'GET',
      customHeaders: [[name: "Authorization", value: "Token ${TOKEN}"]],
      acceptType: 'APPLICATION_JSON'
    )
    def jsonResponse = readJSON(text: response.content)
    return jsonResponse.results
  }
}
