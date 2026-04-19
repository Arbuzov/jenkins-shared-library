import hudson.model.*
import hudson.util.LogTaskListener

@NonCPS
def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def builds = [ ]
  jenkins.model.Jenkins.instance.getAllItems(jenkins.branch.OrganizationFolder.class).each { folder->
    folder.getItems().each {project ->
      project.getItems().each { branch ->
        branch.getBuilds().each{ build->   
          if (build.getAction(hudson.plugins.promoted_builds_simple.PromoteAction.class)?.getLevel()) {
              builds.add(build)
          }
        }
      }
    }
  }
  return builds
}