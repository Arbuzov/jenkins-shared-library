// doCleanWorkspaceBeforeCheckout(): Delete all workspace contents before checkout
//
// Usage: Call this function at the beginning of a stage that has a
// "checkout" step or "git" step
//
// NOTE: if you are in a stage that has option
//       "skipDefaultCheckout true" and has no "checkout" step and
//       no "git" step, call the function doCleanWorkspaceBeforeStage()
//       instead. That function preserves the necessary files for
//       subsequent stages.

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	echo "Deleting all workspace contents before checkout, in stage ${env.STAGE_NAME} ..."
	cleanWs deleteDirs: true, disableDeferredWipeout: true
}
