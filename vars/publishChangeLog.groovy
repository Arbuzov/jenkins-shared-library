import org.apache.commons.io.FilenameUtils;
def call(ArrayList<String> paths, String version = '') {
	def destFile = 'changelog.txt';
	def fullLog = 'full.log'
	sh 'install -d logs'
	def reponames = {};
	def tags = sh (
		script: "git tag --sort=-committerdate --merged",
		returnStdout: true
	).tokenize("\n");
	
	String logList = ''
	
	paths.each {
		def localTags = sh (
			script: "cd ${it} && git tag --sort=-committerdate",
			returnStdout: true
		).tokenize("\n");
		def strUrl = sh (
			script: "cd ${it} && git remote get-url --all origin",
			returnStdout: true
		);
		reponames[it]=FilenameUtils.getBaseName(strUrl).toUpperCase();
		sh 'cd ' + it + ' && git log --pretty=format:"%cI : %h - %an : ' + FilenameUtils.getBaseName(strUrl).toUpperCase() + ' %s" HEAD >> '+pwd()+'/logs/' + FilenameUtils.getBaseName(strUrl) + '.log'
		logList += (pwd()+'/logs/' + FilenameUtils.getBaseName(strUrl) + '.log ')
		tags = tags.intersect(localTags)
	}
	
	sh "sort --reverse --output=logs/${fullLog} --k=1,19 ${logList}"
	
	workspace = pwd();
	def firstTag="HEAD"
	def logContent = "";
	tags.each{tag->
		def step ="";
		paths.each {path->
			try {
				step += sh (
					script: "cd ${path} && git log --pretty=format:\" * %cI : %h - %an : ${reponames[path]} %s\" ${tag}..${firstTag}|sort --reverse --k=1,19|grep '#'",
					returnStdout: true
				)
			} catch (error) {
				println error;
				println step;
			}
		}
		if (!step.equals("")) {
			logContent += "\n## ${firstTag=='HEAD'?version:firstTag}-${tag}\n"
			step = step.split("\n").sort(false).reverse().join("\n");
			logContent += step;
			firstTag=tag;
		}
	}
	dir ('logs') {
		writeFile file:destFile, text: logContent;
	}
	
	publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '', reportFiles: 'logs/'+fullLog, reportName: 'Full Git Log', reportTitles: 'Full Git Log']);
	publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '', reportFiles: 'logs/'+destFile, reportName: 'Change Log', reportTitles: 'Change Log']);
}