<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>${title} &mdash; build ${buildNumber}</title>
<style>
  :root { color-scheme: light dark; }
  body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 0; padding: 24px; max-width: 1200px; background: #f7f7f8; color: #1a1a1a; }
  h1 { margin: 0 0 4px; font-size: 22px; }
  h2 { margin: 24px 0 8px; font-size: 16px; color: #444; border-bottom: 1px solid #ddd; padding-bottom: 4px; }
  .meta { color: #666; font-size: 13px; margin-bottom: 16px; }
  .meta a { color: #2563eb; text-decoration: none; }
  .summary { background: #fff; border: 1px solid #e5e5e7; border-radius: 8px; padding: 12px 16px; margin-bottom: 16px; }
  .summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 8px 16px; }
  .summary-grid div { font-size: 13px; }
  .summary-grid b { color: #555; font-weight: 600; }
  .event { background: #fff; border: 1px solid #e5e5e7; border-radius: 8px; margin-bottom: 10px; overflow: hidden; }
  .event-header { padding: 8px 14px; font-size: 13px; display: flex; gap: 10px; align-items: center; background: #fafafa; border-bottom: 1px solid #eee; }
  .badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
  .badge.assistant { background: #ddebff; color: #1e40af; }
  .badge.tool { background: #fff3cd; color: #7c5800; }
  .badge.tool.err { background: #fde2e2; color: #b91c1c; }
  .badge.result { background: #1a1a1a; color: #fff; }
  .tool-name { font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: 12px; color: #444; }
  .event-body { padding: 12px 14px; }
  .markdown { line-height: 1.55; font-size: 14px; }
  .markdown :first-child { margin-top: 0; }
  .markdown :last-child { margin-bottom: 0; }
  .markdown h1, .markdown h2, .markdown h3, .markdown h4 { margin: 14px 0 6px; line-height: 1.3; }
  .markdown h1 { font-size: 18px; }
  .markdown h2 { font-size: 16px; border: 0; padding: 0; color: inherit; }
  .markdown h3 { font-size: 15px; }
  .markdown h4 { font-size: 14px; color: #555; }
  .markdown p { margin: 6px 0; }
  .markdown ul, .markdown ol { margin: 6px 0; padding-left: 22px; }
  .markdown li { margin: 2px 0; }
  .markdown code { background: #f1f1f3; padding: 1px 5px; border-radius: 3px; font-size: 12.5px; font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; }
  .markdown pre { background: #f4f4f6; border: 1px solid #e5e5e7; border-radius: 6px; padding: 10px; overflow-x: auto; font-size: 12px; line-height: 1.4; }
  .markdown pre code { background: none; padding: 0; }
  .markdown blockquote { border-left: 3px solid #c7c7cc; margin: 6px 0; padding: 0 10px; color: #555; }
  .markdown table { border-collapse: collapse; margin: 8px 0; font-size: 13px; }
  .markdown th, .markdown td { border: 1px solid #d2d2d7; padding: 4px 8px; text-align: left; vertical-align: top; }
  .markdown th { background: #f4f4f6; font-weight: 600; }
  .markdown a { color: #2563eb; }
  .text-plain { white-space: pre-wrap; word-break: break-word; line-height: 1.45; font-size: 14px; }
  details { margin-top: 6px; }
  details + details { margin-top: 4px; }
  details summary { cursor: pointer; color: #555; font-size: 12px; padding: 4px 0; user-select: none; }
  details[open] summary { color: #1a1a1a; font-weight: 600; }
  pre.code { background: #f4f4f6; border: 1px solid #e5e5e7; border-radius: 6px; padding: 10px; overflow-x: auto; font-size: 12px; line-height: 1.4; max-height: 360px; font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; margin: 4px 0 0; }
  pre.code.error { background: #fef2f2; border-color: #fecaca; color: #7f1d1d; }
  .truncated { color: #888; font-style: italic; font-size: 11px; margin-top: 4px; }
  .empty-note { color: #888; font-style: italic; font-size: 13px; }
  .toc { background: #fff; border: 1px solid #e5e5e7; border-radius: 8px; padding: 8px 14px; margin-bottom: 16px; font-size: 13px; }
  .toc b { color: #555; }
  .toc a { color: #2563eb; text-decoration: none; margin-right: 8px; }
</style>
</head>
<body>

<h1>${title}</h1>
<div class="meta">
  Build <b>#${buildNumber}</b><% if (buildUrl) { %> &middot; <a href="${buildUrl}">Jenkins build</a><% } %>
  <% context.each { k, v -> %>
    &middot; ${k}:
    <% if (v?.toString()?.startsWith('http')) { %><a href="${v}">${v}</a><% } else { %>${v}<% } %>
  <% } %>
</div>

<div class="summary">
  <h2 style="margin-top:0;border:none;">Run summary</h2>
  <div class="summary-grid">
    <div><b>Session</b> <span class="tool-name">${sessionId}</span></div>
    <div><b>Turns</b> ${stats.turns}</div>
    <div><b>Duration</b> ${stats.durationMs} ms</div>
    <div><b>Cost</b> \$${stats.costUsd}</div>
    <div><b>Tool calls</b> ${stats.toolCalls}</div>
    <div><b>Failed tool calls</b> <span style="color:${stats.toolErrors > 0 ? '#b91c1c' : '#137333'};font-weight:600;">${stats.toolErrors}</span></div>
    <div><b>Output tokens</b> ${stats.outputTokens}</div>
    <div><b>Result</b> <span style="color:${stats.isError ? '#b91c1c' : '#137333'};font-weight:600;">${stats.isError ? 'ERROR' : 'success'}</span></div>
  </div>
</div>

<% if (publishFailureLine) { %>
<div class="event">
  <div class="event-header"><span class="badge tool err">PUBLISH FAILED</span></div>
  <div class="event-body"><div class="text-plain">${publishFailureLine}</div></div>
</div>
<% } %>

<%
  def errorCount = 0
  events.each { ev -> if (ev.kind == 'tool_invocation' && ev.isError) errorCount++ }
%>
<% if (errorCount > 0) { %>
<div class="toc">
  <b>${errorCount} failed tool call${errorCount == 1 ? '' : 's'}</b> &middot;
  <a href="#first-error">jump to first error</a>
</div>
<% } %>

<h2>Transcript (${events.size()} events)</h2>

<% if (events.isEmpty()) { %>
<div class="empty-note">No transcript events parsed from the source file.</div>
<% } %>

<%
  boolean firstErrorAnchored = false
  events.each { ev ->
%>
  <% if (ev.kind == 'assistant_text') { %>
    <div class="event">
      <div class="event-header">
        <span class="badge assistant">Assistant</span>
        <span class="tool-name">${ev.model}</span>
      </div>
      <div class="event-body">
        <div class="markdown" data-md="1">${ev.text?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')}</div>
      </div>
    </div>
  <% } else if (ev.kind == 'tool_invocation') { %>
    <%
      def anchor = ''
      if (ev.isError && !firstErrorAnchored) {
        anchor = ' id="first-error"'
        firstErrorAnchored = true
      }
    %>
    <div class="event"${anchor}>
      <div class="event-header">
        <span class="badge tool${ev.isError ? ' err' : ''}">${ev.isError ? 'Tool error' : (ev.hasResult ? 'Tool' : 'Tool (no result)')}</span>
        <span class="tool-name">${ev.name}</span>
      </div>
      <div class="event-body">
        <details>
          <summary>Input (${ev.inputBytes} bytes)</summary>
          <pre class="code">${ev.inputPreview?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')}</pre>
          <% if (ev.inputTruncated) { %><div class="truncated">truncated to ${ev.previewLimit} chars; full input archived in the source file</div><% } %>
        </details>
        <% if (ev.hasResult) { %>
        <details ${ev.isError ? 'open' : ''}>
          <summary>Output (${ev.contentBytes} bytes)${ev.isError ? ' &mdash; error' : ''}</summary>
          <pre class="code ${ev.isError ? 'error' : ''}">${ev.contentPreview?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')}</pre>
          <% if (ev.contentTruncated) { %><div class="truncated">truncated to ${ev.previewLimit} chars; full content archived in the source file</div><% } %>
        </details>
        <% } else { %>
        <div class="truncated">no matching tool_result captured in the stream &mdash; call may have been cancelled or interrupted.</div>
        <% } %>
      </div>
    </div>
  <% } else if (ev.kind == 'tool_result') { %>
    <div class="event">
      <div class="event-header">
        <span class="badge tool${ev.isError ? ' err' : ''}">Orphan tool result${ev.isError ? ' (error)' : ''}</span>
        <span class="tool-name">${ev.toolUseId}</span>
      </div>
      <div class="event-body">
        <details ${ev.isError ? 'open' : ''}>
          <summary>Output (${ev.contentBytes} bytes)</summary>
          <pre class="code ${ev.isError ? 'error' : ''}">${ev.contentPreview?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')}</pre>
          <% if (ev.contentTruncated) { %><div class="truncated">truncated to ${ev.previewLimit} chars; full content archived in the source file</div><% } %>
        </details>
      </div>
    </div>
  <% } else if (ev.kind == 'final_result') { %>
    <div class="event">
      <div class="event-header">
        <span class="badge result">Final result</span>
      </div>
      <div class="event-body">
        <div class="text-plain"><b>${ev.subtype}</b> &middot; ${ev.numTurns} turns &middot; ${ev.durationMs} ms &middot; \$${ev.costUsd}</div>
        <% if (ev.resultText) { %><div class="markdown" data-md="1" style="margin-top:8px">${ev.resultText?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')}</div><% } %>
      </div>
    </div>
  <% } %>
<% } %>

<div class="meta" style="margin-top:24px">
  Generated ${generatedAt}. Source: <code>${sourceFile}</code>.
</div>

<!-- Client-side Markdown rendering for assistant text. marked.js is loaded
     from a CDN; if it fails (offline / strict CSP) the inline fallback
     switches the divs back to .text-plain (pre-wrap) so the text stays
     readable. -->
<script src="https://cdn.jsdelivr.net/npm/marked@12/marked.min.js" crossorigin="anonymous"></script>
<script>
  (function () {
    if (typeof marked === 'undefined') {
      document.querySelectorAll('.markdown').forEach(function (el) {
        el.classList.remove('markdown');
        el.classList.add('text-plain');
      });
      return;
    }
    marked.setOptions({ gfm: true, breaks: false });
    document.querySelectorAll('.markdown[data-md="1"]').forEach(function (el) {
      el.innerHTML = marked.parse(el.textContent);
    });
  })();
</script>

</body>
</html>
