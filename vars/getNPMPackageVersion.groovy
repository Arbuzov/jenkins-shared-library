def call(rootDir = pwd()) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  result = ''
  try {
    result = readJSON(file: rootDir+'/package.json').version
  } catch (Exception e) {
    println "File is not valid or not exists " + e
  }
  return result;
}