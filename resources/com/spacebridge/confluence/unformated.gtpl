<ac:structured-macro ac:name="expand" ac:schema-version="1" ac:macro-id="83f04eff-9f22-4dce-823c-f48dd2991aac">
  <ac:rich-text-body>
    <ac:structured-macro ac:name="noformat" ac:schema-version="1" ac:macro-id="b1edfa43-846e-4a6e-92cc-c7b376ea5fb7">
      <ac:plain-text-body>
        <![CDATA[${data.replaceAll(/(\r\n|\r|\n)/g, '\\\\n').replaceAll(/>\s+</, '><').replaceAll('"', '&quot;').replaceAll("'", '&#39;').replaceAll(/\n/, '').replaceAll(/>\s+</, '><').replaceAll('>', '&gt;').replaceAll('<', '&lt;')}]]>
      </ac:plain-text-body>
    </ac:structured-macro>
  </ac:rich-text-body>
</ac:structured-macro>