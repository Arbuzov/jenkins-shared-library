// saveEnvVarsForChangeRequest(): Save environment variables for change request
//
// Usage: Call this function in steps block (or a script block under
// steps block) of a stage with the condition:
//
//	when {
//		changeRequest()
//	}
//

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	int varsCount = 0;

	if (env.getAt('CHANGE_ID') == null) {
		return;
	}

	String[] varNames = [
		'CHANGE_ID',
		'CHANGE_AUTHOR',
		'CHANGE_AUTHOR_DISPLAY_NAME',
		'CHANGE_AUTHOR_EMAIL',
		'CHANGE_BRANCH',
		'CHANGE_FORK',
		'CHANGE_TARGET',
		'CHANGE_TITLE',
		'CHANGE_URL',
		'GITLAB_OA_LAST_COMMIT_ID',
		'GITLAB_OA_LAST_COMMIT_URL',
		'GITLAB_OA_SOURCE_BRANCH',
		'GITLAB_OA_SOURCE_DEFAULT_BRANCH',
		'GITLAB_OA_SOURCE_GIT_HTTP_URL',
		'GITLAB_OA_SOURCE_GIT_SSH_URL',
		'GITLAB_OA_SOURCE_HOMEPAGE',
		'GITLAB_OA_SOURCE_HTTP_URL',
		'GITLAB_OA_SOURCE_NAME',
		'GITLAB_OA_SOURCE_NAMESPACE',
		'GITLAB_OA_SOURCE_PATH_WITH_NAMESPACE',
		'GITLAB_OA_SOURCE_PROJECT_ID',
		'GITLAB_OA_SOURCE_SSH_URL',
		'GITLAB_OA_SOURCE_URL',
		'GITLAB_OA_SOURCE_VISIBILITY_LEVEL',
		'GITLAB_OA_SOURCE_WEB_URL',
		'GITLAB_OA_TARGET_BRANCH',
		'GITLAB_OA_TARGET_DEFAULT_BRANCH',
		'GITLAB_OA_TARGET_DESCRIPTION',
		'GITLAB_OA_TARGET_GIT_HTTP_URL',
		'GITLAB_OA_TARGET_GIT_SSH_URL',
		'GITLAB_OA_TARGET_HOMEPAGE',
		'GITLAB_OA_TARGET_HTTP_URL',
		'GITLAB_OA_TARGET_NAME',
		'GITLAB_OA_TARGET_NAMESPACE',
		'GITLAB_OA_TARGET_PATH_WITH_NAMESPACE',
		'GITLAB_OA_TARGET_PROJECT_ID',
		'GITLAB_OA_TARGET_SSH_URL',
		'GITLAB_OA_TARGET_VISIBILITY_LEVEL',
		'GITLAB_OA_TARGET_WEB_URL',
	];

	varNames.each { varName ->
		String varValue = env.getAt(varName);
		if (varValue != null) {
			if (false) {
				println("debug: Saving environment variable " + varName + "='" + varValue + "'");
			}
			env.putAt(varName, varValue);
			varsCount++;
		}
	}

	println("Note: Saved " + varsCount.toString() + " environment variables for restarting from stage");
}
