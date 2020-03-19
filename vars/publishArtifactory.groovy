import static groovy.io.FileType.*;
import static groovy.io.FileVisitResult.*;

ArrayList call(String repoPath, String serverName = 'artifactory', String artifactsRegexp = '.*', String artifactVersion = '', Boolean flat = true) {
	
	def server = Artifactory.server(serverName);
	
	ArrayList selectedFiles = []
  
  String shaReport = ""

  String shaURLString = 
    server.getUrl() + '/' +
    repoPath + 
    (artifactVersion.equals('') ? '' : (artifactVersion + '/')) + 
    "signature.sha"
	
  try {
    shaReport = (new URL (shaURLString)).getText()
  } catch (Exception e) {
    shaReport = ""
  }
	
  def uploadSpec = """{
		\"files\": [
			{
				\"pattern\": \"(${artifactsRegexp})\",
				\"regexp\": \"true\",
				\"flat\": \"${flat}\",
				\"target\": \"${repoPath}${artifactVersion.equals("")?"":"/"+artifactVersion+"/"}\"
			}
 		]
	}"""
	
	def buildInfo = server.upload(uploadSpec);
	
  buildInfo.getArtifacts().each{
    shaReport = shaReport + it.sha1 + ' '+it.localPath.replace(WORKSPACE, '.') + "\n"
    selectedFiles.add(new URL(server.getUrl()+'/'+it.remotePath).toString())
  }
//	println(selectedFiles)
//  println("=========================================================");
//  println(shaReport);
//  println("=========================================================");
  
  server.publishBuildInfo(buildInfo);

  writeFile file: 'signature.sha', text: shaReport;

  uploadSpec = """{
		\"files\": [
			{
				\"pattern\": \"signature.sha\",
				\"regexp\": \"false\",
				\"target\": \"${repoPath}${artifactVersion.equals("")?"":"/"+artifactVersion+"/"}\"
			}
 		]
	}"""
	
	buildInfo = server.upload(uploadSpec)
  server.publishBuildInfo(buildInfo)

	return selectedFiles
}

