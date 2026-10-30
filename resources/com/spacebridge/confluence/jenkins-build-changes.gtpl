<%
def hadChanges = false
changeSets.each { changeSet -> 
    hadChanges = true
    def browser = changeSet.browser
%>
<h${headerLevel?: '2'}>
  <a target='_blank' href='${rootURL}/${build.url}'>${build.displayName}(<i>${formattedTimestamp}</i>)</a>
</h${headerLevel?: '2'}>
<ol>
<% changeSet.eachWithIndex { c, loopIndex -> %>
  <li value='${c.revision.class.name == "java.lang.Integer" ? c.revision : null}'>
    ${c.msgAnnotated}
    &#8212;
    <a target='_blank' href='${rootURL}/${c.author.url}/' class='model-link inside'>${c.author.displayName}</a> /
    
    <% def cslink = browser?.getChangeSetLink(c) %>
    <% if (cslink) { %>
      <a target='_blank' href='${cslink}'>${browser.descriptor.displayName}</a>
    <% } else { %>
      <a target='_blank' href='${build.number}/changes#detail${loopIndex}'>Detail</a>
    <% } %>
  </li>
<% } %>
</ol>
<% } %>
