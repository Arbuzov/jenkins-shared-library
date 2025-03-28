def call(Map argv) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  String fileName = argv.get("file")
  if (!fileName) {
    error("File name is required but not provided.")
  }

  try {
    def xmlContent = readFile(fileName)
    def xml = new XmlSlurper().parseText(xmlContent)

    def results = [testsuites: [], status: 'SUCCESS']
    xml.testsuite.each { suite ->
      int errors = (suite.@errors ?: '0') as int
      int failures = (suite.@failures ?: '0') as int

      def suiteResult = [
        name: suite.@name.toString(),
        errors: errors,
        failures: failures,
        skipped: (suite.@skipped ?: '0').toString(),
        tests: (suite.@tests ?: '0').toString(),
        time: (suite.@time ?: '0').toString(),
        testcases: []
      ]

      // Update overall status
      if (errors + failures > 0) {
        results.status = 'FAILED'
      }

      suite.testcase.each { testcase ->
        def testcaseResult = [
          classname: testcase.@classname.toString(),
          name: testcase.@name.toString(),
          time: testcase.@time.toString()
        ]

        if (testcase.failure.size() > 0) {
          testcaseResult.failure = [
            message: testcase.failure.@message.toString(),
            type: testcase.failure.@type.toString(),
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