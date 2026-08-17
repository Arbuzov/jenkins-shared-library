package vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import groovy.json.JsonSlurper
import org.junit.Before
import org.junit.Test

class ParseClaudeStreamTest extends BasePipelineTest {

    def script
    List<String> echoMessages
    Map<String, String> files

    static final String ASSISTANT_TEXT =
        '{"type":"assistant","message":{"model":"claude-sonnet-4-6","content":[' +
        '{"type":"text","text":"Checking the ticket"}]}}'

    static final String ASSISTANT_TOOL_USE =
        '{"type":"assistant","message":{"model":"claude-sonnet-4-6","content":[' +
        '{"type":"tool_use","id":"toolu_1","name":"Read","input":{"file_path":"downloads/VV-861.json"}}]}}'

    static final String USER_TOOL_RESULT =
        '{"type":"user","message":{"content":[' +
        '{"type":"tool_result","tool_use_id":"toolu_1","content":[{"type":"text","text":"file body"}]}]}}'

    static final String RESULT =
        '{"type":"result","subtype":"success","is_error":false,"num_turns":7,"duration_ms":42000,' +
        '"total_cost_usd":0.123456,"usage":{"output_tokens":1234},"session_id":"sess-1","result":"done"}'

    @Before
    void setUp() {
        super.setUp()
        echoMessages = []
        files = [:]

        helper.registerAllowedMethod('reportUsage', [Map], { Map args -> })
        helper.registerAllowedMethod('echo', [String], { String message -> echoMessages << message })
        helper.registerAllowedMethod('error', [String], { String message -> throw new Exception(message) })
        helper.registerAllowedMethod('fileExists', [String], { String path -> files.containsKey(path) })
        helper.registerAllowedMethod('readFile', [String], { String path -> files[path] })
        helper.registerAllowedMethod('readJSON', [Map], { Map args ->
            assert args.returnPojo == true
            new JsonSlurper().parseText(args.text as String)
        })
        script = loadScript('vars/parseClaudeStream.groovy')
    }

    @Test
    void parsesAssistantTextToolInvocationAndResult() {
        def data = script.call([TEXT: [ASSISTANT_TEXT, ASSISTANT_TOOL_USE, USER_TOOL_RESULT, RESULT].join('\n')])

        assert data.events.size() == 3
        assert data.events[0].kind == 'assistant_text'
        assert data.events[0].text == 'Checking the ticket'
        assert data.events[1].kind == 'tool_invocation'
        assert data.events[1].name == 'Read'
        assert data.events[1].hasResult == true
        assert data.events[1].contentPreview == 'file body'
        assert data.events[2].kind == 'final_result'
        assert data.sessionId == 'sess-1'
    }

    @Test
    void collectsRunStatistics() {
        def data = script.call([TEXT: [ASSISTANT_TOOL_USE, USER_TOOL_RESULT, RESULT].join('\n')])

        assert data.stats.turns == 7
        assert data.stats.durationMs == 42000
        assert data.stats.costUsd == 0.1235
        assert data.stats.toolCalls == 1
        assert data.stats.toolErrors == 0
        assert data.stats.outputTokens == 1234
        assert data.stats.isError == false
    }

    @Test
    void marksToolResultWithIsErrorFlagAsFailed() {
        String failed =
            '{"type":"user","message":{"content":[' +
            '{"type":"tool_result","tool_use_id":"toolu_1","is_error":true,' +
            '"content":[{"type":"text","text":"404 Not Found"}]}]}}'

        def data = script.call([TEXT: [ASSISTANT_TOOL_USE, failed].join('\n')])

        assert data.stats.toolErrors == 1
        assert data.events[0].isError == true
        assert data.events[0].contentPreview == '404 Not Found'
    }

    @Test
    void treatsSuccessFalsePayloadAsFailedToolCall() {
        String softFailure =
            '{"type":"user","message":{"content":[' +
            '{"type":"tool_result","tool_use_id":"toolu_1",' +
            '"content":[{"type":"text","text":"{\\"success\\": false, \\"error\\": \\"denied\\"}"}]}]}}'

        def data = script.call([TEXT: [ASSISTANT_TOOL_USE, softFailure].join('\n')])

        assert data.stats.toolErrors == 1
        assert data.events[0].isError == true
    }

    @Test
    void keepsToolResultWithoutMatchingCallAsOrphan() {
        def data = script.call([TEXT: USER_TOOL_RESULT])

        assert data.events.size() == 1
        assert data.events[0].kind == 'tool_result'
        assert data.events[0].toolUseId == 'toolu_1'
    }

    @Test
    void keepsToolCallWithoutResultAndMarksItIncomplete() {
        def data = script.call([TEXT: ASSISTANT_TOOL_USE])

        assert data.events[0].kind == 'tool_invocation'
        assert data.events[0].hasResult == false
    }

    @Test
    void truncatesPreviewsAtTheConfiguredLimit() {
        String long_body = 'x' * 50
        String bigResult =
            '{"type":"user","message":{"content":[' +
            '{"type":"tool_result","tool_use_id":"toolu_1","content":[{"type":"text","text":"' + long_body + '"}]}]}}'

        def data = script.call([TEXT: [ASSISTANT_TOOL_USE, bigResult].join('\n'), PREVIEW_LIMIT: 10])

        assert data.events[0].contentTruncated == true
        assert data.events[0].contentBytes == 50
        assert data.events[0].contentPreview.startsWith('xxxxxxxxxx')
        assert data.events[0].contentPreview.contains('[truncated]')
    }

    @Test
    void skipsStreamEventDeltasAndMalformedLines() {
        String noise = '{"type":"stream_event","event":{"type":"content_block_delta"}}'
        String broken = '{"type":"assistant","message":{'

        def data = script.call([TEXT: [noise, broken, ASSISTANT_TEXT].join('\n')])

        assert data.events.size() == 1
        assert data.events[0].kind == 'assistant_text'
    }

    @Test
    void surfacesTheFirstFailureMarkerLine() {
        String withMarker =
            '{"type":"assistant","message":{"model":"m","content":[' +
            '{"type":"text","text":"line one\\nPUBLISH FAILED: Confluence 403\\nline three"}]}}'

        def data = script.call([TEXT: withMarker])

        assert data.failureLine == 'PUBLISH FAILED: Confluence 403'
    }

    @Test
    void usesACustomFailureMarker() {
        String withMarker =
            '{"type":"assistant","message":{"model":"m","content":[' +
            '{"type":"text","text":"GATE ERROR: no report written"}]}}'

        assert script.call([TEXT: withMarker, FAILURE_MARKER: 'GATE ERROR:']).failureLine ==
            'GATE ERROR: no report written'
        assert script.call([TEXT: withMarker, FAILURE_MARKER: '']).failureLine == ''
    }

    @Test
    void readsTheStreamFileWhenNoTextIsGiven() {
        files['debug.jsonp'] = ASSISTANT_TEXT

        def data = script.call([:])

        assert data.events[0].text == 'Checking the ticket'
    }

    @Test
    void failsWhenTheStreamFileIsMissing() {
        try {
            script.call([STREAM_FILE: 'nope.jsonp'])
            assert false, "Should have thrown an error"
        } catch (Exception exception) {
            assert exception.message.contains('stream file not found: nope.jsonp')
        }
    }

    @Test
    void returnsEmptyResultForEmptyInput() {
        def data = script.call([TEXT: ''])

        assert data.events.isEmpty()
        assert data.stats.toolCalls == 0
        assert data.sessionId == ''
    }

    @Test
    void logsCountsWhenDebugIsEnabled() {
        script.call([TEXT: [ASSISTANT_TOOL_USE, USER_TOOL_RESULT].join('\n'), DEBUG: true])

        assert echoMessages.any { it.contains('1 tool calls (0 errors)') }
    }
}
