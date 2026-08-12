
/**
 * Try to guess the Jira issue associated with the current build.
 * The function does it by finding the MR number, extracting Jira issue key from the MR title
 * (see jiraKeyFromTitle) and then using the Jira API to get the issue details.
 *
 * @return Jira issue object (null if no issue found).
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  
  def mr = gitlabFindMR()
  if (mr) {
    def key = jiraKeyFromTitle(mr.title)

    if(key) {
      try {
        return getJiraIssueInfo(issueId: key, credentialsId: arg.credentialsId ?: 'jira-username-password')
      } catch (Exception e) {
        echo "Failed to get Jira issue info for Key=${key}: ${e?.message ?: 'unknown error'}"
      }
    }
  }
  return null
}
