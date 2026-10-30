import groovy.text.SimpleTemplateEngine;

String call(Map arg) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  def engine = new SimpleTemplateEngine()
  def STRINGIFY = arg.STRINGIFY ?: false
  def TEMPLATE = arg.TEMPLATE ?: ''
  def template = engine.createTemplate(TEMPLATE).make(arg).toString()
  if (STRINGIFY) {
    template = stringify(template)
  }
  return template
}

// Strips newlines outside CDATA sections; CDATA content (e.g. noformat macro) is kept as is.
// @NonCPS: closures passed to regex methods do not run reliably in CPS pipeline code.
@NonCPS
String stringify(String template) {
  def out = new StringBuilder()
  def matcher = java.util.regex.Pattern.compile('(?s)<!\\[CDATA\\[.*?\\]\\]>').matcher(template)
  int last = 0
  while (matcher.find()) {
    out.append(template.substring(last, matcher.start()).replaceAll(/(\r\n|\r|\n)/, ''))
    out.append(matcher.group())
    last = matcher.end()
  }
  out.append(template.substring(last).replaceAll(/(\r\n|\r|\n)/, ''))
  return out.toString()
}
