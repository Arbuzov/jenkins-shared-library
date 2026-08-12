/**
 * Try to guess the Jira issue associated with the current build.
 * The function does it by finding the MR number, extracting the Jira issue key from the MR title
 * (see jiraKeyFromTitle) and then using the Jira API to get the issue details.
 *
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @return Jira issue Map (null if no issue found).
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def mergeRequest = gitlabFindMR()
  if (mergeRequest) {
    def issueKey = jiraKeyFromTitle(mergeRequest.title)

    if (issueKey) {
      try {
        return jiraGetIssueInfo(
          issueKey: issueKey,
          credentialsId: arg.credentialsId ?: 'jira-username-password'
        )
      } catch (Exception exception) {
        echo "jiraGuessIssueInfo(): Failed to get Jira issue info for Key=${issueKey}: ${exception?.message ?: 'unknown error'}"
      }
    }
  }
  return null
}
