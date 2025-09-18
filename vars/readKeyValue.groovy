def call(Map arg) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String fileName = arg.get("file")
  if (!fileName) {
    error("File name is required but not provided.")
  }

  try {
    return readFile(fileName)
      .split('\n')
      .findAll { it.contains('=') } // Ignore lines without '='
      .collectEntries { line ->
        def parts = line.split('=').collect { it.trim() }
        if (parts.size() != 2) {
          error("Invalid key-value pair in line: '${line}'")
        }
        [(parts[0]): parts[1]]
      } as Map<String, String>
  } catch (Exception e) {
    error("Failed to read or parse the file '${fileName}': ${e.message}")
  }
}