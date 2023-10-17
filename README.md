# Jenkins shared library for the Spacebridge DevOps activities

[About the jenkins shared libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

## Project structure

  * [rt-router](rt-router) - builds and views for the router firmware
  * [email-templates](email-templates) - custom email templates keep here
  * [vars](vars) - shared functions
    * [arrayToHrefList](vars/arrayToHrefList.txt)
    * [arrayToString](vars/arrayToString.groovy)
    * [buildInfoPublisher](vars/buildInfoPublisher.groovy)
    * [assignJiraFixVersion](vars/assignJiraFixVersion.txt)
    * [copyFromArtifactory](vars/copyFromArtifactory.groovy)
    * [createNewJiraVersion](vars/createNewJiraVersion.txt)
    * [getLatestApprover](vars/getLatestApprover.groovy)
    * [getOrigin](vars/getOrigin.groovy)
    * [getProjectRepos](vars/getProjectRepos.groovy)
    * [getRequestor](vars/getRequestor.groovy)
    * [getStackTrace](vars/isMergeRequest.groovy)
    * [isMergeRequest](vars/isMergeRequest.groovy)
    * [isOrganizationProject](vars/isOrganizationProject.groovy)
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
* [Jira plugin](https://plugins.jenkins.io/jira/)
* [Http request plugin](https://www.jenkins.io/doc/pipeline/steps/http_request/)
* [Pipeline Utility Steps plugin](https://www.jenkins.io/doc/pipeline/steps/pipeline-utility-steps/)