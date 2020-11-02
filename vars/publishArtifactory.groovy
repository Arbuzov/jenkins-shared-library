import static groovy.io.FileType.*;
import static groovy.io.FileVisitResult.*;

ArrayList call(String repoPath, String serverName = 'artifactory', String artifactsRegexp = '.*', String artifactVersion = '', Boolean flat = true) {
	
	def server = Artifactory.server(serverName);
	
	ArrayList selectedFiles = []
  
  String shaReport = ""
  
  URL shaURL = new URL(
    server.getUrl() + '/' +
    repoPath + '/' + (artifactVersion.equals('') ? '' : (artifactVersion + '/')) + '/' +
    'signature.sha'
  )
  
  String baseRepoName = shaURL
    .toURI()
    .normalize()
    .getPath()
    .replaceFirst(
      new URL(server.getUrl()).getPath()+'/',
      ''
    )
    .split('/')[0]

  String target = shaURL
    .toURI()
    .normalize()
    .getPath()
    .replace(
      new URL(server.getUrl()).getPath()+'/',
      ''
    )
    .replace(
      'signature.sha',
      ''
    )
	
  try {
    shaReport = shaURL.getText()
  } catch (Exception e) {
    shaReport = ""
  }
	
  def uploadSpec = """{
		\"files\": [
			{
				\"pattern\": \"(${artifactsRegexp})\",
				\"regexp\": \"true\",
				\"flat\": \"${flat}\",
				\"target\": \"${target}\"
			}
 		]
	}"""

	def buildInfo = server.upload(uploadSpec);
  buildInfo.getArtifacts().each{
    shaReport = shaReport + it.sha1 + ' '+it.localPath.replace(WORKSPACE, '.') + "\n"
    selectedFiles.add(new URL(server.getUrl()+'/'+baseRepoName+'/'+it.remotePath).toString())
  }
  
  server.publishBuildInfo(buildInfo);

  writeFile file: 'signature.sha', text: shaReport;

  uploadSpec = """{
		\"files\": [
			{
				\"pattern\": \"signature.sha\",
				\"regexp\": \"false\",
				\"target\": \"${target}\"
			}
 		]
	}"""
	
	buildInfo = server.upload(uploadSpec)
  server.publishBuildInfo(buildInfo)

	return selectedFiles
}

