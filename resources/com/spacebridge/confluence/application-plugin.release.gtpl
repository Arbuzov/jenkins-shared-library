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
              <p><span style="color: rgb(112,112,112);"><span style="background-color: rgb(245,245,245);"><em><time
                        datetime="${date}" />&nbsp;<br /></em></span></span></p>
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
      </tbody>
    </table>
    <p class="auto-cursor-target"><br /></p>
  </ac:rich-text-body>
</ac:structured-macro>
<h2>Included images</h2>
${images}
<h2>BOMs</h2>
<ac:structured-macro ac:name="expand" ac:schema-version="1"
  ac:macro-id="e3f4c4f9-dfa2-4e8a-9eb9-ad1717a7a3e8">
  <ac:rich-text-body>
    <ac:structured-macro ac:name="noformat" ac:schema-version="1" ac:macro-id="6ae95228-7a8b-46fb-8801-47392f3a97c1">
      <ac:plain-text-body>
        <![CDATA[${boms}]]>
      </ac:plain-text-body>
    </ac:structured-macro>
  </ac:rich-text-body>
</ac:structured-macro>
<% if (binding.hasVariable('bomChangeset') && bomChangeset != null) { %>
<% if (bomChangeset?.removed?.size() || bomChangeset?.added?.size() || bomChangeset?.updated?.size()) { %>
<h2>BOM changes</h2>
<% } %>
  <% if (bomChangeset?.added?.size()>0) { %>
    <h3>Added Packages</h3>
    <table>
    <colgroup><col/><col/></colgroup>
    <tbody>
    <tr>
      <th scope="col">Name</th>
      <th scope="col">Version</th>
    </tr>
    <% bomChangeset?.added.each { pack, ver -> %>
      <tr>
        <td>${pack}</td>
        <td>${ver}</td>
      </tr>
    <% } %>
    </tbody>
    </table>
  <% } %>
  <% if (bomChangeset?.removed?.size()>0) { %>
    <h3>Removed Packages</h3>
    <table>
    <colgroup><col/><col/></colgroup>
    <tbody>
    <tr>
      <th scope="col">Name</th>
      <th scope="col">Version</th>
    </tr>
    <% bomChangeset?.removed.each { pack, ver -> %>
      <tr>
        <td>${pack}</td>
        <td>${ver}</td>
      </tr>
    <% } %>
    </tbody>
    </table>
  <% } %>
  <% if (bomChangeset?.updated?.size()>0) { %>
    <h3>Updated Packages</h3>
    <table>
    <colgroup><col/><col/></colgroup>
    <tbody>
    <tr>
      <th scope="col">Name</th>
      <th scope="col">Version</th>
    </tr>
    <% bomChangeset?.updated.each { pack, ver -> %>
      <tr>
        <td>${pack}</td>
        <td>${ver}</td>
      </tr>
    <% } %>
    </tbody>
    </table>
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
<h2>Download links to software</h2>
<table class="wrapped">
  <colgroup>
    <col />
    <col />
  </colgroup>
  <tbody>
    <tr>
      <th scope="col">File description</th>
      <th scope="col">File</th>
    </tr>
    <tr>
      <td>Plugin deliverable</td>
      <td>
        <a href="${pluginUrl}">${pluginUrl}</a>
      </td>
    </tr>
  </tbody>
</table>
<h2>Changes</h2>
<p>
<%
def hadChanges = false
build.changeSets.each { changeSet -> 
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
<h2>Installation instructions</h2>
<p>
  <ac:structured-macro ac:name="include" ac:schema-version="1" ac:macro-id="428ea52c-de8b-4747-afe0-ce6df429fd8b">
    <ac:parameter ac:name="">
      <ac:link>
        <ri:page ri:space-key="NNG" ri:content-title="Application Plugin Usage" />
      </ac:link>
    </ac:parameter>
  </ac:structured-macro>
</p>