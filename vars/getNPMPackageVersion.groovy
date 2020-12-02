import groovy.json.JsonSlurper;

def call(rootDir = pwd()) {
  result = ''
  try {
    packageContent = readFile(rootDir+'/package.json')
    result = new JsonSlurper().parseText(packageContent).version
  } catch (Exception e) {
    println "File is not valid or not exists " + e
  }
  return result;
}