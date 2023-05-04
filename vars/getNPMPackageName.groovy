def call(rootDir = pwd()) {
  result = ''
  try {
    result = readJSON(file: rootDir+'/package.json').name
  } catch (Exception e) {
    println "File is not valid or not exists " + e
  }
  return result;
}