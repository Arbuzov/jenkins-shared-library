/**
 * This takes jenkins build as an input and returns
 * HTML changelog for release notes
 */
def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def build = arg.BUILD
  def HEADER = arg.HEADER ?: '2'
  if (arg.PROJECT_ID) {
    def project = Jenkins.instance.getItemByFullName(arg.PROJECT_ID)
    if (project && arg.BUILD_NUMBER) {
      build = project.getBuild(BUILD_NUMBER)
    }
  }
  if (build) {
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
  } else {
    println("Build not found")
    return false
  }
}
