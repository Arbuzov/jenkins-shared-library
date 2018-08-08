import org.apache.commons.io.FilenameUtils;
def call() {
	def strUrl = sh (
		script: "git remote get-url --all origin",
		returnStdout: true
	).trim();
	def upperName = FilenameUtils.getBaseName(strUrl).toUpperCase();
	def destBreif = 'buildinfo-brief-'+FilenameUtils.getBaseName(strUrl)+'.txt';
	def destFull = 'buildinfo-full-'+FilenameUtils.getBaseName(strUrl)+'.txt';
	
	def commiterDate=sh (
		script: "git log -n 1 --date=local --pretty='%ci' HEAD^..HEAD",
		returnStdout: true
	).trim();
	def commiterName=sh (
		script: "git log -n 1 --date=local --pretty='%cn' HEAD^..HEAD",
		returnStdout: true
	).trim();
	def commitMessage=sh (
		script: "git log -n 1 --date=local --pretty='%s' HEAD^..HEAD",
		returnStdout: true
	).trim();
	
	def commitId=sh (
		script: "git log --format='%H' -n 1",
		returnStdout: true
	).trim()
	
	def briefContent = "";
	def fullContent = "";
	
	if (fileExists("${pwd()}/README.md")) {
		briefContent = (readFile("${pwd()}/README.md")).split("\n")[0]+"\n\n";
		fullContent = (readFile("${pwd()}/README.md")).split("\n")[0]+"\n\n";
	}
	
	briefContent += "${upperName}_BUILD_NUMBER=\"${env.BUILD_NUMBER}\"\n"
	briefContent += "${upperName}_COMMIT_ID=\"${commitId}\"\n"
	briefContent += "${upperName}_COMMITTER_DATE=\"${commiterDate}\"\n\n"
	
	fullContent += "${upperName}_BUILD_NUMBER=\"${env.BUILD_NUMBER}\"\n"
	fullContent += "${upperName}_COMMIT_ID=\"${commitId}\"\n"
	fullContent += "${upperName}_COMMITTER_DATE=\"${commiterDate}\"\n\n"
	
	fullContent += "${upperName}_REPO=\"${strUrl}\"\n"
	fullContent += "${upperName}_COMMITTER_NAME=\"${commiterName}\"\n"
	fullContent += "${upperName}_COMMIT_MESSAGE=\"${commitMessage}\"\n\n"
	
	
	dir ("${WORKSPACE}/logs") {
		writeFile file: destBreif, text: briefContent;
		writeFile file: destFull, text: fullContent;
	}
	
	publishHTML([
		allowMissing: false,
		alwaysLinkToLastBuild: false,
		keepAll: false,
		reportDir: "${WORKSPACE}",
		reportFiles: "logs/"+destBreif,
		reportName: "${upperName} Breif info",
		reportTitles: "${upperName} Breif info"
	]);
	
	publishHTML([
		allowMissing: false,
		alwaysLinkToLastBuild: false,
		keepAll: false,
		reportDir: "${WORKSPACE}",
		reportFiles: "logs/"+destFull,
		reportName: "${upperName} Full info",
		reportTitles: "${upperName} Full info"
	]);
	
}