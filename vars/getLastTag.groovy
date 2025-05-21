def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	def isWindows = true
	try {
		bat ""
	} catch (java.io.IOException exeption) {
		isWindows = false
	}
	if (!isWindows) {
		return (
				sh (
				script: 'git describe --tags --abbrev=0',
				returnStdout: true
				)
				).trim();
	}
	else {
		return (
				bat (
				script: '@git describe --tags --abbrev=0',
				returnStdout: true
				)
				).trim();
	}
}
