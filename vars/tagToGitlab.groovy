import groovy.json.JsonSlurper;
def call (String gitUrl, String tagName, String description = '') {
	String gitLabAPIUrl = "https://gitlab.advantech.ca/api/v4";
	
	def nullTrustManager = [
		checkClientTrusted: { chain, authType -> },
		checkServerTrusted: { chain, authType -> },
		getAcceptedIssuers: { null }
	]

	def nullHostnameVerifier = [
		verify: { hostname, session -> 
			true 
		}
	]
	javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL")
	sc.init(null, [nullTrustManager as  javax.net.ssl.X509TrustManager] as  javax.net.ssl.X509TrustManager[], null)
	javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
	javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier as javax.net.ssl.HostnameVerifier);
	def projectList=new JsonSlurper().parse(new URL (gitLabAPIUrl +"/projects/?private_token=" + gitLabAPISecret + "&search="+gitUrl));
}
