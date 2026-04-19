

def findInValidationTransition(String issueKey, credentialsId = 'jira-username-password', debug = false) {
  try {
    def transitionUrl = "https://jira.example.com/rest/api/2/issue/${issueKey}/transitions?expand=transitions.fields"
    def issueResponse = httpRequest(
      url: transitionUrl,
      httpMode: 'GET',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: credentialsId,
      consoleLogResponseBody: debug,
      quiet: !debug)
    def transitions = readJSON(text: issueResponse.getContent()).transitions
    return transitions.find { it.to.name == 'In Validation' }
  } catch (Exception e) {
    return null;
  }
}

/**
 * Transition issue to validation and assign to Reporter
 *
 * @param issueKey Jira issue key
 * @param credentialsId Jira credentials ID
 * @return New assignee display name
 */
String call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def DEBUG = arg.DEBUG ?: false

  def issue = null
  try {
    issue = arg.issueKey
      ? getJiraIssueInfo(
          issueId: arg.issueKey,
          credentialsId: arg.credentialsId ?: null
        )
      : guessJiraIssueInfo(credentialsId: arg.credentialsId ?: null);
  } catch (Exception e) {
    throw new Exception("jiraTransitionIssueToValidation(): Failed to get Jira issue info: ${e?.message ?: 'unknown error'}")
  }
  if(!issue) {
    throw new Exception("jiraTransitionIssueToValidation(): Must provide issueKey or call within a MR context")
  }

  if(DEBUG) echo "jiraTransitionIssueToValidation(): Issue key: ${issue.key}"

  def newAssigneeName = null;
  def newAssigneeDisplayName = null;
  // Get reporter name from issue if 
  if (issue.fields.reporter
    && issue.fields.reporter.toString() != 'null'
    && issue.fields.reporter.name
    && issue.fields.reporter.name.toString() != 'null') {
    newAssigneeName = issue.fields.reporter.name
    newAssigneeDisplayName = issue.fields.reporter.displayName
  }
  if(DEBUG) echo "jiraTransitionIssueToValidation(): New assignee name: ${newAssigneeName}, display name: ${newAssigneeDisplayName}"


  def request=[
    "transition": [:],
    "fields": [:]
  ]

  // Check for current status.
  // - If the issue is already in validation, skip the transition.
  // - If the issue is not in validation, try to find a valid transition to validation.
  // - If no transition is found, skip the transition and only assign the issue
  def inValidationTransition = null;
  if (issue.fields.status.name == 'In Validation') {
    if (DEBUG) echo "jiraTransitionIssueToValidation(): Issue is already in validation"
    return newAssigneeDisplayName
  }

  inValidationTransition = findInValidationTransition(issue.key, arg.credentialsId ?: 'jira-username-password', DEBUG)
  if(!inValidationTransition) {
    throw new Exception("jiraTransitionIssueToValidation(): The issue cannot be transitioned to Validation (maybe it's not in the In Review status?)")
  }

  if (inValidationTransition) {
    request.transition = [
      "id": inValidationTransition.id,
      "to": [
        "name": inValidationTransition.to.name
      ]
    ]

    // For each required transition field, add it to the request with the first allowed value
    // Skip assignee as it's added separately
    for(entry in inValidationTransition.fields) {
      def fieldKey = entry.key
      def field = entry.value
      if(field.required && fieldKey != 'assignee' && field.allowedValues && field.allowedValues.size() > 0) {
        // Use the field key (e.g., "resolution") and the first allowed value's name
        def firstAllowedValue = field.allowedValues[0]
        if(firstAllowedValue.name) {
          request.fields[fieldKey] = [
            "name": firstAllowedValue.name
          ]
        }
      }
    }
  }

  if (newAssigneeName) {
    request.fields.assignee = [
      "name": newAssigneeName
    ]
  }

  if(DEBUG) echo "jiraTransitionIssueToValidation(): Request: ${request}"

  // Send a POST request to send the issue to validation
  def issueUrl = "https://jira.example.com/rest/api/2/issue/${issue.key}/transitions"
  try {
    httpRequest(
      url: issueUrl,
      httpMode: 'POST',
      requestBody: groovy.json.JsonOutput.toJson(request),
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: arg.credentialsId ?: 'jira-username-password',
      quiet: !DEBUG,
      consoleLogResponseBody: DEBUG,
    )
    return newAssigneeDisplayName

  } catch (Exception e) {
    throw new Exception("jiraTransitionIssueToValidation(): Failed to transition issue to validation: ${e?.message ?: 'unknown error'}")
  }
}