import hudson.plugins.jira.JiraSite

/**
 * Assign an existing version as the fix version of every issue of a project that has
 * no fix version yet and sits in the given status.
 *
 * Uses the Jenkins Jira plugin site configuration, not the httpRequest credentials
 * the other jira* steps take.
 *
 * @param arg.version Existing version in the project, e.g. '1.1.1-b456' (required)
 * @param arg.projectKey Jira project key, e.g. 'NMSNG' (required)
 * @param arg.statusName Status the issues have to be in, e.g. 'In Progress' (required)
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (!arg.version) {
    throw new Exception("jiraAssignFixVersionToIssues(): 'version' is required but not provided.")
  }
  if (!arg.projectKey) {
    throw new Exception("jiraAssignFixVersionToIssues(): 'projectKey' is required but not provided.")
  }
  if (!arg.statusName) {
    throw new Exception("jiraAssignFixVersionToIssues(): 'statusName' is required but not provided.")
  }

  // Status names are free text, so they are escaped for the quoted JQL literal.
  // Project keys are not: Jira only allows [A-Z][A-Z0-9_]* there.
  String statusName = arg.statusName.toString().replace('\\', '\\\\').replace("'", "\\'")

  def project = currentBuild.getRawBuild().project
  JiraSite
    .get(project)
    .getSession(project)
    .migrateIssuesToFixVersion(
      arg.projectKey,
      arg.version,
      "project = ${arg.projectKey} and fixVersion is EMPTY and status = '${statusName}'"
    )
}
