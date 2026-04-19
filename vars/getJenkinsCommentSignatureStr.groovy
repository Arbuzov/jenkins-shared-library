// getJenkinsCommentSignatureStr(): Get signature for Jenkins comments
//
// Usage: Use it in post blocks or at the end of a stage, for example:
//
//	withCredentials([string(credentialsId: 'gitlab-text-secret', variable: 'gitLabAPISecret')]) {
//		noteToMrGitlab(
//			"${env.GITLAB_OA_TARGET_GIT_SSH_URL}",
//			"${env.CHANGE_ID}",
//			"Completed tests for MR" + "\n\n" + getJenkinsCommentSignatureStr() + "\n\n"
//		)
//	}
//

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	String triggerCommitId = getGitBuildTriggerCommitID()
	String signatureStr = "Jenkins"
	String buildNumberStr = ""
	String changeReqStr = ""
	String projectNameStr = ""

	if (env.getAt('BUILD_NUMBER') != null) {
		buildNumberStr = "${env.BUILD_NUMBER}"
		if (env.getAt('BUILD_URL') != null) {
			buildNumberStr = "[${buildNumberStr}](${env.BUILD_URL})"
		}
		buildNumberStr = "build " + buildNumberStr
	}

	if (env.getAt('CHANGE_ID') != null) {
		changeReqStr = "!${env.CHANGE_ID}"
		if (env.getAt('CHANGE_URL') != null) {
			changeReqStr = "[${changeReqStr}](${env.CHANGE_URL})"
		}
		if (env.getAt('CHANGE_TITLE') != null) {
			changeReqStr = changeReqStr + " \"${env.CHANGE_TITLE}\""
		}
		changeReqStr = "merge request " + changeReqStr
	}

	if (changeReqStr) {
		if (env.getAt('GITLAB_OA_TARGET_NAME') != null) {
			projectNameStr = "${env.GITLAB_OA_TARGET_NAME}"
			if (env.getAt('GITLAB_OA_TARGET_NAMESPACE') != null) {
				projectNameStr = "${env.GITLAB_OA_TARGET_NAMESPACE} / ${env.GITLAB_OA_TARGET_NAME}"
			}
			if (env.getAt('GITLAB_OA_TARGET_WEB_URL') != null) {
				projectNameStr = "[${projectNameStr}](${env.GITLAB_OA_TARGET_WEB_URL})"
			}
		}
	}

	if (buildNumberStr) {
		signatureStr = signatureStr + ", ${buildNumberStr}"
	}
	if (changeReqStr) {
		signatureStr = signatureStr + ", ${changeReqStr}"
		if (projectNameStr) {
			signatureStr = signatureStr + " at ${projectNameStr}"
		}
	}
	if (triggerCommitId) {
		signatureStr = signatureStr + ", triggered by commit ID ${triggerCommitId}"
	}
	signatureStr = "*" + signatureStr + "*"

	return signatureStr
}
