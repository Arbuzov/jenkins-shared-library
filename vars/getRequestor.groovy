@NonCPS
String call() {
	def causes = currentBuild.rawBuild.getCauses();
	println causes.get(0);
	String username = causes.get(0).getUserName()
	return username;
}