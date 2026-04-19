// This method processes a single test case from the JUnit XML and extracts relevant details,
// including the test case's class name, name, execution time, and failure information (if any).
@NonCPS
def processTestCase(testcase) {
    def result = [
        classname: testcase.getProperty('@classname').toString(),
        name: testcase.getProperty('@name').toString(),
        time: testcase.getProperty('@time').toString()
    ]
    if (testcase.failure.size() > 0) {
        result.failure = [
            message: testcase.failure.getProperty('@message').toString(),
            type: testcase.failure.getProperty('@type').toString(),
            content: testcase.failure.text().toString()
        ]
    }
    if (testcase.error.size() > 0) {
        result.error = [
            message: testcase.error.getProperty('@message').toString(),
            type: testcase.error.getProperty('@type').toString(),
            content: testcase.error.text().toString()
        ]
    }
    if (testcase.skipped.size() > 0) {
        result.skipped = [
            message: testcase.skipped?.getProperty('@message').toString(),
            type: testcase.skipped?.getProperty('@type').toString(),
            content: testcase.skipped?.text().toString()
        ]
    }
    return result
}

// This method processes a single test suite from the JUnit XML and extracts relevant details,
// including the suite's name, error count, failure count, skipped tests, total tests, execution time,
// and the list of test cases within the suite.
@NonCPS
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

def call(Map arg) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    String fileName = arg.get("file")
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
                if (testcase.failure || testcase.error) {
                    results.status = 'FAILED'
                }
            }
        }
        return results
    } catch (Exception e) {
        error("Failed to parse JUnit XML for file '${fileName}': ${e.message}")
    }
}
