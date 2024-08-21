import groovy.text.SimpleTemplateEngine;

def call(Map arg) {
  def engine = new SimpleTemplateEngine()
  def STRINGIFY = arg.STRINGIFY ?: false
  def TEMPLATE = STRINGIFY ? arg.TEMPLATE.replaceAll(/(\r\n|\r|\n)/, '') : arg.TEMPLATE
  def template = engine.createTemplate(TEMPLATE).make(arg)
  return template.toString()
}