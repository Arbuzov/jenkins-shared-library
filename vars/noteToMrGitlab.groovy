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


def call (String gitUrl, String mrIid, String body = '', Boolean skipIfExist = true) {

	URL url = new URL(gitUrl.replaceFirst(/:(?=[a-z])/,'/').replace('git@', 'https://'));
	String gitLabAPIUrl = "https://"+url.getHost()+"/api/v4";
	String usrEncodedProject = url.getPath().substring(1).replace('.git', '').replace('/', '%2F');
	URL mrUrl = new URL (gitLabAPIUrl+"/projects/"+usrEncodedProject+"/merge_requests/"+mrIid+"/notes/?private_token=" + gitLabAPISecret);

	echo mrUrl.toString()
	echo body.toString()

	SSLContextBuilder builder = new SSLContextBuilder();
	builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
	SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build());
	CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();

	HttpGet httpget = new HttpGet(mrUrl.toString());

	HttpResponse response = httpclient.execute(httpget);
	HttpEntity entity = response.getEntity();
	
	HttpPost httppost = new HttpPost(mrUrl.toString());
		
	List<NameValuePair> params = new ArrayList<NameValuePair>();
	params.add(new BasicNameValuePair("body", body));
		
	UrlEncodedFormEntity ent = new UrlEncodedFormEntity(params, "UTF-8");
	httppost.setEntity(ent);
	response = httpclient.execute(httppost);
	entity = response.getEntity();
	// print IOUtils.toString(entity.getContent(), "UTF-8")
	EntityUtils.consume(entity);
}
