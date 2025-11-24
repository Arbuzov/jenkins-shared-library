
/**
 * Extract Jira issue key from the MR title.
 * For example, given the MR title "A-123: Bugs fixed", the function will return "A-123".
 * Correctly handles `Draft:` prefix.
 *
 * @param title The MR title.
 * @return Jira issue key (null if no key found).
 */
def jiraKeyFromTitle(String title) {
  title = title.trim()
  if (title.startsWith('Draft: ')) {
    title = title.substring(7).trim()
  }
  def matcher = ( (title ?: '').toUpperCase() =~ /([A-Za-z0-9]+-\d+):/ )
  if(matcher){
    return matcher[0][1]
  }
  return null
}


/**
 * Try to guess the Jira issue associated with the current build.
 * The function does it by finding the MR number, extracting Jira issue key from the MR title and then using
 * the Jira API to get the issue details.
 *
 * @return Jira issue object (null if no issue found).
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
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
