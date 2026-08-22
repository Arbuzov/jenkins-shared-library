def call (String gitUrl, String tagName, String ref, String message = '', String release = '',Boolean skipIfExist = true, String apiSecretId = 'gitlab-text-secret') {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    if (gitLabAPISecret == null) throw new Exception("Variable 'gitLabAPISecret' is undefined. You must set this variable before function call.");

    URL url = new URL(gitUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
    String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";  
    String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
    String tagsUrl = gitLabAPIUrl+"/projects/"+usrEncodedProject+"/repository/tags";

    def tagsResponce = httpRequest(
      url: tagsUrl,
      httpMode: 'GET',
      consoleLogResponseBody: false,
      contentType: 'APPLICATION_JSON',
      customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabAPISecret]]
    )

    def tagList=readJSON(text: tagsResponce.getContent())

    Boolean tagExists = false;

    tagList.each { tag->
      if (tag.name.equals(tagName)) {
        tagExists = true;
      }
    }

    if (!tagExists || !skipIfExist) {
      String encodedTagName = java.net.URLEncoder.encode(tagName, "UTF-8");
      String deleteURL = "${gitLabAPIUrl}/projects/${usrEncodedProject}/repository/tags/${encodedTagName}";
      String tagDetailsUrl = deleteURL

      def responseStatus = { response ->
        if (response == null) {
          return 0
        }
        if (response instanceof Map && response.containsKey('status')) {
          return (response.status ?: 0) as Integer
        }
        try {
          return (response.status ?: 0) as Integer
        } catch (Exception ignored) {
          // Ignore.
        }
        try {
          return (response.getStatus() ?: 0) as Integer
        } catch (Exception ignored) {
          // Ignore.
        }
        return 0
      }

      def responseContent = { response ->
        if (response == null) {
          return ''
        }
        if (response instanceof Map && response.containsKey('content')) {
          return (response.content ?: '').toString()
        }
        try {
          return (response.getContent() ?: '').toString()
        } catch (Exception ignored) {
          // Ignore.
        }
        try {
          return (response.content ?: '').toString()
        } catch (Exception ignored) {
          // Ignore.
        }
        return ''
      }

      def shorten = { text ->
        String value = text == null ? '' : text.toString()
        return value.length() > 600 ? value.substring(0, 600) + '...' : value
      }

      def createTag = { createUrl ->
        return httpRequest(
          url: createUrl,
          httpMode: 'POST',
          consoleLogResponseBody: false,
          contentType: 'APPLICATION_JSON',
          customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabAPISecret]],
          validResponseCodes: '100:499',
          quiet: true
        )
      }

      httpRequest(
        url: deleteURL,
        httpMode: 'DELETE',
        consoleLogResponseBody: false,
        validResponseCodes: "100:399,404",
        contentType: 'APPLICATION_JSON',
        customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabAPISecret]],
        quiet: true
      )

      String postUrl = tagsUrl +
        '?tag_name=' + java.net.URLEncoder.encode(tagName, "UTF-8") +
        '&ref=' + java.net.URLEncoder.encode(ref, "UTF-8")
      if (message?.trim()) {
        postUrl += '&message=' + java.net.URLEncoder.encode(message, "UTF-8")
      }

      String postUrlWithRelease = postUrl
      if (release?.trim()) {
        postUrlWithRelease += '&release_description=' + java.net.URLEncoder.encode(release, "UTF-8")
      }

      def attempts = [postUrlWithRelease]
      if (postUrlWithRelease != postUrl) {
        attempts << postUrl
      }

      Boolean created = false
      String createErrors = ''
      attempts.each { attemptUrl ->
        if (created) {
          return
        }

        def createResponse = createTag(attemptUrl)
        Integer status = responseStatus(createResponse)
        String body = responseContent(createResponse)

        if (status >= 200 && status < 400) {
          created = true
          return
        }

        // If release creation fails, retry once without release_description.
        if (status == 400 && release?.trim() && attemptUrl == postUrlWithRelease) {
          createErrors += "create(status=${status}, with_release=true, body=${shorten(body)}); "
          return
        }

        if (status == 400) {
          def currentTagResponse = httpRequest(
            url: tagDetailsUrl,
            httpMode: 'GET',
            consoleLogResponseBody: false,
            contentType: 'APPLICATION_JSON',
            customHeaders: [[name: 'PRIVATE-TOKEN', value: gitLabAPISecret]],
            validResponseCodes: '100:499',
            quiet: true
          )
          Integer currentTagStatus = responseStatus(currentTagResponse)
          if (currentTagStatus == 200) {
            def currentTagInfo = readJSON(text: responseContent(currentTagResponse))
            // an annotated tag's target is the tag object, not the commit it points at
            String currentTarget = (currentTagInfo?.commit?.id ?: currentTagInfo?.target)?.toString()
            if (currentTarget == ref?.toString()) {
              // Tag already points to the desired ref: treat as idempotent success.
              created = true
              return
            }
          }
          createErrors += "create(status=${status}, with_release=${attemptUrl == postUrlWithRelease}, body=${shorten(body)}); "
          return
        }

        createErrors += "create(status=${status}, with_release=${attemptUrl == postUrlWithRelease}, body=${shorten(body)}); "
      }

      if (!created) {
        error(
          "Failed to create GitLab tag '${tagName}' for ref '${ref}'. " +
          "Checked endpoint '${tagsUrl}'. Details: ${createErrors}"
        )
      }
    }
  }
}
