/**
 * Add fix version to Jira issue
 * Example:
 * jiraAddFixVersion(fixVersion: '1.1.1-b456')
 *
 * @param issueKey Jira issue key (optional, if missing - try to guess the issue from the current MR)
 * @param fixVersion Existing version in the project, such as 1.1.1-b456
 * 
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def fixVersion = arg.fixVersion
  if(!fixVersion) {
    throw new Exception("jiraAddFixVersion: argument 'fixVersion' is required but not provided.")
  }


  def issue = null
  try {
    issue = arg.issueKey
      ? getJiraIssueInfo(
          issueId: arg.issueKey,
          credentialsId: arg.credentialsId ?: null)
      : guessJiraIssueInfo(credentialsId: arg.credentialsId ?: null);
  } catch (Exception e) {
    throw new Exception("jiraAddFixVersion(): Failed to get Jira issue info: ${e?.message ?: 'unknown error'}")
  }
  if(!issue) {
    throw new Exception("jiraAddFixVersion(): Must provide issueKey or call within a MR context")
  }

  // Check if fix version already exists in the issue
  if(issue.fields.fixVersions != null
    && issue.fields.fixVersions.find { it.name == fixVersion }) {
    return true;
  }

  // Send a PUT request to add the fix version to the issue
  def request = [
    "update": [
      "fixVersions": [
        [
          "add": [
            "name": fixVersion
          ]
        ]
      ]
    ]
  ]
  try {
    httpRequest(
      url: "https://jira.example.com/rest/api/2/issue/${issue.key}",
      httpMode: 'PUT',
      requestBody: groovy.json.JsonOutput.toJson(request),
      consoleLogResponseBody: false,
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: arg.credentialsId ?: 'jira-username-password',
    )
    return true

  } catch (Exception e) {
    throw new Exception("jiraAddFixVersion(): Jira API call failed: ${e?.message ?: 'unknown error'}")
  }
}