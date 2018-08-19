def call() {
	String result = "";
	String uriStr = "";
	
	if (env.getAt('gitlabSourceRepoURL')!=null && !env.getAt('gitlabSourceRepoURL').equals("")) {
		println "Gitlab probably MR"
		uriStr = env.getAt('gitlabSourceRepoURL');
	} else {
		if (env.getAt('GIT_URL')!=null && !env.getAt('GIT_URL').equals("")) {
			uriStr = env.getAt('GIT_URL');
			println "generic Git NO MR ${env.getAt('GIT_URL')}"
		} else {
			uriStr = env.getAt('GIT_URL_1');
			println "Git NO MR two sources"
		}
	}
	
	if (uriStr!=null && !uriStr.equals("")) {
		result = uriStr.split(":").getAt(1).split("/").getAt(0);
	}
	return result.toLowerCase();
}
