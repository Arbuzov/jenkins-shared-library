/**
 * This takes jenkins build as an input and returns
 * HTML changelog for release notes
 */
String call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  deb build = arg.BUILD
  deb HEADER = argv.HEADER ?: '2'
  return inflateTemplate(
    STRINGIFY: true,
    TEMPLATE: libraryResource('com/spacebridge/confluence/jenkins-build-changes.gtpl'),
    changeSets: build.getChangeSets(),
    headerLevel: HEADER,
    build: build,
    rootURL: 'https://jenkins.asatnet.net',
    formattedTimestamp: build.getTime().toString()
  )
    .replaceAll(/(\r\n|\r|\n)/, '\\\\n')
    .replaceAll('<br>', '<br/>')
    .trim()
}