import groovy.json.JsonOutput;

def call (String gitUrl, String mrIid, String body = '', Boolean skipIfExist = true, String apiSecretId = gitLabAPISecret, Boolean debug = false) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  if (gitLabAPISecret == null) throw new Exception("Variable 'gitLabAPISecret' is undefined. You must set this variable before function call.");
  
  println "======== Comment to GitLab: " + body.toString() + " ========"
  if (mrIid == null) {
    return;
  }
  if (mrIid.equals('') || mrIid.equals('null')) {
    return;
  }
  if (gitUrl == null) {
    return;
  }
  if (gitUrl.equals('') || gitUrl.equals('null')) {
    return;
  }

  URL url = new URL(gitUrl.replaceFirst(/:(?=\w)/,'/').replace('git@', 'https://'));
  String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
  String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
  URL mrUrl = new URL (gitLabAPIUrl+"/projects/"+usrEncodedProject+"/merge_requests/"+mrIid+"/notes/?private_token=" + gitLabAPISecret);

  println "Leaving comment to URL: " + mrUrl.toString()

  httpRequest(
    url: mrUrl.toString(),
    httpMode: 'POST',
    consoleLogResponseBody: debug,
    contentType: 'APPLICATION_JSON',
    requestBody: JsonOutput.toJson([body: body])
  )
}

def call (Object args) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def gitUrl = args.gitUrl ?: env.GITLAB_OA_TARGET_GIT_SSH_URL
  def mrIid = args.mrIid ?: env.CHANGE_ID
  def comment = args.comment ?: ''
  if (comment == null || comment == '') throw new Exception("Variable 'comment' is empty or undefined.");
  def debug = args.debug != null ? args.debug : false
  def skipIfExist = args.skipIfExist != null ? args.skipIfExist : true
  def apiSecretId = args.apiSecretId ?: 'gitlab-text-secret'
  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    return this.call(gitUrl, mrIid, comment, skipIfExist, gitLabAPISecret, debug)
  }
}
