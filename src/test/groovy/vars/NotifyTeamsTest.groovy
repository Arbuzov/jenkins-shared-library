package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class NotifyTeamsTest extends BasePipelineTest {

    def script
    List<Map> httpRequestArgs
    List<Map> httpResponses
    int httpRequestIdx

    @Before
    void setUp() {
        super.setUp()
        httpRequestArgs = []
        httpResponses = []
        httpRequestIdx = 0

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> })
        helper.registerAllowedMethod('error', [String], { String message ->
            throw new RuntimeException(message)
        })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            new JsonSlurper().parseText(args.text as String)
        })
        helper.registerAllowedMethod('httpRequest', [Map], { Map args ->
            httpRequestArgs << args
            def resp = httpRequestIdx < httpResponses.size() ? httpResponses[httpRequestIdx] : [status: 200, content: '{}']
            httpRequestIdx++
            return resp
        })
        helper.registerAllowedMethod('usernamePassword', [Map], { Map args -> args })
        helper.registerAllowedMethod('withCredentials', [List, Closure], { List creds, Closure body ->
            // Simulate the http_request plugin's credential injection by
            // populating env with stable fakes for both bindings.
            def envMap = binding.hasVariable('env') ? binding.getVariable('env') : [:]
            envMap.NT_CLIENT_ID = 'fake-client-id'
            envMap.NT_CLIENT_SECRET = 'fake-client-secret'
            envMap.NT_USER = 'svc@example.com'
            envMap.NT_PASSWORD = 'fake-password'
            binding.setVariable('env', envMap)
            body()
        })

        binding.setVariable('env', [:])
        script = loadScript('vars/notifyTeams.groovy')
    }

    @Test
    void postsMessageWithDefaults() {
        httpResponses << [status: 200, content: '{"access_token":"tok","expires_in":3599,"scope":"Chat.ReadWrite"}']
        httpResponses << [status: 201, content: '{"id":"m-1"}']

        def ids = script.call([
            CHAT_ID: '19:abc@unq.gbl.spaces',
            MESSAGE: '<p>Hi</p>'
        ])

        assert ids == ['m-1']
        assert httpRequestArgs.size() == 2

        // Token request
        assert httpRequestArgs[0].url == 'https://login.microsoftonline.com/b177c9bd-46b9-4bf4-86d3-eefbb46ba01a/oauth2/v2.0/token'
        assert httpRequestArgs[0].httpMode == 'POST'
        assert (httpRequestArgs[0].requestBody as String).contains('grant_type=password')
        assert (httpRequestArgs[0].requestBody as String).contains('username=svc%40example.com')
        assert (httpRequestArgs[0].requestBody as String).contains('client_id=fake-client-id')

        // Chat message POST
        assert httpRequestArgs[1].url == 'https://graph.microsoft.com/v1.0/chats/19:abc@unq.gbl.spaces/messages'
        assert httpRequestArgs[1].httpMode == 'POST'
        def authHeader = (httpRequestArgs[1].customHeaders as List).find { it.name == 'Authorization' }
        assert authHeader.value == 'Bearer tok'
        assert authHeader.maskValue == true

        def payload = new JsonSlurper().parseText(httpRequestArgs[1].requestBody as String)
        assert payload.body.contentType == 'html'
        assert payload.body.content == '<p>Hi</p>'
    }

    @Test
    void postsToMultipleChatsReusingToken() {
        httpResponses << [status: 200, content: '{"access_token":"tok"}']
        httpResponses << [status: 201, content: '{"id":"m-1"}']
        httpResponses << [status: 201, content: '{"id":"m-2"}']

        def ids = script.call([
            CHAT_ID: ['19:a@unq.gbl.spaces', '19:b@unq.gbl.spaces'],
            MESSAGE: 'hi',
            FORMAT: 'text'
        ])

        assert ids == ['m-1', 'm-2']
        assert httpRequestArgs.size() == 3
        // Single token request followed by per-chat POSTs
        assert httpRequestArgs[1].url.endsWith('/chats/19:a@unq.gbl.spaces/messages')
        assert httpRequestArgs[2].url.endsWith('/chats/19:b@unq.gbl.spaces/messages')

        def payload = new JsonSlurper().parseText(httpRequestArgs[1].requestBody as String)
        assert payload.body.contentType == 'text'
    }

    @Test
    void honoursOverriddenTenantAndCredentialIds() {
        httpResponses << [status: 200, content: '{"access_token":"tok"}']
        httpResponses << [status: 201, content: '{"id":"m"}']

        script.call([
            CHAT_ID: '19:x',
            MESSAGE: 'hi',
            TENANT_ID: 'tenant-xyz',
            APP_CREDENTIALS_ID: 'my-app',
            USER_CREDENTIALS_ID: 'my-user',
            GRAPH_BASE_URL: 'https://graph.example.com/beta'
        ])

        assert httpRequestArgs[0].url == 'https://login.microsoftonline.com/tenant-xyz/oauth2/v2.0/token'
        assert (httpRequestArgs[0].requestBody as String).contains('scope=https%3A%2F%2Fgraph.example.com%2F.default')
        assert httpRequestArgs[1].url == 'https://graph.example.com/beta/chats/19:x/messages'
    }

    @Test
    void skipsWhenChatIdEmpty() {
        def ids = script.call([CHAT_ID: '', MESSAGE: 'x'])
        assert ids == null
        assert httpRequestArgs.isEmpty()
    }

    @Test
    void skipsWhenChatIdNull() {
        def ids = script.call([MESSAGE: 'x'])
        assert ids == null
        assert httpRequestArgs.isEmpty()
    }

    @Test
    void skipsWhenChatIdListEmpty() {
        def ids = script.call([CHAT_ID: [], MESSAGE: 'x'])
        assert ids == null
        assert httpRequestArgs.isEmpty()
    }

    @Test
    void failsWhenMessageMissing() {
        try {
            script.call([CHAT_ID: '19:abc'])
            assert false, 'should throw'
        } catch (RuntimeException e) {
            assert e.message.contains('MESSAGE must be supplied')
        }
    }

    @Test
    void failsOnInvalidFormat() {
        try {
            script.call([CHAT_ID: '19:abc', MESSAGE: 'x', FORMAT: 'markdown'])
            assert false, 'should throw'
        } catch (RuntimeException e) {
            assert e.message.contains("FORMAT must be 'html' or 'text'")
        }
    }

    @Test
    void failsOnTokenError() {
        httpResponses << [status: 401, content: '{"error":"invalid_grant","error_description":"AADSTS7000218"}']
        try {
            script.call([CHAT_ID: '19:abc', MESSAGE: 'hi'])
            assert false, 'should throw'
        } catch (RuntimeException e) {
            assert e.message.contains('token request failed: HTTP 401')
            assert e.message.contains('AADSTS7000218')
        }
    }

    @Test
    void failsOnPostError() {
        httpResponses << [status: 200, content: '{"access_token":"tok"}']
        httpResponses << [status: 403, content: '{"error":{"code":"Forbidden","message":"AccessDenied"}}']
        try {
            script.call([CHAT_ID: '19:abc', MESSAGE: 'hi'])
            assert false, 'should throw'
        } catch (RuntimeException e) {
            assert e.message.contains('POST chat message failed for chatId=19:abc')
            assert e.message.contains('HTTP 403')
            assert e.message.contains('AccessDenied')
        }
    }
}
