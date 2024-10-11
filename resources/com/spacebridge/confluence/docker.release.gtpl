<%

%>
<h2>Version information</h2><ac:structured-macro ac:name="details" ac:schema-version="1"
  ac:macro-id="26c82d28-bffd-478b-bdfe-9664ee12e71e">
  <ac:parameter ac:name="id">sb-changelog-page</ac:parameter><ac:rich-text-body>
    <p class="auto-cursor-target"><br /></p>
    <table class="relative-table wrapped" style="letter-spacing: 0.0px;width: 50.5435%;">
      <colgroup>
        <col style="width: 17.7419%;" />
        <col style="width: 82.1506%;" />
      </colgroup>
      <tbody>
        <tr>
          <th>
            <p>Date</p>
          </th>
          <td>
            <div class="content-wrapper">
              <p>
                <span style="color: rgb(112,112,112);">
                  <span style="background-color: rgb(245,245,245);">
                    <em><time datetime="${date}" />&nbsp;<br /></em>
                  </span>
                </span>
              </p>
            </div>
          </td>
        </tr>
        <tr>
          <th>Version</th>
          <td>
            <div class="content-wrapper">
              <p>
                ${version}
              </p>
            </div>
          </td>
        </tr>
        <tr>
          <th>Project</th>
          <td>
            <div class="content-wrapper">
              <p><a href="https://jira.spacebridge.com/projects/${projectId}">${projectId}</a></p>
            </div>
          </td>
        </tr>
        <tr>
          <th>Status</th>
          <td class="confluenceTd">
            <div class="content-wrapper">
              <p>
                <ac:structured-macro ac:name="status" ac:schema-version="1" ac:macro-id="a76e32d7-de84-4563-913b-a6c44ab68b62">
                  <ac:parameter ac:name="title">draft</ac:parameter>
                </ac:structured-macro>
              </p>
            </div>
          </td>
        </tr>
        <tr>
          <th>Build</th>
          <td>
            <div class="content-wrapper">
              <p><a href="${buildUrl}">${buildUrl}</a></p>
            </div>
          </td>
        </tr>
        <tr>
          <th>Key components</th>
          <td>
            <div class="content-wrapper">
              ${keyComponents}
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="auto-cursor-target"><br /></p>
  </ac:rich-text-body>
</ac:structured-macro>
<h2>Image URL</h2>
<p>${imageUrl}</p>
<h2>Changes</h2>
<p>${changeLog}</p>
<h2>BOMs</h2>
<ac:structured-macro ac:name="expand" ac:schema-version="1" ac:macro-id="e3f4c4f9-dfa2-4e8a-9eb9-ad1717a7a3e8">
  <ac:rich-text-body>
    <ac:structured-macro ac:name="noformat" ac:schema-version="1" ac:macro-id="6ae95228-7a8b-46fb-8801-47392f3a97c1">
      <ac:plain-text-body>
        <![CDATA[${boms}]]>
      </ac:plain-text-body>
    </ac:structured-macro>
  </ac:rich-text-body>
</ac:structured-macro>
<h2>Vulnerability report</h2>
<ac:structured-macro ac:name="expand" ac:schema-version="1" ac:macro-id="e3f4c4f9-dfa2-4e8a-9eb9-ad1717a7a3e8">
  <ac:rich-text-body>
    <ac:structured-macro ac:name="noformat" ac:schema-version="1" ac:macro-id="6ae95228-7a8b-46fb-8801-47392f3a97c1">
      <ac:plain-text-body>
        <![CDATA[${vulnerability}]]>
      </ac:plain-text-body>
    </ac:structured-macro>
  </ac:rich-text-body>
</ac:structured-macro>
