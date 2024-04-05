// doCleanWorkspaceBeforeStage(): Delete all workspace contents before stage
//
// Usage: Call this function at the beginning of a stage that has option
//       "skipDefaultCheckout true" and has no "checkout" step and
//       no "git" step. This function preserves the necessary files for
//       subsequent stages, when deleting all other files.
//
// NOTE: if you are in a stage that has "checkout" step or
//       "git" step, call the function doCleanWorkspaceBeforeCheckout()
//       instead. That function removes all files right before the SCM checkout.

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	echo "Deleting all workspace contents before stage, in stage ${env.STAGE_NAME} ..."

	def cleanWorkspaceFilesPatterns = [
		[pattern: 'Dockerfile*', type: 'EXCLUDE'],
	]

	// NOTE: Keep files in "cleanWorkspaceFilesPatterns"
	// because this is NOT the last stage on this agent
	cleanWs deleteDirs: true, disableDeferredWipeout: true,
			patterns: cleanWorkspaceFilesPatterns
}
