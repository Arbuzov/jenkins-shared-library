import hudson.model.*;
import java.util.regex.*;

/*********************************************/

String genericBranch = "develop";

String projectName = "corenms.git";

String webRootUrl = "https://git.advantech.ca/cgit.cgi"

String gitRootUrl = "ssh://git7@advgit.advantech.ca"

/*********************************************/

def getProjectRepos(projectName, webRootUrl, gitRootUrl) {
	def nullTrustManager = [
		checkClientTrusted: { chain, authType ->  },
		checkServerTrusted: { chain, authType ->  },
		getAcceptedIssuers: { null }
	]

	def nullHostnameVerifier = [
		verify: { hostname, session -> 
			true 
		}
	]

	def projectRepos = [:]
	javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL")
	sc.init(null, [nullTrustManager as  javax.net.ssl.X509TrustManager] as  javax.net.ssl.X509TrustManager[], null)
	javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
	javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier as javax.net.ssl.HostnameVerifier)
	def projectRegexp = "title='repo/([a-z]|/|-)+\\.git"
	def projectPattern = Pattern.compile(projectRegexp)
	def projectMatcher = projectPattern.matcher(new URL (webRootUrl+"/?q="+projectName).getText())

	projectMatcher.each {
		repoPath = it[0].toString().replace("title='","")
		if (repoPath.startsWith("repo/users/")) {
			tmp = repoPath.replace("repo/users/","")
			repOwner = tmp.take(tmp.indexOf('/'))
		} else {
			repOwner = 'origin'
		}
		projectRepos[repOwner] = (gitRootUrl + "/" + repoPath)
	}
	
	return projectRepos;
}

def projectRepos = getProjectRepos(projectName, webRootUrl, gitRootUrl);

println projectRepos;

listView("nms-dev") {
	description('All builds linked to the nms-dev repository')
	filterBuildQueue()
	filterExecutors()
	jobs {
		regex(/.*nms.*/)
	}
	columns {
		status()
		weather()
		name()
		buildButton()
	}
}