<h2>Overview</h2>
<ac:structured-macro ac:name="details" ac:schema-version="1" ac:macro-id="1e693907-33d2-454c-add4-199d7143833d">
  <ac:parameter ac:name="id">overview</ac:parameter>
  <ac:rich-text-body>
    <table class="relative-table" style="width: 90%;">
      <colgroup>
        <col style="width: 37.4389%;" />
        <col style="width: 62.5611%;" />
      </colgroup>
      <tbody>
        <% if (binding.hasVariable('setupName') && setupName != null) { %>
        <tr>
          <th scope="col">Tested at</th>
          <td scope="col">${setupName}</td>
        </tr>
        <% } %>
        <tr>
          <th scope="col">Date</th>
          <td scope="col">
            <span style="color: rgb(112,112,112);background-color: rgb(245,245,245);">
              <em><time datetime="${new Date().format('yyyy-MM-dd')}" /></em>
            </span>
          </td>
        </tr>
        <tr>
          <th>Test</th>
          <td scope="col">
            <% if (binding.hasVariable('build') && build != null) { %>
            <a href="${build.getAbsoluteUrl()}">${build.getBuildCauses().last().shortDescription }</a>
            <% } %>
          </td>
        </tr>
        <tr>
          <th scope="col">Status</th>
          <td scope="col">
            <% if (binding.hasVariable('results') && results != null) { %>
            <ac:structured-macro ac:name="status" ac:schema-version="1" ac:macro-id="ce178cd5-5503-4026-af1d-fa5c87aec3c6">
              <ac:parameter ac:name="colour">${results?.status == 'SUCCESS' ? 'Green' : 'Red'}</ac:parameter>
              <ac:parameter ac:name="title">${results?.status}</ac:parameter>
            </ac:structured-macro>
            <% } %>
          </td>
        </tr>
      </tbody>
    </table>
  </ac:rich-text-body>
</ac:structured-macro>
<h2>Tested components</h2>
<ac:structured-macro ac:name="details" ac:schema-version="1" ac:macro-id="1e693907-33d2-454c-add4-199d7143833d">
  <ac:parameter ac:name="id">key-components</ac:parameter>
  <ac:rich-text-body>
    <table class="relative-table" style="width: 60%;">
      <colgroup>
        <col style="width: 37.4389%;" />
        <col style="width: 62.5611%;" />
      </colgroup>
      <tbody>
        <% keyComponentsList?.each { pack, ver -> %>
        <tr>
          <th scope="col">${pack}</th>
          <td scope="col">${ver}</td>
        </tr>
        <% } %>
      </tbody>
    </table>
  </ac:rich-text-body>
</ac:structured-macro>
<% if (binding.hasVariable('environment') && environment != null) { %>
${environment}
<% } %>
<% if (binding.hasVariable('params') && params != null) { %>
<h2>Run params</h2>
<ac:structured-macro ac:name="details" ac:schema-version="1" ac:macro-id="1e693907-33d2-454c-add4-199d7143833d">
  <ac:parameter ac:name="id">build-params</ac:parameter>
  <ac:rich-text-body>
    <table class="relative-table" style="width: 60%;">
      <colgroup>
        <col style="width: 37.4389%;" />
        <col style="width: 62.5611%;" />
      </colgroup>
      <tbody>
        <% params?.each { key, value -> %>
        <% if (value && !value.toString().trim().isEmpty()) { %>
        <tr>
          <th scope="col">${key}</th>
          <td scope="col">${value}</td>
        </tr>
        <% } %>
        <% } %>
      </tbody>
    </table>
  </ac:rich-text-body>
</ac:structured-macro>
<% } %>
<%
def escapeXml = { str -> str?.toString()?.replace('&', '&amp;')?.replace('<', '&lt;')?.replace('>', '&gt;')?.replace('"', '&quot;') ?: '' }
def hasSetupErrors = binding.hasVariable('results') && results != null && results.testsuites?.any { it.errors?.isInteger() && (it.errors as Integer) > 0 }
%>
<% if (hasSetupErrors) { %>
<% def firstError = results.testsuites?.getAt(0)?.testcases?.getAt(0) %>
<h2>Errors</h2>
<% if (firstError) { %>
<ac:structured-macro ac:name="warning" ac:schema-version="1">
  <ac:rich-text-body>
    <p><strong>${escapeXml(firstError.classname)}: ${escapeXml(firstError.name)}</strong></p>
    <p>${escapeXml(firstError.error.message)}</p>
    <% if (firstError.error.content) { %>
    <ac:structured-macro ac:name="code" ac:schema-version="1">
      <ac:plain-text-body><![CDATA[${firstError.error.content}]]></ac:plain-text-body>
    </ac:structured-macro>
    <% } %>
  </ac:rich-text-body>
</ac:structured-macro>
<% } %>
<% } else if (binding.hasVariable('testcases') && testcases != null) { %>
<%
def allResultTestcases = results?.testsuites?.collectMany { it.testcases ?: [] } ?: []
def totalTests = (int)(allResultTestcases.size() / 2)
def groupedConfigs = [
  ['Expected Duration', 'Warm-up Duration'],
  ['Center Frequency (Mhz)', 'Frequency Bandwidth (MHz)', 'Target ModCod', 'Target Waveform ID', 'Es/No Delta (dB)', 'Es/No override (dB)', 'Roll-off', 'Attenuation Level (dB)', 'Attenuation Step (dB)', 'Attenuation Dwell Time (sec)'],
  ['MIR Bit Rate (Kbps)', 'CIR Bit Rate (Kbps)', 'CRA Bit Rate (Kbps)'],
  ['Traffic Rate (Kbps)', 'Rate Step (Kbps)', 'Rate Dwell Time (sec)', 'Packet Size (bytes)', 'Traffic Type', 'DSCP (dec)', 'Priority Queue'],
]
def showMissingTest = { missingId ->
  def rtc = allResultTestcases[(missingId - 1)]
  def err = rtc?.error ?: rtc?.failure
  out << "<h3>Test ${missingId}</h3>"
  out << '<ac:structured-macro ac:name="warning" ac:schema-version="1"><ac:rich-text-body>'
  if (err?.message) {
    out << "<p>${escapeXml(err.message)}</p>"
  }
  if (err?.content) {
    out << '<ac:structured-macro ac:name="code" ac:schema-version="1"><ac:plain-text-body><![CDATA[' + err.content + ']]></ac:plain-text-body></ac:structured-macro>'
  }
  if (!err) {
    out << '<p>Test run failed</p>'
  }
  out << '</ac:rich-text-body></ac:structured-macro>'
}
def expectedId = 1
%>
<h2>Test Cases</h2>
<% testcases?.each { tc -> %>
<% def tcId = tc.testId as Integer %>
<% while (expectedId < tcId) { %>
<% showMissingTest(expectedId) %>
<% expectedId++ %>
<% } %>
<h3>Test ${tc.testId}</h3>
<% expectedId = tcId + 1 %>
<% def grouped = tc.streams?.groupBy { it.direction } %>
<% grouped?.each { direction, streams -> %>
<% if (direction) { %><h4>${direction}:</h4><% } %>
<table class="relative-table" style="width: 100%;">
  <tbody>
    <tr>
      <th>Status</th>
      <th>IDU Name</th>
      <th>Description</th>
      <th>Duration</th>
      <th>RF</th>
      <th>Bandwidth</th>
      <th>Traffic</th>
      <th>Success Criteria</th>
      <th>Warning Criteria</th>
      <th>Errors</th>
    </tr>
    <% streams.each { stream -> %>
    <tr>
      <td>
        <ac:structured-macro ac:name="status" ac:schema-version="1">
          <ac:parameter ac:name="colour"><%= stream['Stream Status']?.toUpperCase() == 'PASS' ? 'Green' : stream['Stream Status']?.toUpperCase() == 'WARNING' ? 'Yellow' : stream['Stream Status']?.toUpperCase() == 'FAIL' ? 'Red' : 'Grey' %></ac:parameter>
          <ac:parameter ac:name="title"><%= stream['Stream Status'] ?: '' %></ac:parameter>
        </ac:structured-macro>
      </td>
      <td><%= stream['IDU Name'] ?: '' %></td>
      <td><%= stream['Stream Description'] ?: '' %></td>
      <% groupedConfigs.each { keys -> %>
      <td>
        <% keys.each { key -> %>
        <% if (stream[key] != null && stream[key] != '-') { %>${key}: ${stream[key]}<br/><% } %>
        <% } %>
      </td>
      <% } %>
      <td><%= stream['Success Criteria'] != null ? String.format('%.2E', stream['Success Criteria'] as Double) : '' %></td>
      <td><%= stream['Warning Criteria'] != null ? String.format('%.2E', stream['Warning Criteria'] as Double) : '' %></td>
      <td>
        <%= stream['Stream Status']?.toUpperCase() == 'FAIL' ? "Stream didn't meet success criteria" : '' %>
      </td>
    </tr>
    <% } %>
  </tbody>
</table>
<% } %>
<% } %>
<% while (expectedId <= totalTests) { %>
<% showMissingTest(expectedId) %>
<% expectedId++ %>
<% } %>
<% } %>
<% if (binding.hasVariable('footer') && footer != null) { %>
${footer}
<% } %>
