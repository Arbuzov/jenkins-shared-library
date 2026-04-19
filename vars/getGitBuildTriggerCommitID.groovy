// getGitBuildTriggerCommitID(): Get commit ID of the commit that triggered this build
//
// Recommended variable name: GIT_BUILD_TRIGGER_COMMIT_ID

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	String buildTriggerCommitId = '2000000000000000000000000000000000000000'

	if (env.getAt('GIT_COMMIT') == null) {
		return buildTriggerCommitId
	}

	buildTriggerCommitId = env.GIT_COMMIT

	if (env.getAt('GITLAB_OA_LAST_COMMIT_ID') != null) {
		if (!(env.GITLAB_OA_LAST_COMMIT_ID.equals(''))) {
			buildTriggerCommitId = env.GITLAB_OA_LAST_COMMIT_ID
		}
	}

	return buildTriggerCommitId
}
