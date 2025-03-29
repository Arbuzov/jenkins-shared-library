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
          <th scope="col">Test pipeline</th>
          <td scope="col">
            <% if (binding.hasVariable('build') && build != null) { %>
            <a href="${build.getAbsoluteUrl()}">b${build.getNumber()}</a>
            <% } %>
          </td>
        </tr>
        <tr>
          <th scope="col">Caused</th>
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
<% if (binding.hasVariable('params') && build != params) { %>
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
<h2>Test report</h2>
<table class="relative-table" style="width: 100%;">
  <colgroup>
    <col/>
    <col/>
    <col/>
    <col/>
    <col style="width: 50%;"/>
  </colgroup>
  <tbody>
    <tr>
      <th scope="col">Name</th>
      <th scope="col">Status</th>
      <th scope="col">Duration</th>
      <th scope="col">Class Name</th>
      <th scope="col">Output</th>
    </tr>
    <% results?.testsuites?.each { testsuite -> %>
    <% testsuite?.testcases?.each { testcase -> %>
    <tr>
      <td><%= testcase.name.replaceAll('"', '&quot;').replaceAll("'", '&#39;').replaceAll(/\n/, '').replaceAll(/>\s+</, '><') %></td>
      <td>
        <div class="content-wrapper">
          <ac:structured-macro ac:name="status" ac:schema-version="1" ac:macro-id="ce178cd5-5503-4026-af1d-fa5c87aec3c6">
            <ac:parameter ac:name="colour"><%= testcase.failure?.size() > 0 ? 'Red' : (testcase.skipped?.size() > 0 ? 'Grey' : 'Green') %></ac:parameter>
            <ac:parameter ac:name="title"><%= testcase.failure?.size() > 0 ? 'failed' : (testcase.skipped?.size() > 0 ? 'skipped' : 'passed') %></ac:parameter>
          </ac:structured-macro>
        </div>
      </td>
      <td><%= testcase.time %></td>
      <td><%= testcase.classname %></td>
      <td>
        <% if (testcase.failure?.message) { %>
        <span>
          <%= testcase.failure?.message.replaceAll('>', '&gt;').replaceAll('<', '&lt;') %>
        </span>
        <% } %>
        <% if (testcase.failure?.content) { %>
          <ac:structured-macro ac:name="expand" ac:schema-version="1" ac:macro-id="82160c0a-ad87-408b-b0d5-4525183a6a1c">
          <ac:rich-text-body>
            <ac:structured-macro ac:name="noformat" ac:schema-version="1" ac:macro-id="6ae95228-7a8b-46fb-8801-47392f3a97c1">
              <ac:plain-text-body>
                <![CDATA[${testcase.failure?.content.replaceAll('"', '&quot;').replaceAll("'", '&#39;').replaceAll(/(\r\n|\r|\n)/, '\\\\n').replaceAll(/>\s+</, '><')}]]>
              </ac:plain-text-body>
            </ac:structured-macro>
          </ac:rich-text-body>
        </ac:structured-macro>
        <% } %>
      </td>
    </tr>
    <% } %>
    <% } %>
  </tbody>
</table>
<% } %>
<% if (binding.hasVariable('footer') && footer != null) { %>
${footer}
<% } %>