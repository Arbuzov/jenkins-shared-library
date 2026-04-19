// doCleanWorkspaceAfterStage(): Delete all workspace contents after stage
//
// Usage: Call this function in "post {}" section, "cleanup {}" condition.
//
// NOTE: First argument "isLastStage" should be set to "false" in most cases.
//       Argument "isLastStage" should be set to true only in the global
//       pipeline's"post {}" section, "cleanup {}" condition.
//
// NOTE: Second argument keepWhenFailure should be set to false in production.
//       Argument keepWhenFailure may be set to true by the caller to
//       debug a failed build.
//
// NOTE: When this function is called for a stage that is not last
//       stage, it keeps the file Dockerfile*, so that subsequent stages
//       do not fail due to missing Dockerfile file in workspace.

def call(boolean isLastStage = false, boolean keepWhenFailure = false) {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	def cleanWorkspaceFilesPatterns = [
		[pattern: 'Dockerfile*', type: 'EXCLUDE'],
	]

	if (!isLastStage) {
		// NOTE: Keep files in "cleanWorkspaceFilesPatterns"
		// because this is NOT the last stage on this agent
		echo "Deleting all workspace contents after stage, in stage ${env.STAGE_NAME} ..."
		if (!keepWhenFailure) {
			cleanWs deleteDirs: true, disableDeferredWipeout: true,
					notFailBuild: true,
					patterns: cleanWorkspaceFilesPatterns
		} else {
			echo "WARNING: will not delete workspace contents in case of failed build"
			cleanWs deleteDirs: true, disableDeferredWipeout: true,
					notFailBuild: true,
					cleanWhenSuccess: true,
					cleanWhenFailure: false, cleanWhenUnstable: false,
					cleanWhenAborted: false, cleanWhenNotBuilt: false,
					patterns: cleanWorkspaceFilesPatterns
		}
	} else {
		// NOTE: Clean everything because this is last stage on this agent
		echo "Deleting all workspace contents after last stage on agent, in stage ${env.STAGE_NAME} ..."
		if (!keepWhenFailure) {
			// NOTE: use deferred wipeout
			cleanWs notFailBuild: true
		} else {
			echo "WARNING: will not delete workspace contents in case of failed build"
			cleanWs deleteDirs: true, disableDeferredWipeout: true,
					notFailBuild: true,
					cleanWhenSuccess: false,
					cleanWhenFailure: false, cleanWhenUnstable: false,
					cleanWhenAborted: false, cleanWhenNotBuilt: false
		}
	}

	return
}
