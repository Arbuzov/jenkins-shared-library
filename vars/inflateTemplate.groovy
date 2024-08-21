import groovy.text.SimpleTemplateEngine;

def call(Map arg) {
  def engine = new SimpleTemplateEngine()
  def template = engine.createTemplate(arg.TEMPLATE).make(arg)
  return template.toString()
}