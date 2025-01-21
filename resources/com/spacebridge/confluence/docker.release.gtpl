<!--
Template variables:
  build
  version
  imageUri
  projectId
-->
<h2>Version information</h2>
<ac:structured-macro ac:name="details" ac:schema-version="1"
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
                    <em><time datetime="${new Date().format('yyyy-MM-dd')}" />&nbsp;<br /></em>
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
              <p><a href="${build.getAbsoluteUrl()}">${build.getAbsoluteUrl()}</a></p>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <p class="auto-cursor-target"><br /></p>
  </ac:rich-text-body>
</ac:structured-macro>
<h2>Usage</h2>
<ac:structured-macro ac:name="code" ac:schema-version="1" ac:macro-id="df2d4001-ebaa-480b-8c12-2d68c8a2fd5c">
  <ac:parameter ac:name="language">bash</ac:parameter>
  <ac:parameter ac:name="theme">Midnight</ac:parameter>
  <ac:plain-text-body><![CDATA[docker pull ${imageUri}]]></ac:plain-text-body>
</ac:structured-macro>
<h2>Changes</h2>
<p>
<%
def hadChanges = false
build?.changeSets.each { changeSet -> 
    hadChanges = true
    def browser = changeSet.browser
%>
<ol>
<% changeSet.eachWithIndex { c, loopIndex -> %>
  <li value='${c.revision.class.name == "java.lang.Integer" ? c.revision : null}'>
    ${c.msgAnnotated}
    &#8212;
    <a target='_blank' href='https://jenkins.asatnet.net/${c.author.url}/' class='model-link inside'>${c.author.displayName}</a> /
    
    <% def cslink = browser.getChangeSetLink(c) %>
    <% if (cslink) { %>
      <a target='_blank' href='${cslink}'>${browser.descriptor.displayName}</a>
    <% } else { %>
      <a target='_blank' href='${build.number}/changes#detail${loopIndex}'>Detail</a>
    <% } %>
  </li>
<% } %>
</ol>
<% } %>
</p>
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
<% if (binding.hasVariable('bomChangeset') && bomChangeset != null) { %>
<h2>BOM changes</h2>
  <% if (bomChangeset?.added?.size()>0) { %>
    <h3>Added Packages</h3>
  <% } %>
  <% if (bomChangeset?.removed?.size()>0) { %>
    <h3>Removed Packages</h3>
  <% } %>
  <% if (bomChangeset?.updated?.size()>0) { %>
    <h3>Updated Packages</h3>
  <% } %>
<% } %>
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