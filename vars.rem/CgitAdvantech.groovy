package com.advantech;

public class CgitAdvantech {

def getProjectRepos(projectName, webRootUrl, gitRootUrl) {

	System.out.println('2345235235324532');
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
}