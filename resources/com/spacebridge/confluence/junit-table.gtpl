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
      <td><%= testcase.classname.replaceAll('>', '&gt;').replaceAll('<', '&lt;') %></td>
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
                <![CDATA[${testcase.failure?.content.replaceAll(/(\r\n|\r|\n)/, '\\\\n').replaceAll(/>\s+</, '><')}]]>
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