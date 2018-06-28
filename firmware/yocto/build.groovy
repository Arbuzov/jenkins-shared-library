import hudson.model.*;
import groovy.json.JsonSlurper;

/*********************************************/

String hostLabel = "jslave-step1";

String genericBranch = "fido-plinux";

String projectName = "satnet-dev-yocto-meta-awl";

gitLabAPIUrl = "https://gitlab.advantech.ca/api/v4"
gitLabAPISecret = "EY5yZqrYRKBxn8FDAeZh"
usersBlackList = ['alandovskis', 'spaul', 'jguo', 'romanovici', 'jtu'];

/*********************************************/

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

hudson.FilePath workspace = (new hudson.FilePath(new File("${WORKSPACE}")));

def remoteConfig = (SEED_JOB.getSCMs())[0].getUserRemoteConfigs().get(0);

String repoCreds = remoteConfig.getCredentialsId();

File scriptFile = new File("${workspace}/firmware/yocto/script.sh");

File scriptToMakeChangeLog = new File("${workspace}/firmware/git_log.sh");

projectRepos.each{
    if (!usersBlackList.contains(it.key.toString())) {
        jobName = "yocto-build-"+it.key;
        if (it.key.equals('origin')) {
            jobName = "yocto-build";
        }
        def gitUrl = it.value
        job(jobName) {
               properties {
                    buildDiscarder {
                        strategy {
                            logRotator {
                                daysToKeepStr('-1')
                                numToKeepStr('-1')
                                artifactDaysToKeepStr('-1')
                                artifactNumToKeepStr('5')
                            }
                        }
                    }
               }
           keepDependencies()
           scm {
                git {
                    remote {
                        url(gitUrl)
                        credentials(repoCreds)
                    }
                    branch("*/"+genericBranch)
                }
           }
           label(hostLabel)
           triggers {
                scm("H/5 * * * *")
           }
           steps {
                shell(scriptFile.getText())
                shell("#!/bin/bash\nset -x\nrepos=(\$WORKSPACE)\n"+scriptToMakeChangeLog.getText())
           }
           publishers {
               textFinder(/(ERROR message|ERROR 404)/, '', true, false, true)
               archiveArtifacts {
                    pattern("artifacts/**, **.log,/home/build/fsdk/v1.9/QorIQ-SDK-V1.9-20151210-yocto/build_awl-idu7000/tmp/work/**/log.do_configure.*")
                    fingerprint(true)
                    allowEmpty(false)
               }
               publishHtml {
                   report ('$WORKSPACE') {
                       reportName('Change Log')
                       reportFiles('META-AWL.change.log')
                       keepAll()
                       allowMissing()
                       alwaysLinkToLastBuild()
                   }
               }
               extendedEmailPublisher {
                    project_recipient_list('$DEFAULT_RECIPIENTS')
                    project_default_subject('$DEFAULT_SUBJECT')
                    project_replyto('$DEFAULT_REPLYTO')
                    project_content_type('both')
                    project_default_content('${SCRIPT, template="changelog.html.groovy"}')
                    project_presend_script('$DEFAULT_PRESEND_SCRIPT')
                    postsendScript('$DEFAULT_POSTSEND_SCRIPT')
                    project_save_output(true)
                    project_disabled(false)
                    matrixTriggerMode('BOTH')
                    project_attach_buildlog(0)
                    project_attachments('')
                    project_from('')
                    project_triggers {
                        xNthFailureTrigger {
                            recipientProviders {
                                listRecipientProvider()
                                requesterRecipientProvider()
                            }
                            recipientList('$DEFAULT_RECIPIENTS')
                            replyTo('$PROJECT_DEFAULT_REPLYTO')
                            subject('$PROJECT_DEFAULT_SUBJECT')
                            body('$PROJECT_DEFAULT_CONTENT')
                            attachmentsPattern('')
                            attachBuildLog(0)
                            contentType('project')
                            requiredFailureCount(0)
                        }
                        successTrigger {
                            recipientProviders {
                                developersRecipientProvider()
                                requesterRecipientProvider()
                            }
                            recipientList('$DEFAULT_RECIPIENTS')
                            replyTo('$PROJECT_DEFAULT_REPLYTO')
                            subject('$PROJECT_DEFAULT_SUBJECT')
                            body('$PROJECT_DEFAULT_CONTENT')
                            attachmentsPattern('')
                            attachBuildLog(0)
                            contentType('project')
                        }
                        buildingTrigger {
                            recipientProviders {
                                developersRecipientProvider()
                                listRecipientProvider()
                                requesterRecipientProvider()
                            }
                            recipientList('$DEFAULT_RECIPIENTS')
                            replyTo('$PROJECT_DEFAULT_REPLYTO')
                            subject('$PROJECT_DEFAULT_SUBJECT')
                            body('$PROJECT_DEFAULT_CONTENT')
                            attachmentsPattern('')
                            attachBuildLog(0)
                            contentType('project')
                        }
                    }
               }
           }
           configure {
                it / 'publishers' / 'org.advantech.jenkins.CascadedReportPublisher' {
                    'depth'('')
                }
           }
    }
    }
}
//create yocto rebuild project
job("yocto-rebuild") {
	description("""Build the images and the SDK self-extracting archive with BitBake.

You can chose branch or tag to build.""")
	keepDependencies(false)
	parameters {
		stringParam("REVISION_YOCTO", "HEAD", "")
	}
	scm {
		git {
			remote {
				github("ssh://git7@advgit.advantech.ca/repo/advantech/satnet-dev/yocto/meta-awl.git", "ssh")
				credentials("efedb983-6b76-42ee-ad7f-ed211b08657a")
			}
			branch("\${REVISION_YOCTO}")
		}
	}
	disabled(false)
	concurrentBuild(false)
	steps {
		shell(scriptFile.getText())
	}
	publishers {
		archiveArtifacts {
			pattern("artifacts/**")
			allowEmpty(true)
			onlyIfSuccessful(false)
			fingerprint(true)
			defaultExcludes(true)
		}
		extendedEmail {
			recipientList("\$DEFAULT_RECIPIENTS")
			contentType("default")
			defaultSubject("\$DEFAULT_SUBJECT")
			defaultContent("\${SCRIPT, template=\"groovy-html.template\"}")
			attachmentPatterns()
			preSendScript("\$DEFAULT_PRESEND_SCRIPT")
			attachBuildLog(false)
			compressBuildLog(false)
			replyToList("\$DEFAULT_REPLYTO")
			saveToWorkspace(false)
			disabled(false)
		}
	}
	configure {
		it / 'properties' / 'com.sonyericsson.rebuild.RebuildSettings' {
			'autoRebuild'('false')
			'rebuildDisabled'('false')
		}
	}
}

