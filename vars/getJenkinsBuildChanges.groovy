import groovy.text.SimpleTemplateEngine;
/**
 * This takes jenkins build as an input and returns
 * HTML changelog for release notes
 */
String call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  deb build = arg.BUILD
  deb HEADER = argv.HEADER ?: 'h2'
}