import groovy.text.SimpleTemplateEngine;

String call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def engine = new SimpleTemplateEngine()
  def STRINGIFY = arg.STRINGIFY ?: false
  def TEMPLATE = arg.TEMPLATE ?: ''
  def template = engine.createTemplate(TEMPLATE).make(arg).toString()
  if (STRINGIFY) {
    // Preserve newlines inside CDATA sections (e.g. noformat macro content)
    def cdataBlocks = []
    template = template.replaceAll('(?s)<!\\[CDATA\\[(.*?)\\]\\]>') { match ->
      cdataBlocks << match[1]
      "__CDATA_BLOCK_${cdataBlocks.size() - 1}__"
    }
    template = template.replaceAll(/(\r\n|\r|\n)/, '')
    cdataBlocks.eachWithIndex { content, i ->
      template = template.replace("__CDATA_BLOCK_${i}__", "<![CDATA[${content}]]>")
    }
  }
  return template
}
