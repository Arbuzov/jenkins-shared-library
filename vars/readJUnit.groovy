// This method processes a single test case from the JUnit XML and extracts relevant details,
// including the test case's class name, name, execution time, and failure information (if any).
def processTestCase(testcase) {
  def result = [
    classname: testcase.@classname.toString(),
    name: testcase.@name.toString(),
    time: testcase.@time.toString()
  ]
  if (testcase.failure.size() > 0) {
    result.failure = [
      message: testcase.failure.@message.toString(),
      type: testcase.failure.@type.toString(),
      content: testcase.failure.text().toString()
    ]
  }
  return result
}

// This method processes a single test suite from the JUnit XML and extracts relevant details,
// including the suite's name, error count, failure count, skipped tests, total tests, execution time,
// and the list of test cases within the suite.
def processTestSuite(suite) {
  def result = [
    name: suite.getProperty('@name').toString(),
    errors: suite.getProperty('@errors').toString(),
    failures: suite.getProperty('@failures').toString(),
    skipped: suite.getProperty('@skipped').toString(),
    tests: suite.getProperty('@tests').toString(),
    time: suite.getProperty('@time').toString(),
    testcases: []
  ]
  suite.testcase.each { testcase ->
    result.testcases << processTestCase(testcase)
  }
  return result
}

def call(Map argv) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String fileName = argv.get("file")
  if (!fileName) {
    error("File name is required but not provided.")
  }

  try {
    def xmlContent = readFile(fileName)
    def xml = new XmlSlurper().parseText(xmlContent)
    def results = [:]
    results.status = 'SUCCESS'
    results.testsuites = []
    xml.testsuite.each { suite ->
      results.testsuites << processTestSuite(suite)
    }
    results.testsuites << processTestSuite(xml)
    results.testsuites.each { suite ->
      suite.testcases.each { testcase ->
        if (testcase.failure) {
          results.status = 'FAILED'
        }
      }
    }
    return results
  } catch (Exception e) {
    error("Failed to parse JUnit XML for file '${fileName}': ${e.message}")
  }
}