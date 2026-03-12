import groovy.text.SimpleTemplateEngine;

String call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def engine = new SimpleTemplateEngine()
  def STRINGIFY = arg.STRINGIFY ?: false
  def TEMPLATE = arg.TEMPLATE ?: ''
  def template = engine.createTemplate(TEMPLATE).make(arg).toString()
  if (STRINGIFY) {
    template = template.replaceAll(/(\r\n|\r|\n)/, '')
  }
  return template
}
