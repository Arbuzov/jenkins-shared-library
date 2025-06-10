<% if (binding.hasVariable('collapse') && collapse) { %>
<p>
<ac:structured-macro ac:name="expand" ac:schema-version="1" ac:macro-id="de6c27aa-4634-4248-8823-db29367943eb">
<ac:parameter ac:name="title">Click here to view attachments...</ac:parameter>
<ac:rich-text-body>
<% } %>
<p>
  <ac:structured-macro ac:name="attachments" ac:schema-version="1" ac:macro-id="09f50983-5b2c-4b55-b7b5-1ba3d300222c">
    <ac:parameter ac:name="upload"><% if (binding.hasVariable('upload') && upload) { %>true<% } else { %>false<% } %></ac:parameter>
    <ac:parameter ac:name="new">false</ac:parameter>
    <ac:parameter ac:name="old">false</ac:parameter>
  </ac:structured-macro>
</p>
<% if (binding.hasVariable('collapse') && collapse) { %>
</ac:rich-text-body>
</ac:structured-macro>
</p>
<% } %>
