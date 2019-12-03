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
  
  env.putAt('GIT_RELATED_URL', '');
  env.putAt('GIT_RELATED_BRANCH', '');

  if (apiSecretId == '') throw new Exception("Attribute apiSecretId must be set.");
  if (env.gitlabSourceBranch == null) throw new Exception("Variable 'gitlabSourceBranch' is undefined. You must use this function with the GitLab merge request.");
  if (env.gitlabSourceNamespace == null) throw new Exception("Variable 'gitlabSourceNamespace' is undefined. You must use this function with the GitLab merge request.");

  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    URL url = new URL(relatedProjectOriginUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
	  String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
    String encodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
    String forksUrl = gitLabAPIUrl+"/projects/"+encodedProject+"/forks/?private_token=" + gitLabAPISecret;
    String branchesUrl = '';
    String relatedUrl = '';
   

	  SSLContextBuilder builder = new SSLContextBuilder();
	  builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
	  SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
	  CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

  	HttpGet httpget = new HttpGet(forksUrl);
	  HttpResponse response = httpclient.execute(httpget);
	  HttpEntity entity = response.getEntity();
    ArrayList  forksList=(new JsonSlurper().parseText(IOUtils.toString(entity.getContent(), "UTF-8")));
    EntityUtils.consume(entity);
    
    forksList.each { fork->
      if (fork.namespace.name.equals(env.gitlabSourceNamespace)) {
        branchesUrl = gitLabAPIUrl+"/projects/"+fork.path_with_namespace.replace('/', '%2F')+"/repository/branches/?private_token=" + gitLabAPISecret;
        relatedUrl = fork.ssh_url_to_repo;
      }
	  }

    if (!branchesUrl.equals('')) {
	    httpget = new HttpGet(branchesUrl);
	    response = httpclient.execute(httpget);
	    entity = response.getEntity();
	
	    ArrayList  branchesList=(new JsonSlurper().parseText(IOUtils.toString(entity.getContent(), "UTF-8")));
      EntityUtils.consume(entity);
	
	    branchesList.each { branch->
		    if (branch.name.equals(env.gitlabSourceBranch)) {
          env.putAt('GIT_RELATED_BRANCH', env.gitlabSourceBranch);
          env.putAt('GIT_RELATED_URL', relatedUrl);
		    }
	    }
    }
  }
}