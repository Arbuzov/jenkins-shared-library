def call() {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  return readFile(
    file: findFiles(
      glob: "**/*.fw.version"
    )[0].path
  ).trim()
}