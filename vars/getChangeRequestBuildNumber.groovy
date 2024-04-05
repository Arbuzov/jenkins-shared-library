// getChangeRequestBuildNumber(): Get build number with Change ID encoded
//
// Example usage:
//	environment {
//		CHANGE_REQUEST_BUILD_NUMBER = """${ getChangeRequestBuildNumber() }"""
//	}
//
// Recommended variable name: CHANGE_REQUEST_BUILD_NUMBER
//

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	String changeReqBuildNumber = '123456789'

	if (env.getAt('BUILD_NUMBER') == null) {
		return changeReqBuildNumber
	}

	changeReqBuildNumber = env.BUILD_NUMBER

	if (env.getAt('CHANGE_ID') == null) {
		return changeReqBuildNumber
	}

	if (env.BUILD_NUMBER?.isInteger() && env.CHANGE_ID?.isInteger()) {
		int build_num = env.BUILD_NUMBER as Integer
		int change_id_num = env.CHANGE_ID as Integer
		int change_id_remain = change_id_num % 10000
		int build_num_with_id = change_id_remain * 1000 + build_num % 1000

		changeReqBuildNumber = build_num_with_id as String
	}

	return changeReqBuildNumber
}
