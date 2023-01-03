import groovy.json.JsonOutput;

def call (String gitUrl, String mrIid, String body = '', Boolean skipIfExist = true) {
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

  URL url = new URL(gitUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
  String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
  String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
  URL mrUrl = new URL (gitLabAPIUrl+"/projects/"+usrEncodedProject+"/merge_requests/"+mrIid+"/notes/?private_token=" + gitLabAPISecret);

  println "Leaving comment to URL: " + mrUrl.toString()

  httpRequest(
    url: mrUrl.toString(),
    httpMode: 'POST',
    consoleLogResponseBody: true,
    contentType: 'APPLICATION_JSON',
    requestBody: JsonOutput.toJson([body: body])
  )
}
