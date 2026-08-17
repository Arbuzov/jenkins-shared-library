/**
 * Parse the NDJSON produced by `claude --output-format=stream-json` into a
 * structured Map for rendering (see publishClaudeTranscript).
 *
 * @param arg.STREAM_FILE Path to the stream-json file (default: 'debug.jsonp')
 * @param arg.TEXT Raw stream-json text; when given, STREAM_FILE is ignored
 * @param arg.PREVIEW_LIMIT Max characters kept per tool input/output (default: 4000)
 * @param arg.FAILURE_MARKER Line prefix in assistant text surfaced as a banner
 *                           (default: 'PUBLISH FAILED:'); set to '' to disable
 * @param arg.DEBUG Log the parsed event and tool-call counts (default: false)
 * @return Map with events, stats, sessionId, failureLine, generatedAt
 *
 * Implementation note: iteration uses explicit `for` loops, not `.each` /
 * `.eachLine` / `.findAll`. The Jenkins CPS transformer silently swallows
 * closure bodies when the body calls a pipeline step like `readJSON`
 * ("expected to call eachLine but wound up catching CpsClosure2.call"
 * — see https://www.jenkins.io/redirect/pipeline-cps-method-mismatches/).
 * The for-loop form is CPS-safe.
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  int previewLimit = (arg.PREVIEW_LIMIT ?: 4000) as int
  String failureMarker = arg.containsKey('FAILURE_MARKER') ? (arg.FAILURE_MARKER ?: '') : 'PUBLISH FAILED:'
  boolean debug = arg.DEBUG ?: false

  String text
  if (arg.TEXT != null) {
    text = arg.TEXT
  } else {
    String streamFile = arg.STREAM_FILE ?: 'debug.jsonp'
    if (!fileExists(streamFile)) {
      error("parseClaudeStream(): stream file not found: ${streamFile}")
    }
    text = readFile(streamFile)
  }

  def truncate = { String s ->
    if (s == null) return [text: '', truncated: false, bytes: 0]
    int n = s.length()
    if (n <= previewLimit) return [text: s, truncated: false, bytes: n]
    return [text: s.substring(0, previewLimit) + '\n...[truncated]', truncated: true, bytes: n]
  }
  def stringify = { v ->
    (v instanceof String) ? v : groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(v))
  }
  def joinTextBlocks = { content ->
    if (content == null) return ''
    if (content instanceof String) return content
    if (!(content instanceof List)) return stringify(content)
    def parts = []
    for (int i = 0; i < content.size(); i++) {
      def b = content[i]
      parts << ((b instanceof Map && b.type == 'text') ? (b.text ?: '') : stringify(b))
    }
    return parts.join('\n')
  }

  def events = []
  def stats = [
    turns: 0, durationMs: 0, costUsd: 0.0d,
    toolCalls: 0, toolErrors: 0, outputTokens: 0, isError: false
  ]
  String sessionId = ''
  String failureLine = ''

  // Pre-filter: stream_event deltas dominate the file; only assembled
  // assistant/user/result/system records carry the data we render.
  def keepPrefixes = ['{"type":"assistant"', '{"type":"user"', '{"type":"result"', '{"type":"system"']

  String[] lines = text.split('\n')
  for (int li = 0; li < lines.length; li++) {
    String line = lines[li].trim()
    if (!line) continue
    boolean keep = false
    for (int pi = 0; pi < keepPrefixes.size(); pi++) {
      if (line.startsWith(keepPrefixes[pi])) { keep = true; break }
    }
    if (!keep) continue
    def obj
    try { obj = readJSON(text: line, returnPojo: true) } catch (Exception ignored) { continue }
    def t = obj.type

    if (t == 'assistant') {
      def msg = obj.message ?: [:]
      def textParts = []
      def toolCalls = []
      def content = (msg.content ?: [])
      for (int bi = 0; bi < content.size(); bi++) {
        def block = content[bi]
        if (!(block instanceof Map)) continue
        if (block.type == 'text') {
          textParts << (block.text ?: '')
        } else if (block.type == 'tool_use') {
          stats.toolCalls += 1
          def inputStr = stringify(block.input ?: [:])
          def p = truncate(inputStr)
          toolCalls << [
            kind: 'tool_call', name: block.name ?: '?', id: block.id ?: '',
            inputBytes: p.bytes, inputPreview: p.text, inputTruncated: p.truncated,
            previewLimit: previewLimit
          ]
        }
      }
      if (textParts) {
        events << [kind: 'assistant_text', model: msg.model ?: '', text: textParts.join('\n')]
      }
      events.addAll(toolCalls)

    } else if (t == 'user') {
      def msg = obj.message ?: [:]
      def content = (msg.content ?: [])
      for (int bi = 0; bi < content.size(); bi++) {
        def block = content[bi]
        if (!(block instanceof Map) || block.type != 'tool_result') continue
        def body = joinTextBlocks(block.content)
        boolean isError = block.is_error == true
        if (!isError && (body.contains('"success": false') || body.contains('"success":false'))) {
          isError = true
        }
        if (isError) stats.toolErrors += 1
        def p = truncate(body)
        events << [
          kind: 'tool_result', toolUseId: block.tool_use_id ?: '', isError: isError,
          contentBytes: p.bytes, contentPreview: p.text, contentTruncated: p.truncated,
          previewLimit: previewLimit
        ]
      }

    } else if (t == 'result') {
      stats.isError = (obj.is_error == true)
      stats.turns = obj.num_turns ?: stats.turns
      stats.durationMs = obj.duration_ms ?: stats.durationMs
      stats.costUsd = ((obj.total_cost_usd ?: 0) as BigDecimal)
                        .setScale(4, java.math.RoundingMode.HALF_UP)
      def usage = obj.usage
      if (usage && usage.output_tokens) stats.outputTokens = usage.output_tokens
      sessionId = obj.session_id ?: sessionId
      def resText = obj.result instanceof String ? obj.result : ''
      events << [
        kind: 'final_result', subtype: obj.subtype ?: '',
        numTurns: obj.num_turns ?: 0, durationMs: obj.duration_ms ?: 0,
        costUsd: ((obj.total_cost_usd ?: 0) as BigDecimal)
                   .setScale(4, java.math.RoundingMode.HALF_UP),
        resultText: resText.length() > previewLimit ? resText.substring(0, previewLimit) : resText
      ]

    } else if (t == 'system') {
      sessionId = obj.session_id ?: sessionId
    }
  }

  // Pair tool_call with its matching tool_result into a single tool_invocation
  // event so the rendered transcript shows one card per tool action (input +
  // output side by side) instead of two scattered cards. Orphans on either
  // side (rare: cancelled call without result, or result without recorded
  // call) are kept as-is.
  def merged = []
  def pendingById = [:]
  for (int i = 0; i < events.size(); i++) {
    def ev = events[i]
    if (ev.kind == 'tool_call') {
      def inv = [
        kind: 'tool_invocation', name: ev.name, id: ev.id,
        inputBytes: ev.inputBytes, inputPreview: ev.inputPreview, inputTruncated: ev.inputTruncated,
        previewLimit: ev.previewLimit,
        hasResult: false, isError: false,
        contentBytes: 0, contentPreview: '', contentTruncated: false
      ]
      merged << inv
      pendingById[ev.id] = inv
    } else if (ev.kind == 'tool_result') {
      def inv = pendingById.remove(ev.toolUseId)
      if (inv) {
        inv.hasResult = true
        inv.isError = ev.isError
        inv.contentBytes = ev.contentBytes
        inv.contentPreview = ev.contentPreview
        inv.contentTruncated = ev.contentTruncated
      } else {
        merged << ev
      }
    } else {
      merged << ev
    }
  }
  events = merged

  // A skill may emit a top-level marker line in its assistant output when a
  // required step could not be completed (for example `PUBLISH FAILED: <cause>`
  // from /review-prd). Surface the first such line as a banner in the report.
  if (failureMarker) {
    for (int ei = 0; ei < events.size() && !failureLine; ei++) {
      def ev = events[ei]
      if (ev.kind != 'assistant_text') continue
      String[] textLines = (ev.text ?: '').split('\n')
      for (int tli = 0; tli < textLines.length; tli++) {
        String s = textLines[tli].trim()
        if (s.startsWith(failureMarker)) { failureLine = s; break }
      }
    }
  }

  if (debug) {
    echo "parseClaudeStream(): ${events.size()} events, ${stats.toolCalls} tool calls (${stats.toolErrors} errors)"
  }

  return [
    events: events,
    stats: stats,
    sessionId: sessionId,
    failureLine: failureLine,
    generatedAt: new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
  ]
}
