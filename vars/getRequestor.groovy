@NonCPS
String call() {
	def causes = currentBuild.rawBuild.getCauses();
	String username = causes.get(0).getUserName()
	return username;
}