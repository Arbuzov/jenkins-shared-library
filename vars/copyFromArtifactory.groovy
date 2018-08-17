import static groovy.io.FileType.*;
import static groovy.io.FileVisitResult.*;
import java.util.regex.*;
import groovy.json.JsonSlurper;
import org.apache.commons.io.FilenameUtils;

@NonCPS
ArrayList call(String repoPath, String serverName = 'artifactory', String destDirectory = '', String artifactVersion = '') {
	def server = Artifactory.server(serverName);
	
	ArrayList selectedFiles = []
	
	String storageUrl = server.getUrl()+ '/api/storage/' + repoPath + artifactVersion
	
	def targetItem = (new JsonSlurper().parse(new URL (storageUrl)))
	
	if (artifactVersion.equals('')) {
		def created = "";
		def latestItem = {};
		ArrayList children = (new JsonSlurper().parse(new URL (storageUrl))).children;
		children.each {
			if (it.folder==true) {
				def itemInfo = (new JsonSlurper().parse(new URL (storageUrl + it.uri)));
				if (itemInfo.created>created) {
					latestItem = itemInfo;
					created = itemInfo.created;
				}
			}
		}
		storageUrl = latestItem.uri;
		targetItem = latestItem;
	}
	def itemInfo = null;
	targetItem.children.each {
		if (it.folder==false) {
			itemInfo = (new JsonSlurper().parse(new URL (storageUrl + it.uri)));
			selectedFiles.add(itemInfo.downloadUri);
			fileOperations(
				[
					fileDownloadOperation(
						password: server.getPassword(),
						targetFileName: FilenameUtils.getName(new URL(itemInfo.downloadUri).getPath()),
						targetLocation: destDirectory,
						url: itemInfo.downloadUri,
						userName: server.getUsername()
					)
				]
			)
		}
	}
	
	return selectedFiles
}
