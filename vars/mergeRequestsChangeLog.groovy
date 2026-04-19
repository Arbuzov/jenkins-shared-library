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

def call(String apiSecretId = '') {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  if (apiSecretId == '') throw new Exception("Attribute apiSecretId must be set.");
  collectMRChangsets(apiSecretId)

  def changeLogSets = currentBuild.rawBuild.changeSets
    print "changeLogSets type"
    print changeLogSets
    for (int i = 0; i < changeLogSets.size(); i++) {
      print "changeLogSets item type"
      print changeLogSets[i]
      def entries = changeLogSets[i].items
      for (int j = 0; j < entries.length; j++) {
        def entry = entries[j]
        print "entry type"
        print entry
        echo "${entry.commitId} by ${entry.author} on ${new Date(entry.timestamp)}: ${entry.msg}"
        def files = new ArrayList(entry.affectedFiles)
        for (int k = 0; k < files.size(); k++) {
            def file = files[k]
            echo "  ${file.editType.name} ${file.path}"
        }
      }
      //entries.add(entries[0])
    }
  currentBuild.rawBuild.changeSets.add(changeLogSets[0])
}

def collectMRChangsets(String apiSecretId) {
  withCredentials([string(credentialsId: apiSecretId, variable: 'gitLabAPISecret')]) {
    String GIT_URL = env.GITLAB_OA_TARGET_GIT_SSH_URL==null?env.GIT_URL:env.GITLAB_OA_TARGET_GIT_SSH_URL
    URL url = new URL(GIT_URL.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'))
    String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4"
    String encodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F')
    String mergeRequestUrl = gitLabAPIUrl+"/projects/"+encodedProject+"/merge_requests/?per_page=1000&order_by=updated_at&state=all&private_token=" + gitLabAPISecret

	  SSLContextBuilder builder = new SSLContextBuilder();
	  builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
	  SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
	  CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

    HttpGet httpget = new HttpGet(mergeRequestUrl)
	  HttpResponse response = httpclient.execute(httpget)
	  HttpEntity entity = response.getEntity()
    ArrayList  mergeRequestList=(new JsonSlurper().parseText(IOUtils.toString(entity.getContent(), "UTF-8")))
    EntityUtils.consume(entity)
    
    mergeRequestList.each { mergeRequest->
      print "ID " + mergeRequest.iid +
        "\nupdated " + mergeRequest.updated_at +
        "\nstate " + mergeRequest.state +
        "\ndescription " + mergeRequest.description
	  }
  }
}