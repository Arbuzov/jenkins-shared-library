# Builds and view description for Jenkins

## Project structure

  * [firmware](firmware) - builds and views for IDU 7000
  * [rt-router](rt-router) - builds and views for the router firmware
  * [vars](vars) - shared librarys
    * [arrayToString](vars/arrayToString.groovy)
    * [buildInfoPublisher](vars/buildInfoPublisher.groovy)
    * [copyFromArtifactory](vars/copyFromArtifactory.groovy)
    * [getLatestApprover](vars/getLatestApprover.groovy)
    * [getOrigin](vars/getOrigin.groovy)
    * [getProjectRepos](vars/getProjectRepos.groovy)
    * [getRequestor](vars/getRequestor.groovy)
    * [getStackTrace](vars/isMergeRequest.groovy)
    * [isMergeRequest](vars/isMergeRequest.groovy)
    * [isOrigin](vars/isOrigin.groovy)
    * [notifyRyver](vars/notifyRyver.groovy)
    * [publishArtifactory](vars/publishArtifactory.groovy)
    * [publishChangeLog](vars/publishChangeLog.groovy)
    * [publishSummary](vars/publishSummary.groovy)
    * [sayHello](vars/sayHello.groovy)
    * [tagToGitlab](vars/tagToGitlab.groovy)


## Required Jenkins plugins

* [Git plugin](https://wiki.jenkins.io/display/JENKINS/Git+Plugin)
* [Job DSL plugin](https://github.com/jenkinsci/job-dsl-plugin)