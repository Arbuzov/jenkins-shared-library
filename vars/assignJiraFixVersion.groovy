import hudson.plugins.jira.*;

def call(String jiraVersion, String jiraProjectKey, String statusName) {
  def project = currentBuild.getRawBuild().project
  JiraSite
    .get(project)
    .getSession(project)
    .migrateIssuesToFixVersion(
      jiraProjectKey,
      jiraVersion,
      "project = ${jiraProjectKey} and fixVersion is EMPTY  and status = '${statusName}'"
    )
}