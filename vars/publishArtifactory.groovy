import static groovy.io.FileType.*;
import static groovy.io.FileVisitResult.*;
import java.util.regex.*;
import java.util.Collections;
import org.apache.commons.io.FilenameUtils;

ArrayList call(String repoPath, String serverName = 'artifactory', String artifactsRegexp = '.*', String artifactVersion = '') {
	
	def server = Artifactory.server(serverName);
	
	//String beginUpload = "================= Begin upload =================\n";

	//String endUpload = "================= End upload =================\n";
	
	ArrayList selectedFiles = []
	
	//println beginUpload;
	
	def uploadSpec = """{
		\"files\": [
			{
				\"pattern\": \"(${artifactsRegexp})\",
				\"regexp\": \"true\",
				\"flat\": \"true\",
				\"target\": \"${repoPath}${artifactVersion.equals("")?"":"/"+artifactVersion+"/"}\"
			}
 		]
	}"""
	
	def buildInfo = server.upload(uploadSpec);
	
	server.publishBuildInfo(buildInfo);
	
	sleep 5;
	
	//println endUpload;
	
	String[] logArray = manager.build.getLog(100);
	
	for (int i = logArray.length-1; i > 0; i--) {
		
		def matches=logArray[i]=~/.*Deploying artifact: (.*)$/
		if(matches.find() && logArray[i].contains(FilenameUtils.normalize(repoPath+(artifactVersion.equals("")?"":"/"+artifactVersion+"/")))) {
			selectedFiles.add(matches.group(1));
		}
	}
	return selectedFiles
}

