import static groovy.io.FileType.*;
import static groovy.io.FileVisitResult.*;
import java.util.regex.*;

ArrayList call(String repoPath, String serverName = 'artifactory', String artifactsRegexp = '.*', String artifactVersion = '') {
	def server = Artifactory.server(serverName);
	ArrayList selectedFiles = []
	
	def addToList = {
		selectedFiles << server.getUrl() + "/" + repoPath +(artifactVersion.equals("")?"":artifactVersion+"/")+it.name
	}

	def groovySrcDir = new File(pwd());
	def uploadSpec = """{
		"files\": [
			{
				\"pattern\": \"(${artifactsRegexp})\",
				\"regexp\": \"true\",
				\"flat\": \"true\",
				\"target\": \"${repoPath}${artifactVersion.equals("")?"":"/"+artifactVersion+"/"}\"
			}
 		]
	}"""
	
	def buildInfo = server.upload(uploadSpec);
	
	groovySrcDir.traverse type: FILES, visit: addToList, nameFilter: Pattern.compile("${artifactsRegexp}")
	
	server.publishBuildInfo(buildInfo);
	
	return selectedFiles
}
