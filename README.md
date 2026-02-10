# Jenkins shared library for the Spacebridge DevOps activities

[About the jenkins shared libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

## Project structure

  * [email-templates](email-templates) - custom email templates keep here
  * [vars](vars) - shared functions
    * [arrayToHrefList](vars/arrayToHrefList.groovy) - format a list of URLs into an HTML list of anchors ([docs](vars/arrayToHrefList.txt))
    * [arrayToString](vars/arrayToString.groovy) - join a list of strings into a single string with optional row/list wrappers ([docs](vars/arrayToString.txt))
    * [buildInfoPublisher](vars/buildInfoPublisher.groovy) - publish additional data into the build (brief and full build-info files) and publish them via HTML report
    * [addBageToBuild](vars/addBageToBuild.groovy)
    * [addJiraIssueLink](vars/addJiraIssueLink.groovy)
    * [addLinkToBuild](vars/addLinkToBuild.groovy)
    * [assignJiraFixVersion](vars/assignJiraFixVersion.groovy) ([docs](vars/assignJiraFixVersion.txt))
    * [attachFilesToConfluencePage](vars/attachFilesToConfluencePage.groovy) - attach one or more files to a Confluence page (resolves page ID by title when needed)
    * [changeDefinition](vars/changeDefinition.groovy)
    * [copyFromArtifactory](vars/copyFromArtifactory.groovy)
    * [createConfluencePage](vars/createConfluencePage.groovy)
    * [createConfluencePageChain](vars/createConfluencePageChain.groovy) - create a chain of nested or flat Confluence pages ([docs](vars/createConfluencePageChain.txt))
    * [createNewJiraVersion](vars/createNewJiraVersion.groovy) ([docs](vars/createNewJiraVersion.txt))
    * [getConfluencePageId](vars/getConfluencePageId.groovy) - extract pageId parameter from Confluence URL ([docs](vars/getConfluencePageId.txt))
    * [doCleanGitWorkTree](vars/doCleanGitWorkTree.groovy)
    * [doCleanWorkspaceAfterStage](vars/doCleanWorkspaceAfterStage.groovy)
    * [doCleanWorkspaceBeforeCheckout](vars/doCleanWorkspaceBeforeCheckout.groovy)
    * [doCleanWorkspaceBeforeStage](vars/doCleanWorkspaceBeforeStage.groovy)
    * [downloadList](vars/downloadList.groovy)
    * [downloadConfluenceAttachments](vars/downloadConfluenceAttachments.groovy) - download all or selected Confluence attachments to a local directory ([docs](vars/downloadConfluenceAttachments.txt))
    * [dslMethodExists](vars/dslMethodExists.groovy)
    * [findSBCVersion](vars/findSBCVersion.groovy)
    * [getArtifactsURLsByBuild](vars/getArtifactsURLsByBuild.groovy)
    * [getArtifactsURLsByTag](vars/getArtifactsURLsByTag.groovy)
    * [getBuildCause](vars/getBuildCause.groovy) - return a concise list of causes that triggered the build (class names)
    * [getChangeRequestBuildNumber](vars/getChangeRequestBuildNumber.groovy)
    * [getExternalPort](vars/getExternalPort.groovy)
    * [getFullSnapshotVersion](vars/getFullSnapshotVersion.groovy)
    * [getGitBuildTriggerCommitID](vars/getGitBuildTriggerCommitID.groovy)
    * [getGitCommitInfoStr](vars/getGitCommitInfoStr.groovy)
    * [getIDUDockerImageInfo](vars/getIDUDockerImageInfo.groovy)
    * [IDUDockerImageInfoToString](vars/IDUDockerImageInfoToString.groovy)
    * [getIDURepoPath](vars/getIDURepoPath.groovy)
    * [getIDURepoTopPath](vars/getIDURepoTopPath.groovy)
    * [getIDURepoVersionPath](vars/getIDURepoVersionPath.groovy)
    * [getIDUversions](vars/getIDUversions.groovy) - fetch SIDU `.sit` artifacts from Nexus as `version -> path` map ([docs](vars/getIDUversions.txt))
    * [getJenkinsBuildChanges](vars/getJenkinsBuildChanges.groovy) - generate an HTML changelog from a Jenkins build's changeSets (used for release notes)
    * [getJenkinsCommentSignatureStr](vars/getJenkinsCommentSignatureStr.groovy)
    * [getJiraIssueInfo](vars/getJiraIssueInfo.groovy)
    * [getLastTag](vars/getLastTag.groovy)
    * [getLatestApprover](vars/getLatestApprover.groovy)
    * [getNPMPackageName](vars/getNPMPackageName.groovy)
    * [getNPMPackageVersion](vars/getNPMPackageVersion.groovy)
    * [getNetboxDevices](vars/getNetboxDevices.groovy) - query NetBox DCIM devices API and return a list of results (supports role/tenant filters)
    * [getNetboxRacks](vars/getNetboxRacks.groovy) - query NetBox racks API and return a list of results
    * [getNetboxTenants](vars/getNetboxTenants.groovy) - query NetBox tenancy API and return a list of results
    * [getOrigin](vars/getOrigin.groovy)
    * [getProjectRepos](vars/getProjectRepos.groovy)
    * [getPromotedPipelineBuilds](vars/getPromotedPipelineBuilds.groovy)
    * [getRelatedBranch](vars/getRelatedBranch.groovy)
    * [getRequestor](vars/getRequestor.groovy)
    * [getStackTrace](vars/getStackTrace.groovy)
    * [inflateTemplate](vars/inflateTemplate.groovy)
    * [isOrganizationProject](vars/isOrganizationProject.groovy)
    * [isOrigin](vars/isOrigin.groovy)
    * [iterateBuilds](vars/iterateBuilds.groovy)
    * [gitlabSetMrApproval](vars/gitlabSetMrApproval.groovy) - approve/unapprove merge requests in GitLab ([docs](vars/gitlabSetMrApproval.txt))
    * [labelConfluencePage](vars/labelConfluencePage.groovy)
    * [launchSonarQube](vars/launchSonarQube.groovy) - launch SonarQube analysis ([docs](vars/launchSonarQube.txt))
    * [loadSharedLibraryFile](vars/loadSharedLibraryFile.groovy) - write a library resource to disk and set permissions (helper to deploy shared scripts)
    * [makeYMD](vars/makeYMD.groovy)
    * [mergeRequestsChangeLog](vars/mergeRequestsChangeLog.groovy)
    * [noteToMrGitlab](vars/noteToMrGitlab.groovy)
    * [notifyRyver](vars/notifyRyver.groovy)
    * [notifyTeams](vars/notifyTeams.groovy)
    * [publishArtifactory](vars/publishArtifactory.groovy) - upload artifacts to Artifactory and return public URLs; writes a signature.sha with checksums
    * [publishArtifactsInGitlabComment](vars/publishArtifactsInGitlabComment.groovy)
    * [publishChangeLog](vars/publishChangeLog.groovy)
    * [publishNexus](vars/publishNexus.groovy)
    * [publishSummary](vars/publishSummary.groovy) - create a Jenkins build summary entry (uses manager.createSummary)
    * [readJUnit](vars/readJUnit.groovy) - parse JUnit XML into a structured map (suites, cases, failures) and return overall status
    * [readKeyValue](vars/readKeyValue.groovy)
    * [reportUsage](vars/reportUsage.groovy) ([docs](vars/reportUsage.txt))
    * [requireCleanGitWorkTree](vars/requireCleanGitWorkTree.groovy)
    * [requireNoGeneratedGlobalFiles](vars/requireNoGeneratedGlobalFiles.groovy)
    * [runAngularContainerBuild](vars/runAngularContainerBuild.groovy)
    * [runAngularDebBuild](vars/runAngularDebBuild.groovy)
    * [runApplicationMetaPluginBuild](vars/runApplicationMetaPluginBuild.groovy)
    * [runApplicationPluginBuild](vars/runApplicationPluginBuild.groovy)
    * [runDockerBuild](vars/runDockerBuild.groovy)
    * [runIDUDockerBuild](vars/runIDUDockerBuild.groovy)
    * [runLoopBackBuild](vars/runLoopBackBuild.groovy)
    * [runPIPtoDebBuild](vars/runPIPtoDeb.groovy)
    * [saveEnvVarsForChangeRequest](vars/saveEnvVarsForChangeRequest.groovy)
    * [sayHello](vars/sayHello.groovy)
    * [tagToGitlab](vars/tagToGitlab.groovy) - create/delete tags in GitLab via the API using a stored token


## Required Jenkins plugins

The shared library makes use of several Jenkins pipeline steps and integrations; install these plugins on your Jenkins controller if you plan to use the corresponding functions from `vars/`:

* [Git plugin](https://plugins.jenkins.io/git/) — repository checkout and SCM triggers
* [Job DSL plugin](https://plugins.jenkins.io/job-dsl/) — used by some job-generation helpers
* [Jira plugin](https://plugins.jenkins.io/jira/) — Jira integration and version builders (used by createNewJiraVersion / assignJiraFixVersion)
* [HTTP Request Plugin](https://plugins.jenkins.io/http_request/) — `httpRequest` is used widely for API calls (GitLab, Confluence, Jira, NetBox, etc.)
* [Pipeline Utility Steps plugin](https://plugins.jenkins.io/pipeline-utility-steps/) — utilities used by several helpers (file/zip helpers, findFiles, readYaml, etc.)
* [HTML Publisher Plugin](https://plugins.jenkins.io/htmlpublisher/) — `publishHTML` is used to publish generated site reports
* [JFrog Artifactory Plugin](https://plugins.jenkins.io/artifactory/) — `Artifactory.server(...)` and build-info publishing
* [Docker Pipeline plugin](https://plugins.jenkins.io/docker-workflow/) — `docker.withRegistry`, `docker.image` usage
* [Pipeline Maven Integration (withMaven)](https://plugins.jenkins.io/pipeline-maven/) — used in the Jenkinsfile for Maven lifecycle integration
* [Credentials Binding Plugin](https://plugins.jenkins.io/credentials-binding/) — `withCredentials` usage across many helpers
* [JUnit Plugin](https://plugins.jenkins.io/junit/) — `junit` step is used to publish test results (looks for JUnit XML under target/surefire-reports)
* [Nexus Artifact Uploader plugin](https://plugins.jenkins.io/nexus-jenkins-plugin/) — `nexusArtifactUploader` is used in some publishing helpers
* [Pipeline: Basic Steps](https://plugins.jenkins.io/workflow-basic-steps/) — `archiveArtifacts`, `sh`, and other basic steps used in pipelines

Notes:
- Some library helpers call `manager.createSummary(...)` to add build summaries. That API is provided by legacy build-step contexts (and some post-build plugins). If you use those helpers inside non-classic contexts, ensure the plugin providing `createSummary` (or an equivalent summary mechanism) is available in your environment.
- Install only the plugins you actually need for your pipelines; many helpers are optional and call external services (Artifactory, Nexus, Confluence, GitLab, Jira).
