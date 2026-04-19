import hudson.model.*
import hudson.triggers.*

@NonCPS
def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  for(rootJob in Hudson.instance.items) {
    switch ( rootJob.class ) {
      case hudson.model.FreeStyleProject:
      case org.jenkinsci.plugins.workflow.job.WorkflowJob:
        parseBuilds(rootJob.getBuilds())
        break
      case org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject:
        for(branchJob in rootJob.getItems()) {
          parseBuilds(branchJob.getBuilds())
        }
        break
      case jenkins.branch.OrganizationFolder:
        for(projectJob in rootJob.getItems()) {
          for(childJob in projectJob.getItems()) {
            parseBuilds(childJob.getBuilds())
          }
        }
        break
      default:
        print('Not processed type ' + rootJob.class)
    }
  }
}

@NonCPS
def parseBuilds(builds) {
  for(build in builds) {
    try {
      if (build.getAction(hudson.plugins.promoted_builds_simple.PromoteAction)) {
        if (build.getAction(hudson.plugins.promoted_builds_simple.PromoteAction).getLevelValue()!=0) {
          println(
            "Not null value " + 
            build.getAction(hudson.plugins.promoted_builds_simple.PromoteAction).getLevelValue() +
            "  " + build.getAction(hudson.plugins.promoted_builds_simple.PromoteAction).getLevel() +
            build.getEnvironment()
          )
        }
      }
    } catch (Exception e) {
      println (e)
    }
  }
}