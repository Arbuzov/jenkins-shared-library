def call() {
  return readFile(
    file: findFiles(
      glob: "**/*.fw.version"
    )[0].path
  ).trim()
}