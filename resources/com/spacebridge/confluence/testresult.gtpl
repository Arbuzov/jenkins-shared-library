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
<% if (binding.hasVariable('results') && results != null) { %>
<%
def groupedConfigs = [
  ['Expected Duration', 'Warm-up Duration'],
  ['Center Frequency (Mhz)', 'Frequency Bandwidth (MHz)', 'Target ModCod', 'Target Waveform ID', 'Es/No Delta (dB)', 'Es/No override (dB)', 'Roll-off', 'Attenuation Level (dB)', 'Attenuation Step (dB)', 'Attenuation Dwell Time (sec)'],
  ['MIR Bit Rate (Kbps)', 'CIR Bit Rate (Kbps)', 'CRA Bit Rate (Kbps)'],
  ['Traffic Rate (Kbps)', 'Rate Step (Kbps)', 'Rate Dwell Time (sec)', 'Packet Size (bytes)', 'Traffic Type'],
]
%>
<h2>Test Cases</h2>
<% results?.eachWithIndex { tc, idx -> %>
<h3>Test ${tc.testId}</h3>
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
    </tr>
    <% streams.each { stream -> %>
    <tr>
      <td>
        <ac:structured-macro ac:name="status" ac:schema-version="1">
          <ac:parameter ac:name="colour"><%= stream['Stream Status']?.toUpperCase() == 'PASS' ? 'Green' : 'Red' %></ac:parameter>
          <ac:parameter ac:name="title"><%= stream['Stream Status'] ?: '' %></ac:parameter>
        </ac:structured-macro>
      </td>
      <td><%= stream['IDU Name'] ?: '' %></td>
      <td><%= stream['Stream Description'] ?: '' %></td>
      <% groupedConfigs.each { keys -> %>
      <td>
        <% keys.each { key -> %>
        <% if (stream[key] != null) { %>${key}: ${stream[key]}<br/><% } %>
        <% } %>
      </td>
      <% } %>
      <td><%= stream['Success Criteria'] ?: '' %></td>
      <td><%= stream['Warning Criteria'] ?: '' %></td>
    </tr>
    <% } %>
  </tbody>
</table>
<% } %>
<% } %>
<% } %>
<% if (binding.hasVariable('footer') && footer != null) { %>
${footer}
<% } %>
