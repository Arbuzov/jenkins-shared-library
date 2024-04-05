@NonCPS
String call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	def causes = currentBuild.rawBuild.getCauses();
	String username = causes.get(0).getUserName()
	return username;
}