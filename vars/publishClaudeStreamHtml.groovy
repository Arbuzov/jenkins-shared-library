import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic

/**
 * Parse the NDJSON produced by `claude --output-format=stream-json` and
 * publish a human-readable HTML transcript of the run as a Jenkins HTML
 * report. One call does both: read SOURCE -> parse -> render -> publishHTML.
 *
 * Example:
 *   publishClaudeStreamHtml(
 *     SOURCE: 'debug.jsonp',
 *     REPORT_NAME: 'PRD Review Transcript',
 *     TITLE: 'PRD review transcript',
 *     CONTEXT: ['PRD page': PRD_PAGE]
 *   )
 *
 * @param SOURCE      Path to the stream-json NDJSON file. Default: 'debug.jsonp'.
 * @param OUTPUT      Path to write the rendered HTML to. Default: 'claude-transcript.html'.
 * @param REPORT_NAME Jenkins HTML report name shown in the build sidebar.
 *                    Default: 'Claude Stream Transcript'.
 * @param TITLE       H1 used on the rendered page. Default: 'Claude run transcript'.
 * @param CONTEXT     Optional Map of label -> value rendered in the page meta line
 *                    (URLs are auto-linkified). Default: [:].
 * @param BUILD_NUMBER  Override env.BUILD_NUMBER if you need to.
 * @param BUILD_URL     Override env.BUILD_URL if you need to.
 * @param PREVIEW_LIMIT Max chars kept per tool input/output preview before
 *                      truncation. Default: 4000.
 * @param KEEP_ALL                   publishHTML keepAll flag. Default: true.
 * @param ALWAYS_LINK_TO_LAST_BUILD  publishHTML flag. Default: true.
 * @param ALLOW_MISSING              publishHTML allowMissing flag. Default: false.
 * @param ALLOW_MISSING_SOURCE       When true and SOURCE does not exist, echo
 *                                   a notice and return null instead of failing.
 *                                   Default: true (so post.always blocks are safe).
 * @param DEBUG       Enable verbose echo. Default: false.
 *
 * @return Map with the parsed structure:
 *         [events, stats, sessionId, publishFailureLine, generatedAt, outputFile]
 *         or null when SOURCE is missing and ALLOW_MISSING_SOURCE is true.
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  String SOURCE      = arg.SOURCE      ?: 'debug.jsonp'
  String OUTPUT      = arg.OUTPUT      ?: 'claude-transcript.html'
  String REPORT_NAME = arg.REPORT_NAME ?: 'Claude Stream Transcript'
  String TITLE       = arg.TITLE       ?: 'Claude run transcript'
  Map    CONTEXT     = (arg.CONTEXT instanceof Map) ? arg.CONTEXT : [:]
  String BUILD_NUMBER = arg.BUILD_NUMBER?.toString() ?: env.BUILD_NUMBER?.toString() ?: ''
  String BUILD_URL    = arg.BUILD_URL ?: env.BUILD_URL ?: ''
  int    PREVIEW_LIMIT = (arg.PREVIEW_LIMIT ?: 4000) as int
  Boolean KEEP_ALL                  = toBool(arg.KEEP_ALL, true)
  Boolean ALWAYS_LINK_TO_LAST_BUILD = toBool(arg.ALWAYS_LINK_TO_LAST_BUILD, true)
  Boolean ALLOW_MISSING             = toBool(arg.ALLOW_MISSING, false)
  Boolean ALLOW_MISSING_SOURCE      = toBool(arg.ALLOW_MISSING_SOURCE, true)
  Boolean DEBUG                     = toBool(arg.DEBUG, false)

  if (!fileExists(SOURCE)) {
    if (ALLOW_MISSING_SOURCE) {
      echo "publishClaudeStreamHtml(): source '${SOURCE}' not found — skipping transcript rendering"
      return null
    }
    error("publishClaudeStreamHtml(): source file '${SOURCE}' not found")
  }

  String raw = readFile(SOURCE)
  if (DEBUG) echo "publishClaudeStreamHtml(): parsing ${raw.length()} bytes from '${SOURCE}'"

  Map data = parseStream(raw, PREVIEW_LIMIT)
  if (DEBUG) {
    echo "publishClaudeStreamHtml(): parsed ${data.events.size()} events, " +
         "${data.stats.toolCalls} tool calls (${data.stats.toolErrors} errors)"
  }

  String tmpl = libraryResource('claude/transcript.html.gtpl')
  String html = inflateTemplate(
    TEMPLATE:           tmpl,
    title:              TITLE,
    buildNumber:        BUILD_NUMBER,
    buildUrl:           BUILD_URL,
    context:            CONTEXT,
    sessionId:          data.sessionId,
    stats:              data.stats,
    events:             data.events,
    publishFailureLine: data.publishFailureLine,
    generatedAt:        data.generatedAt,
    sourceFile:         SOURCE
  )
  writeFile file: OUTPUT, text: html

  publishHTML([
    allowMissing:          ALLOW_MISSING,
    alwaysLinkToLastBuild: ALWAYS_LINK_TO_LAST_BUILD,
    keepAll:               KEEP_ALL,
    reportDir:             '.',
    reportFiles:           OUTPUT,
    reportName:            REPORT_NAME,
    reportTitles:          REPORT_NAME
  ])

  data.outputFile = OUTPUT
  return data
}

private static Boolean toBool(value, Boolean dflt) {
  if (value == null) return dflt
  if (value instanceof Boolean) return value
  return value.toString().toBoolean()
}

/**
 * Pure parser. Annotated @NonCPS so we can use .each / .collect / closures
 * freely without tripping Jenkins CPS-closure mismatches — none of this code
 * calls a pipeline step. JSON parsing uses JsonSlurperClassic (trusted in a
 * shared-library var) instead of the `readJSON` step for the same reason.
 *
 * Returns: [events, stats, sessionId, publishFailureLine, generatedAt]
 *
 * Event kinds in the returned list:
 *   - assistant_text   { kind, model, text }
 *   - tool_invocation  { kind, name, id,
 *                        inputBytes, inputPreview, inputTruncated,
 *                        hasResult, isError,
 *                        contentBytes, contentPreview, contentTruncated,
 *                        previewLimit }
 *   - tool_result      orphan result with no matching call (rare)
 *   - final_result     { kind, subtype, numTurns, durationMs, costUsd, resultText }
 */
@NonCPS
private static Map parseStream(String text, int previewLimit) {
  def truncate = { String s ->
    if (s == null) return [text: '', truncated: false, bytes: 0]
    int n = s.length()
    if (n <= previewLimit) return [text: s, truncated: false, bytes: n]
    return [text: s.substring(0, previewLimit) + '\n...[truncated]', truncated: true, bytes: n]
  }
  def stringify = { v ->
    (v instanceof String) ? v : JsonOutput.prettyPrint(JsonOutput.toJson(v))
  }
  def joinTextBlocks = { content ->
    if (content == null) return ''
    if (content instanceof String) return content
    if (!(content instanceof List)) return stringify(content)
    return content.collect { b ->
      (b instanceof Map && b.type == 'text') ? (b.text ?: '') : stringify(b)
    }.join('\n')
  }

  def slurper = new JsonSlurperClassic()
  def events = []
  def stats = [
    turns: 0, durationMs: 0, costUsd: 0.0d,
    toolCalls: 0, toolErrors: 0, outputTokens: 0, isError: false
  ]
  String sessionId = ''
  String publishFailure = ''

  // Pre-filter: stream_event deltas dominate the file; only assembled
  // assistant/user/result/system records carry the data we render.
  def keepPrefixes = ['{"type":"assistant"', '{"type":"user"', '{"type":"result"', '{"type":"system"']

  text.split('\n').each { String rawLine ->
    String line = rawLine?.trim()
    if (!line || !keepPrefixes.any { line.startsWith(it) }) return
    def obj
    try { obj = slurper.parseText(line) } catch (Exception ignored) { return }
    def t = obj.type

    if (t == 'assistant') {
      def msg = obj.message ?: [:]
      def textParts = []
      def toolCalls = []
      (msg.content ?: []).each { block ->
        if (!(block instanceof Map)) return
        if (block.type == 'text') {
          textParts << (block.text ?: '')
        } else if (block.type == 'tool_use') {
          stats.toolCalls = (stats.toolCalls as int) + 1
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
      (msg.content ?: []).each { block ->
        if (!(block instanceof Map) || block.type != 'tool_result') return
        def body = joinTextBlocks(block.content)
        boolean isError = block.is_error == true
        if (!isError && (body.contains('"success": false') || body.contains('"success":false'))) {
          isError = true
        }
        if (isError) stats.toolErrors = (stats.toolErrors as int) + 1
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
  // event so the rendered transcript shows one card per tool action.
  def merged = []
  def pendingById = [:]
  events.each { ev ->
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

  // The /review-prd skill emits a top-level `PUBLISH FAILED: <cause>` line in
  // the assistant output when any required publish step (attach/comment/Jira)
  // could not be completed. Surface that as a banner in the rendered HTML.
  events.find { ev ->
    if (ev.kind != 'assistant_text') return false
    def hit = (ev.text ?: '').readLines().find { it.trim().startsWith('PUBLISH FAILED:') }
    if (hit) { publishFailure = hit.trim(); return true }
    return false
  }

  return [
    events:             events,
    stats:              stats,
    sessionId:          sessionId,
    publishFailureLine: publishFailure,
    generatedAt:        new Date().format("yyyy-MM-dd'T'HH:mm:ss'Z'", TimeZone.getTimeZone('UTC'))
  ]
}
