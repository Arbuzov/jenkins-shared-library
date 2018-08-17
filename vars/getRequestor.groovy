@NonCPS
String call() {
	causes = currentBuild.rawBuild.getCauses();
	println causes;
	username = causes.get(0).getUserName()
	return username;
}