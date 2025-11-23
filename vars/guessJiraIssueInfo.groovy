
/**
 * Extract all Jira issue keys from the MR title.
 * For example, given the MR title "A-123: B-765: Bugs fixed", the function will return ["A-123", "B-765"].
 * Correctly handles `Draft:` prefix.
 *
 * @param title The MR title.
 * @return List of Jira issue keys (empty list if no keys found).
 */
def jiraKeysFromTitle(String title) {
  title = title.trim()
  if (title.startsWith('Draft: ')) {
    title = title.substring(7).trim()
  }
  def matcher = ( (title ?: '').toUpperCase() =~ /([A-Za-z0-9]+-\d+):/ )
  if(matcher){
    def matches = matcher.findAll().collect{ it[1] }.unique(false)
    return matches
  }
  return []
}


/**
 * Try to guess the Jira issues associated with the current build.
 * The function does it by finding the MR number, extracting all Jira issue keys from the MR title and then using
 * the Jira API to get the issue details for each key.
 *
 * @return List of Jira issue objects (empty list if no issues found).
 */
def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  
  def mr = gitlabFindMR()
  if (mr) {
    def jiraIssueKeys = jiraKeysFromTitle(mr.title)
    List issues = []
    jiraIssueKeys.each { key ->
      try {
        def issue = getJiraIssueInfo(issueId: key)
        if (issue) {
          issues.add(issue)
        }
      } catch (Exception e) {
        echo "Failed to get Jira issue info for Key=${key}: ${e?.message ?: 'unknown error'}"
      }
    }
    return issues
  }
  return []
}
