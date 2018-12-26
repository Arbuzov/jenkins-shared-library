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


def call (String gitUrl, String tagName, String ref, String message = '', String release = '',Boolean skipIfExist = true) {
	
	URL url = new URL(gitUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
	String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";	
	String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
	URL tagsUrl = new URL (gitLabAPIUrl+"/projects/"+usrEncodedProject+"/repository/tags/?private_token=" + gitLabAPISecret);
	
	SSLContextBuilder builder = new SSLContextBuilder();
	builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
	SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
	CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
	
	HttpGet httpget = new HttpGet(tagsUrl.toString());

	HttpResponse response = httpclient.execute(httpget);
	HttpEntity entity = response.getEntity();
	
	ArrayList  tagList=(new JsonSlurper().parseText(IOUtils.toString(entity.getContent(), "UTF-8")));
	EntityUtils.consume(entity);
	
	Boolean tagExists = false;
	
	tagList.each { tag->
		if (tag.name.equals(tagName)) {
			tagExists = true;
		}
	}
	if (!tagExists || !skipIfExist) {
		HttpPost httppost = new HttpPost(tagsUrl.toString());
		
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("tag_name", tagName));
		params.add(new BasicNameValuePair("ref", ref));
		params.add(new BasicNameValuePair("message", message));
		params.add(new BasicNameValuePair("release_description", release));
		
		UrlEncodedFormEntity ent = new UrlEncodedFormEntity(params, "UTF-8");
		httppost.setEntity(ent);
		response = httpclient.execute(httppost);
		entity = response.getEntity();
		print IOUtils.toString(entity.getContent(), "UTF-8")
		EntityUtils.consume(entity);
	}
}