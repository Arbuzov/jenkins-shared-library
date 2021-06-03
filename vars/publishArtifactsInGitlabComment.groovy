import groovy.json.JsonSlurper;

def call(String secretId = 'gitlab-text-secret') {
  if (!env.gitlabTargetRepoSshUrl || !env.gitlabMergeRequestIid) {
    return
  }
  def artifactUrl = env.BUILD_URL + "artifact/"
  ArrayList artifacts = []
  currentBuild.rawBuild.getArtifacts().each {
      artifacts.add("${artifactUrl}${it.getFileName()}")
  }
  message = "### This is the artifacts from this merge-request\n\n"
  artifacts.each {
    message +=(it+"\n")
  }
  message +="\n### Note the links will be reachable untill the MR is not merged\n"
  
  withCredentials([string(credentialsId: secretId, variable: 'gitLabAPISecret')]) {
    noteToMrGitlab(env.gitlabTargetRepoSshUrl, env.gitlabMergeRequestIid, message)
  }
}