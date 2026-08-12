def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  String repository = arg.repository
  String packageName = arg.packageName
  String nexusURL = arg.nexusURL ?: 'http://deb.repo.asatnet.net/debian'
  
  def versions = []

  try {
    def entries = httpRequest(
      url: "${nexusURL}/dists/${repository}/main/binary-amd64/Packages",
      httpMode: 'GET',
      consoleLogResponseBody: false,
      quiet: true
    ).getContent().split("\n\n")

    entries.each { entry ->
      if (entry.contains("Package: ${packageName}")) {
        def versionLine = entry.readLines().find { it.startsWith("Version: ") }
        if (versionLine) {
          def version = versionLine.split(":", 2)[1].trim()
          versions << version
        }
      }
    }
  } catch (Exception e) {
    echo "Warning: Could not fetch versions for ${packageName}: ${e.message}"
  }
  return versions.unique().sort()
}
