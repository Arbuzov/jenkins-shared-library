import hudson.model.*;
import java.util.regex.*;

/*********************************************/

String hostLabel = 'jslave-step1';

String genericBranch = 'fido-plinux';

String projectName = 'meta-awl.git';

String webRootUrl = 'https://git.advantech.ca/cgit.cgi'

String gitRootUrl = 'ssh://git7@192.168.21.19'

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

hudson.FilePath workspace = (new hudson.FilePath(new File("${WORKSPACE}")));

def remoteConfig = (SEED_JOB.getSCMs())[0].getUserRemoteConfigs().get(0);

String repoCreds = remoteConfig.getCredentialsId();

File scriptFile = new File("${workspace}/firmware/yocto/script.sh")

projectRepos.each{
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
                    browser {
                        cGit {
                            repoUrl(gitUrl.replace(gitRootUrl, webRootUrl))
                        }
                    }
                }
           }
           label(hostLabel)
           triggers {
                scm("H/5 * * * *")
           }
           steps {
                shell(scriptFile.getText())
           }
           publishers {
               textFinder(/(ERROR message|ERROR 404)/, '', true, false, true)
               archiveArtifacts {
                    pattern("artifacts/**, **.log,/home/build/fsdk/v1.9/QorIQ-SDK-V1.9-20151210-yocto/build_awl-idu7000/tmp/work/**/log.do_configure.*")
                    fingerprint(true)
                    allowEmpty(false)
               }
               extendedEmailPublisher {
                    project_recipient_list('$DEFAULT_RECIPIENTS'+"\nAlex.Landovskis@advantechwireless.com")
                    project_default_subject('$DEFAULT_SUBJECT')
                    project_replyto('$DEFAULT_REPLYTO')
                    project_content_type('both')
                    project_default_content('${SCRIPT, template="groovy-html.template"}')
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
                            }
                            recipientList('Serge.Arbuzov@advantechwireless.com, Igor.Mordkovitch@advantechwireless.com')
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
                            }
                            recipientList('Serge.Arbuzov@advantechwireless.com')
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
                            }
                            recipientList('Igor.Mordkovitch@advantechwireless.com, Serge.Arbuzov@advantechwireless.com')
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
//create yocto rebuild project
job('yocto-rebuild-autogenerated') {
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
    configure { project ->
        project / 'properties' / 'config' / 'userIdCauseRestriction' {
            'prohibitManualLaunch'('true')
        }
    }
    parameters {
        stringParam('REVISION_YOCTO', 'HEAD', '')
    }
    scm {
        git {
            remote {
            url(projectRepos.get('origin'))
            credentials(repoCreds)
        }
        branch('${REVISION_YOCTO}')
            browser {
                cGit {
                    repoUrl(projectRepos.get('origin').replace(gitRootUrl, webRootUrl))
                }
            }
        }
    }
    steps {
        shell(scriptFile.getText())
    }
    publishers {
        textFinder(/(ERROR message|ERROR 404)/, '', true, false, true)
        archiveArtifacts {
            pattern("artifacts/**, **.log,/home/build/fsdk/v1.9/QorIQ-SDK-V1.9-20151210-yocto/build_awl-idu7000/tmp/work/**/log.do_configure.*")
            fingerprint(true)
            allowEmpty(false)
        }
        extendedEmailPublisher {
                    project_recipient_list('$DEFAULT_RECIPIENTS')
                    project_default_subject('$DEFAULT_SUBJECT')
                    project_replyto('$DEFAULT_REPLYTO')
                    project_content_type('both')
                    project_default_content('${SCRIPT, template="groovy-html.template"}')
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
                            }
                            recipientList('Sergey.Arbuzov@advantechwireles.com')
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
    keepDependencies()
}

