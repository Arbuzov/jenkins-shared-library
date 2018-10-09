package utils;
import java.util.regex.*;
static call(String projectName, String webRootUrl, String gitRootUrl) {

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
    def projectRegexp = "title='repo/([a-z]|/|-|\\d)+\\.git"
    def projectPattern = Pattern.compile(projectRegexp)
    def projectMatcher = projectPattern.matcher(new URL (webRootUrl+"/?q="+projectName).getText())

    projectMatcher.each {
        String repoPath = it[0].toString().replace("title='","");
        String repOwner = 'origin';
        if (repoPath.startsWith("repo/users/")) {
            String tmp = repoPath.replace("repo/users/","")
            repOwner = tmp.take(tmp.indexOf('/'))
        }
        projectRepos[repOwner] = (gitRootUrl + "/" + repoPath)
    }
    
    return projectRepos;
}