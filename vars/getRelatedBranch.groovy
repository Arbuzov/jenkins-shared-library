// getRelatedBranch(): Get target project fork and branch from source project URL and branch

import groovy.json.JsonSlurper;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.conn.ssl.*;
import org.apache.http.message.BasicNameValuePair;
import org.apache.commons.io.IOUtils;

def call (String relatedProjectOriginUrl, String apiSecretId = '') {
  int forksCount = 0;
  int branchesCount = 0;
  env.putAt('GIT_RELATED_URL', '');
  env.putAt('GIT_RELATED_BRANCH', '');

  if (apiSecretId == '') throw new Exception("Attribute apiSecretId must be set.");
  if (env.GITLAB_OA_SOURCE_BRANCH == null) throw new Exception("Variable 'GITLAB_OA_SOURCE_BRANCH' is undefined. You must use this function with the GitLab merge request.");
  if (env.GITLAB_OA_SOURCE_NAMESPACE == null) throw new Exception("Variable 'GITLAB_OA_SOURCE_NAMESPACE' is undefined. You must use this function with the GitLab merge request.");

  println("Looking for related branch for namespace '" + env.GITLAB_OA_SOURCE_NAMESPACE.toString() + "' branch '" + env.GITLAB_OA_SOURCE_BRANCH.toString() + "' in related target project '" + relatedProjectOriginUrl + "'...");

  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    URL url = new URL(relatedProjectOriginUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
    String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
    String encodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
    String forksUrl = gitLabAPIUrl+"/projects/"+encodedProject+"/forks/?private_token=" + gitLabAPISecret;
    String branchesUrl = '';
    String relatedUrl = '';
    String relatedBranchStr = '';

    SSLContextBuilder builder = new SSLContextBuilder();
    builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
    SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
    CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

    HttpGet httpget = new HttpGet(forksUrl);
    HttpResponse response = httpclient.execute(httpget);
    HttpEntity entity = response.getEntity();
    ArrayList  forksList=(new JsonSlurper().parseText(IOUtils.toString(entity.getContent(), "UTF-8")));
    EntityUtils.consume(entity);

    forksCount = 0;
    forksList.each { fork->
      forksCount++;
      if (fork.namespace.name.equals(env.GITLAB_OA_SOURCE_NAMESPACE)) {
        branchesUrl = gitLabAPIUrl+"/projects/"+fork.path_with_namespace.replace('/', '%2F')+"/repository/branches/?per_page=100&private_token=" + gitLabAPISecret;
        relatedUrl = fork.ssh_url_to_repo;
        println("Found related fork '" + relatedUrl + "' for related target project '" + relatedProjectOriginUrl + "'")
      }
    }
    println("Note: Looked at " + forksCount.toString() + " forks for related target project '" + relatedProjectOriginUrl + "'")

    if (!branchesUrl.equals('')) {
      httpget = new HttpGet(branchesUrl);
      response = httpclient.execute(httpget);
      entity = response.getEntity();

      ArrayList  branchesList=(new JsonSlurper().parseText(IOUtils.toString(entity.getContent(), "UTF-8")));
      EntityUtils.consume(entity);

      branchesCount = 0;
      branchesList.each { branch->
        branchesCount++;
        if (branch.name.equals(env.GITLAB_OA_SOURCE_BRANCH)) {
          relatedBranchStr = env.GITLAB_OA_SOURCE_BRANCH;
          env.putAt('GIT_RELATED_BRANCH', relatedBranchStr);
          env.putAt('GIT_RELATED_URL', relatedUrl);
          println("Found related branch '" + relatedBranchStr + "' for related fork '" + relatedUrl + "'")
        }
      }

      println("Note: Looked at " + branchesCount.toString() + " branches for related fork '" + relatedUrl + "'")

    }
  }
}
