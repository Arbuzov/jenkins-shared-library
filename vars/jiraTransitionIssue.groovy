import groovy.json.JsonOutput
import groovy.transform.Field

// Fallback for exceptions that carry no message
@Field
String UNKNOWN_ERROR = 'unknown error'

// Returns null only when no transition to targetStatus exists; request failures propagate
def findTransitionTo(String issueKey, String targetStatus, credentialsId, debug = false) {
  def issueResponse
  try {
    issueResponse = httpRequest(
      url: "https://jira.spacebridge.com/rest/api/2/issue/${issueKey}/transitions?expand=transitions.fields",
      httpMode: 'GET',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: credentialsId,
      consoleLogResponseBody: debug,
      quiet: !debug)
  } catch (Exception e) {
    throw new Exception("jiraTransitionIssue(): Failed to list transitions of ${issueKey}: ${e?.message ?: UNKNOWN_ERROR}")
  }
  def transitions = readJSON(text: issueResponse.getContent()).transitions
  return transitions.find { it.to.name.equalsIgnoreCase(targetStatus) }
}

/**
 * Transition a Jira issue to a target status (by status name) and optionally change
 * the assignee. Generic sibling of jiraTransitionIssueToValidation.
 *
 * The transition is idempotent: an issue already in the target status is not
 * transitioned again (the assignee change, when requested, is still applied).
 * The assignee change is performed with a separate issue update, so it works even
 * when the transition screen has no assignee field. When the screen does have the
 * assignee field, a new assignee is also sent with the transition itself, so screens
 * that require an assignee are satisfied even on unassigned issues.
 *
 * @param arg.targetStatus Target status name, e.g. 'To Do' (required)
 * @param arg.issueKey Jira issue key (default: resolved from the MR title via guessJiraIssueInfo)
 * @param arg.unassign Remove the assignee (default: false)
 * @param arg.assignee Username to assign the issue to (ignored when unassign is true)
 * @param arg.credentialsId Jira credentials ID (default: 'jira-username-password')
 * @param arg.DEBUG Log requests and responses (default: false)
 * @return Map [issueKey, transitioned, previousAssigneeName, previousAssigneeDisplayName]
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def DEBUG = arg.DEBUG ?: false
  def credentialsId = arg.credentialsId ?: 'jira-username-password'

  if (!arg.targetStatus) {
    throw new Exception("jiraTransitionIssue(): 'targetStatus' is required")
  }

  def issue = null
  try {
    issue = arg.issueKey
      ? getJiraIssueInfo(
          issueId: arg.issueKey,
          credentialsId: arg.credentialsId ?: null
        )
      : guessJiraIssueInfo(credentialsId: arg.credentialsId ?: null)
  } catch (Exception e) {
    throw new Exception("jiraTransitionIssue(): Failed to get Jira issue info: ${e?.message ?: UNKNOWN_ERROR}")
  }
  if (!issue) {
    throw new Exception("jiraTransitionIssue(): Must provide issueKey or call within a MR context")
  }

  def result = [
    issueKey: issue.key,
    transitioned: false,
    previousAssigneeName: null,
    previousAssigneeDisplayName: null,
  ]
  if (issue.fields.assignee
    && issue.fields.assignee.toString() != 'null'
    && issue.fields.assignee.name
    && issue.fields.assignee.name.toString() != 'null') {
    result.previousAssigneeName = issue.fields.assignee.name
    result.previousAssigneeDisplayName = issue.fields.assignee.displayName
  }

  if (DEBUG) {
    echo "jiraTransitionIssue(): ${issue.key} '${issue.fields.status.name}' -> '${arg.targetStatus}', previous assignee: ${result.previousAssigneeName}"
  }

  // Transition, unless the issue is already in the target status
  if (!issue.fields.status.name.equalsIgnoreCase(arg.targetStatus)) {
    def transition = findTransitionTo(issue.key, arg.targetStatus, credentialsId, DEBUG)
    if (!transition) {
      throw new Exception("jiraTransitionIssue(): No transition to '${arg.targetStatus}' is available from status '${issue.fields.status.name}'")
    }

    def request = [
      "transition": [
        "id": transition.id,
        "to": [
          "name": transition.to.name
        ]
      ],
      "fields": [:]
    ]

    // For each required transition field, add it to the request with the first allowed value.
    // Skip assignee as it's handled separately below.
    for (entry in transition.fields) {
      def fieldKey = entry.key
      def field = entry.value
      if (field.required && fieldKey != 'assignee' && field.allowedValues && field.allowedValues.size() > 0) {
        def firstAllowedValue = field.allowedValues[0]
        if (firstAllowedValue.name) {
          request.fields[fieldKey] = [
            "name": firstAllowedValue.name
          ]
        }
      }
    }
    // A screen that requires the assignee rejects a request without one — when a new
    // assignee is given, send it with the transition too (the update below still has
    // the final word). For unassign, keep the field out: the current assignee satisfies
    // the screen and the update clears it afterwards.
    if (arg.assignee && !arg.unassign && transition.fields?.assignee) {
      request.fields.assignee = ["name": arg.assignee]
    }
    if (request.fields.isEmpty()) {
      request.remove('fields')
    }

    if (DEBUG) {
      echo "jiraTransitionIssue(): Transition request: ${request}"
    }

    try {
      httpRequest(
        url: "https://jira.spacebridge.com/rest/api/2/issue/${issue.key}/transitions",
        httpMode: 'POST',
        requestBody: JsonOutput.toJson(request),
        contentType: 'APPLICATION_JSON',
        acceptType: 'APPLICATION_JSON',
        authentication: credentialsId,
        quiet: !DEBUG,
        consoleLogResponseBody: DEBUG,
      )
      result.transitioned = true
    } catch (Exception e) {
      throw new Exception("jiraTransitionIssue(): Failed to transition ${issue.key} to '${arg.targetStatus}': ${e?.message ?: UNKNOWN_ERROR}")
    }
  } else if (DEBUG) {
    echo "jiraTransitionIssue(): Issue is already in '${arg.targetStatus}'"
  }

  // Assignee change as a separate issue update: works regardless of the transition screen
  if (arg.unassign || arg.assignee) {
    def update = [
      "fields": [
        "assignee": arg.unassign ? null : ["name": arg.assignee]
      ]
    ]
    if (DEBUG) {
      echo "jiraTransitionIssue(): Assignee update: ${update}"
    }
    try {
      httpRequest(
        url: "https://jira.spacebridge.com/rest/api/2/issue/${issue.key}",
        httpMode: 'PUT',
        requestBody: JsonOutput.toJson(update),
        contentType: 'APPLICATION_JSON',
        acceptType: 'APPLICATION_JSON',
        authentication: credentialsId,
        quiet: !DEBUG,
        consoleLogResponseBody: DEBUG,
      )
    } catch (Exception e) {
      throw new Exception("jiraTransitionIssue(): Failed to update assignee of ${issue.key}: ${e?.message ?: UNKNOWN_ERROR}")
    }
  }

  return result
}
