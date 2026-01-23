<h2>Version information</h2>
<ac:structured-macro ac:name="details" ac:schema-version="1" ac:macro-id="26c82d28-bffd-478b-bdfe-9664ee12e71e">
  <ac:parameter ac:name="id">sb-changelog-page</ac:parameter>
  <ac:rich-text-body>
    <table class="relative-table wrapped" style="letter-spacing: 0.0px;width: 80%;">
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
          <td scope="col">
            <% if (binding.hasVariable('build') && build != null) { %>
            <a href="${build.getAbsoluteUrl()}">${build.getBuildCauses().last().shortDescription }</a>
            <% } %>
          </td>
        </tr>
      </tbody>
    </table>
  </ac:rich-text-body>
</ac:structured-macro>
<% if (binding.hasVariable('dependency') && dependency != null) { %>
<h2>Dependency</h2>
<ac:structured-macro ac:name="details" ac:schema-version="1" ac:macro-id="dep-details-macro">
  <ac:parameter ac:name="id">sb-dependency-info</ac:parameter>
  <ac:rich-text-body>
    <table class="relative-table wrapped" style="letter-spacing: 0.0px;width: 80%;">
      <colgroup>
        <col style="width: 30%;" />
        <col style="width: 70%;" />
      </colgroup>
      <tbody>
        <% dependency.each { key, value -> %>
        <tr>
          <th>${key}</th>
          <td>
            <div class="content-wrapper">
              <p>${value}</p>
            </div>
          </td>
        </tr>
        <% } %>
      </tbody>
    </table>
  </ac:rich-text-body>
</ac:structured-macro>
<% } %>
<h2>Dependency</h2>
<ac:structured-macro ac:name="dependency" ac:schema-version="1" ac:macro-id="26c82d28-bffd-478b-bdfe-9664ee12e71e">
  <ac:parameter ac:name="id">sb-compartibility-matrix</ac:parameter>
  <ac:rich-text-body>
    <table class="relative-table wrapped" style="letter-spacing: 0.0px;width: 80%;">

    </table>
  </ac:rich-text-body>
</ac:structured-macro>
<% } %>
<% if (binding.hasVariable('build') && build != null) { %>
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
<% } %>
<h2>Tests for the package</h2>
<ac:structured-macro ac:name="detailssummary" ac:schema-version="2" ac:macro-id="6ba6a98e-dd94-40c8-a96d-efd13ec4677a">
  <ac:parameter ac:name="headings">Caused,Status,Tested at,confd-basic,${packageName},mcd4-lite,ncc,</ac:parameter>
  <ac:parameter ac:name="cql">label = &quot;test-report&quot; and space in (&quot;VeVa&quot;,&quot;DEVOPS&quot;) and text ~ &quot;${packageName}|${version}&quot;</ac:parameter>
</ac:structured-macro>
<h2>Releases including package</h2>
<ac:structured-macro ac:name="detailssummary" ac:schema-version="2" ac:macro-id="02a56091-d447-4dfc-9662-e29dab0315e1">
  <ac:parameter ac:name="headings">Version,Build,Project,Status</ac:parameter>
  <ac:parameter ac:name="cql">label = &quot;sb-changelog-page&quot; and space = &quot;PSIN&quot; and text ~ &quot;Key components|${version}&quot;</ac:parameter>
</ac:structured-macro>
<% if (binding.hasVariable('footer') && footer != null) { %>
${footer}
<% } %>
