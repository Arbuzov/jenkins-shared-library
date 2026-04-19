/**
 * This takes jenkins build as an input and returns
 * HTML changelog for release notes
 */
def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def build = arg.BUILD
  def HEADER = arg.HEADER ?: '2'
  Boolean DEBUG = arg.DEBUG ?: false
  if (DEBUG) {
    println(arg.toString())
  }
  if (arg.PROJECT_ID) {
    def project = Jenkins.instance.getItemByFullName(arg.PROJECT_ID)
    if (DEBUG) {
      println(project.toString())
    }
    if (project && arg.BUILD_NUMBER.toString()) {
      if (DEBUG) {
        println(arg.BUILD_NUMBER.toString())
      }
      build = project.getBuild(arg.BUILD_NUMBER.toString())
      if (DEBUG) {
        println(build.toString())
      }
    }
  }
  if (build) {
    return inflateTemplate(
      STRINGIFY: true,
      TEMPLATE: libraryResource('com/example/confluence/jenkins-build-changes.gtpl'),
      changeSets: build.getChangeSets(),
      headerLevel: HEADER,
      build: build,
      rootURL: 'https://jenkins.example.com',
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
