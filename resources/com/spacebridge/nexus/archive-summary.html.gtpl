<% def escape = { groovy.xml.XmlUtil.escapeXml(it.toString()) } %>
<h2>${escape(archive.file)}</h2>
<% if (problems) { %>
<p>Not uploaded: ${escape(problems.join(', '))}</p>
<% } else { %>
<p>Ready to upload as ${escape(archive.artifactId)}:${escape(archive.version)}</p>
<% } %>
<p>sha256 ${checksum}</p>
<table>
  <tr><th>File</th><th>Bytes</th></tr>
<% content.each { file -> %>
  <tr><td>${escape(file.path - 'content/')}</td><td>${file.length}</td></tr>
<% } %>
</table>
