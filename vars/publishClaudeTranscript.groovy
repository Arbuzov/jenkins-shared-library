/**
 * Render the transcript of a Claude Code run as an HTML report and publish it
 * on the build page via publishHTML.
 *
 * @param arg.STREAM_FILE Path to the stream-json file (default: 'debug.jsonp')
 * @param arg.TITLE Report heading (default: 'Claude Code transcript')
 * @param arg.REPORT_NAME publishHTML report name, also the build-page link text
 *                        (default: value of TITLE)
 * @param arg.CONTEXT_LABEL Optional label of what the run was about, e.g. 'VV-861'
 * @param arg.CONTEXT_URL Optional link target for CONTEXT_LABEL
 * @param arg.OUTPUT_FILE Rendered HTML file (default: 'claude-transcript.html')
 * @param arg.PREVIEW_LIMIT Max characters per tool input/output (default: 4000)
 * @param arg.FAILURE_MARKER Assistant line prefix rendered as a banner
 *                           (default: 'PUBLISH FAILED:'); '' disables it
 * @param arg.ALLOW_MISSING Skip instead of failing when the stream file is
 *                          absent (default: true)
 * @param arg.DEBUG Log parsing details (default: false)
 * @return Map from parseClaudeStream, or null when the stream file was missing
 *         and ALLOW_MISSING is true
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  String streamFile = arg.STREAM_FILE ?: 'debug.jsonp'
  String title = arg.TITLE ?: 'Claude Code transcript'
  String reportName = arg.REPORT_NAME ?: title
  String outputFile = arg.OUTPUT_FILE ?: 'claude-transcript.html'
  boolean allowMissing = arg.containsKey('ALLOW_MISSING') ? arg.ALLOW_MISSING : true

  if (!fileExists(streamFile)) {
    if (allowMissing) {
      echo "publishClaudeTranscript(): ${streamFile} not produced - skipping transcript rendering"
      return null
    }
    error("publishClaudeTranscript(): stream file not found: ${streamFile}")
  }

  def data = parseClaudeStream(
    STREAM_FILE: streamFile,
    PREVIEW_LIMIT: arg.PREVIEW_LIMIT ?: 4000,
    FAILURE_MARKER: arg.containsKey('FAILURE_MARKER') ? arg.FAILURE_MARKER : 'PUBLISH FAILED:',
    DEBUG: arg.DEBUG ?: false
  )

  def html = inflateTemplate(
    TEMPLATE: libraryResource('com/spacebridge/claude/transcript.html.gtpl'),
    title: title,
    buildNumber: env.BUILD_NUMBER ?: '',
    buildUrl: env.BUILD_URL ?: '',
    contextLabel: arg.CONTEXT_LABEL ?: '',
    contextUrl: arg.CONTEXT_URL ?: '',
    sessionId: data.sessionId,
    stats: data.stats,
    events: data.events,
    failureLine: data.failureLine,
    streamFile: streamFile,
    generatedAt: data.generatedAt
  )
  writeFile(file: outputFile, text: html)

  publishHTML([
    allowMissing: false,
    alwaysLinkToLastBuild: true,
    keepAll: true,
    reportDir: '.',
    reportFiles: outputFile,
    reportName: reportName,
    reportTitles: title
  ])

  return data
}
