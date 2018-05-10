import hudson.model.*;
import groovy.json.JsonSlurper;

/*********************************************/

String genericBranch = "develop";

String projectName = "nms-dev-corenms";

gitLabAPIUrl = "https://gitlab.advantech.ca/api/v4"
gitLabAPISecret = "EY5yZqrYRKBxn8FDAeZh"
usersBlackList = ['alandovskis', 'spaul', 'jguo', 'romanovici', 'jtu'];

/*********************************************/

def getProjectRepos(projectName) {
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
	def projects = [:]
	javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL")
	sc.init(null, [nullTrustManager as  javax.net.ssl.X509TrustManager] as  javax.net.ssl.X509TrustManager[], null)
	javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
	javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(nullHostnameVerifier as javax.net.ssl.HostnameVerifier);	
		def projectList=new JsonSlurper().parse(new URL (gitLabAPIUrl +"/projects/?private_token=" + gitLabAPISecret + "&search="+projectName));
		projectList.each {
			if (it.name.equals(projectName) && !it.namespace.name.startsWith("zz-") && !usersBlackList.contains(it.namespace.name)) {
				def nsName="origin";
				if (it.forks_count==0) {
					nsName=it.namespace.name;
				}
				projects[nsName]=it.ssh_url_to_repo
			}
		}
	return projects;
}

def projectRepos = getProjectRepos(projectName);

def remoteConfig = (SEED_JOB.getSCMs())[0].getUserRemoteConfigs().get(0);

String repoCreds = remoteConfig.getCredentialsId();

projectRepos.each{
	if (!usersBlackList.contains(it.key.toString())) {
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
			}
		}
	}
}
categorizedJobsView("nms-dev") {
	description('All builds linked to the nms-dev repository')
	jobs {
		regex(/corenms.*|webnms.*|geo.*|.*pcr.*/)
	}
	categorizationCriteria {
		regexGroupingRule(/corenms|webnms|geo|jenkins\-node|test|pcr/)
	}
	columns {
		status()
		weather()
		name()
		buildButton()
	}
}