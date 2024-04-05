def call(rootDir = pwd()) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  result = ''
  try {
    result = readJSON(file: rootDir+'/package.json').name
  } catch (Exception e) {
    println "File is not valid or not exists " + e
  }
  return result;
}