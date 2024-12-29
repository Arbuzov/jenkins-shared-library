// getRelatedBranch(): Get target project fork and branch from source project URL and branch

def call (String relatedProjectOriginUrl, String apiSecretId = '') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  int pageNum = 0;
  int itemsOnPageCount = 0;     // items on page
  int forksCount = 0;
  int branchesCount = 0;
  env.putAt('GIT_RELATED_URL', '');
  env.putAt('GIT_RELATED_BRANCH', '');

  if (apiSecretId == '') throw new Exception("Attribute apiSecretId must be set.");
  if (env.CHANGE_ID == null) throw new Exception("Variable 'CHANGE_ID' is undefined. You must use getRelatedBranch() function with a GitLab merge request.");

  if (!(env.GITLAB_OA_SOURCE_BRANCH != null && env.GITLAB_OA_SOURCE_NAMESPACE != null)) {
    // Called by Jenkins when building a change request, but without involving GitLab,
    // for example when the user pressed Rescan GitLab project or Rebuild buttons.
    // We cannot use GitLab API in this case.
    // Just let the caller build the main branch of dependent projects in this case.
    println("WARNING: Building change request '" + env.CHANGE_ID.toString() + "' without going through GitLab, for some reason");
    println("WARNING: will build the main branch of dependent projects");
    return;
  }

  println("Looking for related branch for namespace '" + env.GITLAB_OA_SOURCE_NAMESPACE.toString() + "' branch '" + env.GITLAB_OA_SOURCE_BRANCH.toString() + "' in related target project '" + relatedProjectOriginUrl + "'...");

  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    URL url = new URL(relatedProjectOriginUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
    String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
    String encodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
    String forksUrlPrefix = gitLabAPIUrl+"/projects/"+encodedProject+"/forks/";
    String branchesUrlPrefix = '';
    String relatedUrl = '';
    String relatedBranchStr = '';

    forksCount = 0;
    for (pageNum = 1; pageNum <= 20; pageNum++) {
      String forksUrl = forksUrlPrefix + "?page=" + pageNum.toString() + "&private_token=" + gitLabAPISecret;

      def forksResponce = httpRequest(
        url: forksUrl,
        httpMode: 'GET',
        consoleLogResponseBody: false,
        contentType: 'APPLICATION_JSON'
      )
      def forksList = readJSON(text: forksResponce.getContent())

      itemsOnPageCount = 0;
      forksList.each { fork->
        itemsOnPageCount++;
        forksCount++;
        if (fork.namespace.name.equals(env.GITLAB_OA_SOURCE_NAMESPACE)) {
          branchesUrlPrefix = gitLabAPIUrl + "/projects/" + fork.path_with_namespace.replace('/', '%2F') + "/repository/branches/";
          relatedUrl = fork.ssh_url_to_repo;
          println("Found related fork '" + relatedUrl + "' on page " + pageNum.toString() + ", for related target project '" + relatedProjectOriginUrl + "'")
        }
      }

      if (itemsOnPageCount == 0) {
        break;
      }
    }

    println("Note: Looked at " + forksCount.toString() + " forks on " + pageNum.toString() + " pages, for related target project '" + relatedProjectOriginUrl + "'")

    if (!branchesUrlPrefix.equals('')) {
      branchesCount = 0;
      for (pageNum = 1; pageNum <= 20; pageNum++) {
        String branchesUrl = branchesUrlPrefix + "?per_page=100&page=" + pageNum.toString() + "&private_token=" + gitLabAPISecret;

        def branchesResponce = httpRequest(
          url: branchesUrl,
          httpMode: 'GET',
          consoleLogResponseBody: false,
          contentType: 'APPLICATION_JSON'
        )
        def branchesList = readJSON(text: branchesResponce.getContent())

        itemsOnPageCount = 0;
        branchesList.each { branch->
          itemsOnPageCount++;
          branchesCount++;
          if (branch.name.equals(env.GITLAB_OA_SOURCE_BRANCH)) {
            relatedBranchStr = env.GITLAB_OA_SOURCE_BRANCH;
            env.putAt('GIT_RELATED_BRANCH', relatedBranchStr);
            env.putAt('GIT_RELATED_URL', relatedUrl);
            println("Found related branch '" + relatedBranchStr + "' on page " + pageNum.toString() + ", for related fork '" + relatedUrl + "'")
          }
        }

        if (itemsOnPageCount == 0) {
          break;
        }
      }

      println("Note: Looked at " + branchesCount.toString() + " branches on " + pageNum.toString() + " pages, for related fork '" + relatedUrl + "'")
    }
  }
}
