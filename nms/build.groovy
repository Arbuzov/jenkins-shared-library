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

def remoteConfig = (SEED_JOB.getSCMs())[0].getUserRemoteConfigs().get(0);

String repoCreds = remoteConfig.getCredentialsId();

projectRepos.each{
	jobName = "corenms-"+it.key;
	if (it.key.equals('origin')) {
		jobName = "corenms";
	}
	def gitUrl = it.value;
	multibranchPipelineJob(jobName) {
		triggers {
			periodic(2)
		}
		branchSources {
			git {
				remote(gitUrl)
				credentialsId(repoCreds)
			}
		}
		configure {
			it / 'sources' / 'data' / 'jenkins.branch.BranchSource' / 'source' / 'traits' / 'jenkins.plugins.git.traits.BranchDiscoveryTrait'
		}
		configure {
				it / 'sources' / 'data' / 'jenkins.branch.BranchSource' / 'source' / 'traits' / 'jenkins.plugins.git.traits.CloneOptionTrait' / extension(class: 'hudson.plugins.git.extensions.impl.CloneOption') {
					'shallow'('false')
					'noTags'('false')
				}
			/*
				 / 'extension(class: "hudson.plugins.git.extensions.impl.CloneOption")' {
			}*/
		}
	}
}
categorizedJobsView("nms-dev") {
	description('All builds linked to the nms-dev repository')
	jobs {
		regex(/corenms.*|webnms.*|geo.*/)
	}
	categorizationCriteria {
		regexGroupingRule(/corenms|webnms|geo|jenkins\-node|test/)
	}
	columns {
		status()
		weather()
		name()
		buildButton()
	}
}