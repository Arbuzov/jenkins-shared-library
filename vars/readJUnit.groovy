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
    results.testsuites = []
    xml.testsuite.each { suite ->
      def suiteResult = [
        name: suite.getProperty('@name').toString(),
        errors: suite.getProperty('@errors').toString(),
        failures: suite.getProperty('@failures').toString(),
        skipped: suite.getProperty('@skipped').toString(),
        tests: suite.getProperty('@tests').toString(),
        time: suite.getProperty('@time').toString(),
        testcases: []
      ]
      results.status = suite.getProperty('@failures').toInteger()+suite.getProperty('@errors').toInteger() > 0 ? 'FAILED' : 'SUCCESS'
      suite.testcase.each { testcase ->
        def testcaseResult = [
          classname: testcase.getProperty('@classname').toString(),
          name: testcase.getProperty('@name').toString(),
          time: testcase.getProperty('@time').toString()
        ]
        if (testcase?.failure.size() > 0) {
          testcaseResult.failure = [
            message: testcase.failure.getProperty('@message').toString(),
            type: testcase.failure.getProperty('@type').toString(),
            content: testcase.failure.text().toString()
          ]
        }
        suiteResult.testcases << testcaseResult
      }
      results.testsuites << suiteResult
    }

    return results
  } catch (Exception e) {
    error("Failed to parse JUnit XML for file '${fileName}': ${e.message}")
  }
}