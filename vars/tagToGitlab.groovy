def call (String gitUrl, String tagName, String ref, String message = '', String release = '',Boolean skipIfExist = true, String apiSecretId = 'gitlab-text-secret') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    if (gitLabAPISecret == null) throw new Exception("Variable 'gitLabAPISecret' is undefined. You must set this variable before function call.");

    URL url = new URL(gitUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
    String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";  
    String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
    URL tagsUrl = new URL (gitLabAPIUrl+"/projects/"+usrEncodedProject+"/repository/tags/?private_token=" + gitLabAPISecret);

    def tagsResponce = httpRequest(
      url: tagsUrl.toString(),
      httpMode: 'GET',
      consoleLogResponseBody: false,
      contentType: 'APPLICATION_JSON'
    )

    def tagList=readJSON(text: tagsResponce.getContent())

    Boolean tagExists = false;

    tagList.each { tag->
      if (tag.name.equals(tagName)) {
        tagExists = true;
      }
    }

    if (!tagExists || !skipIfExist) {
      String deleteURL = new URL(gitLabAPIUrl+"/projects/"+usrEncodedProject+"/repository/tags/"+tagName+"?private_token=" + gitLabAPISecret);
      httpRequest(
        url: deleteURL.toString(),
        httpMode: 'DELETE',
        consoleLogResponseBody: false,
        contentType: 'APPLICATION_JSON',
        validResponseCodes: "100:399,404",
        quiet: true
      )
      String postUrl = tagsUrl.toString()+
        '&tag_name='+java.net.URLEncoder.encode(tagName, "UTF-8")+
        '&ref='+java.net.URLEncoder.encode(ref, "UTF-8")+
        '&message='+java.net.URLEncoder.encode(message, "UTF-8")+
        '&release_description='+java.net.URLEncoder.encode(release, "UTF-8");
      httpRequest(
        url: postUrl,
        httpMode: 'POST',
        consoleLogResponseBody: true,
        contentType: 'APPLICATION_JSON',
        quiet: false
      )
    }
  }
}