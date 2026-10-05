def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  String repository = arg.repository
  String nexusURL = arg.nexusURL ?: 'http://deb.repo.asatnet.net/debian'
  // packageNames asks for several packages in one fetch; the index is ~7 MB
  boolean batch = arg.packageNames != null
  List names = batch ? arg.packageNames : [arg.packageName]

  def versions = names.collectEntries { [(it): []] }

  try {
    def entries = httpRequest(
      url: "${nexusURL}/dists/${repository}/main/binary-amd64/Packages",
      httpMode: 'GET',
      consoleLogResponseBody: false,
      quiet: true
    ).getContent().split("\n\n")

    for (entry in entries) {
      def lines = entry.readLines()
      def packageLine = lines.find { it.startsWith("Package: ") }
      if (!packageLine) {
        continue
      }
      // whole field, not a substring: "Package: ncc" must not match ncc-tools
      def name = packageLine.split(":", 2)[1].trim()
      if (!versions.containsKey(name)) {
        continue
      }
      def versionLine = lines.find { it.startsWith("Version: ") }
      if (versionLine) {
        versions[name] << versionLine.split(":", 2)[1].trim()
      }
    }
  } catch (Exception e) {
    // Configurators must not turn an unavailable index into valid empty choices.
    if (arg.failOnError) {
      throw e
    }
    echo "Warning: Could not fetch versions for ${names.join(', ')}: ${e.message}"
  }

  def result = versions.collectEntries { name, found -> [(name): found.unique().sort()] }
  return batch ? result : result[arg.packageName]
}
