// getGitCommitInfoStr(): return commit info as string
//
// Usage example in Jenkinsfile:
//
//      String commit_info_str = getGitCommitInfoStr()
//      String sum_text = ''
//
//      sum_text = sum_text +
//                  "<p>Last commit information:</p>\n" +
//                  "<pre>" + commit_info_str + "</pre>\n"
//
//      publishSummary(sum_text, "Last commit information")
//

def call(Map argm) {
    String commit_Id = 'HEAD'
    String lastCommitMsgHead = ''
    String lastCommitMsgBody = ''
    String lastCommitInfo = ''

    reportUsage(getClass().protectionDomain.codeSource.location.path)

    if (argm) {
        if (argm.containsKey('commitId')) {
            commit_Id = argm.commitId
        }
    }

    String cmdA = "git log --format='format:commit %H%nCommitDate: %cd%n%n%s' --date='format:%Y-%m-%d %H:%M:%S %z' -n 1 '${commit_Id}'"
    String cmdB = "git log --format='format:%b' -n 1 '${commit_Id}'"
    String cmdFold = """fold -s -w 100 | sed -r -e 's/[[:blank:]]+\$//g'"""

    lastCommitMsgHead = sh(script: """${cmdA} | ${cmdFold}""",
                           returnStdout: true,
                           label: 'Get commit subject and date').trim()
    lastCommitMsgBody = sh(script: """${cmdB} | ${cmdFold}""",
                           returnStdout: true,
                           label: 'Get commit message body').trim()

    if (env.BUILD_NUMBER) {
        lastCommitInfo = lastCommitInfo + "BUILD_NUMBER ${env.BUILD_NUMBER}\n"
    }
    lastCommitInfo = lastCommitInfo + lastCommitMsgHead + '\n'
    if (lastCommitMsgBody) {
        // NOTE: need 2 newlines between header and message body
        lastCommitInfo = lastCommitInfo + '\n' + lastCommitMsgBody + '\n'
    }
    return lastCommitInfo
}
