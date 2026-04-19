# Jenkins Shared Library

A curated set of Jenkins pipeline steps and helpers developed over years of running
CI/CD for product teams. The library is designed to keep Jenkinsfiles declarative and thin
by pushing the integration logic — Confluence, Jira, GitLab, NetBox, Artifactory, Nexus,
SonarQube, InfluxDB, MS Teams — into reusable `vars/` steps written in pure Groovy
on top of `httpRequest` and the standard pipeline API.

> See [Using libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/) for
> how to plug a shared library into a Jenkins controller.

## Design principles

- **Named arguments everywhere.** Every public step accepts `Map arg = [:]` with
  `UPPER_SNAKE_CASE` keys, explicit defaults and fail-fast validation via `error(...)`.
- **No shell wrappers.** Integrations are built on `httpRequest`/`readJSON` instead of
  piping `curl` through `sh`. Portable, testable, and the pipeline log stays clean.
- **No hardcoded secrets.** Credentials flow through `withCredentials` and `credentialsId`
  parameters — defaults are only for examples.
- **Structured returns.** Steps return `Map`/`List` rather than stringly-typed blobs so
  callers can compose them.
- **Documentation next to code.** Each non-trivial step has a sibling `vars/<name>.txt`
  with parameters, defaults, return shape and failure modes.

## Pipeline steps

### Confluence
`httpRequest`-based REST client for Confluence Server/DC — read, create, update, attach,
label, comment, walk page trees.

- [`addConfluenceComment`](vars/addConfluenceComment.groovy) — post a comment on a page ([docs](vars/addConfluenceComment.txt))
- [`attachFilesToConfluencePage`](vars/attachFilesToConfluencePage.groovy) — upload one or many files, resolve page by title when needed ([docs](vars/attachFilesToConfluencePage.txt))
- [`createConfluencePage`](vars/createConfluencePage.groovy) — create or update a page, idempotent by title ([docs](vars/createConfluencePage.txt))
- [`createConfluencePageChain`](vars/createConfluencePageChain.groovy) — build a nested or flat chain of child pages from a spec ([docs](vars/createConfluencePageChain.txt))
- [`downloadConfluenceAttachments`](vars/downloadConfluenceAttachments.groovy) — download all or selected attachments to a local directory ([docs](vars/downloadConfluenceAttachments.txt))
- [`getConfluenceComment`](vars/getConfluenceComment.groovy) — fetch a comment by id ([docs](vars/getConfluenceComment.txt))
- [`getConfluencePageChildren`](vars/getConfluencePageChildren.groovy) — enumerate children of a page ([docs](vars/getConfluencePageChildren.txt))
- [`getConfluencePageContent`](vars/getConfluencePageContent.groovy) — fetch storage-format content ([docs](vars/getConfluencePageContent.txt))
- [`getConfluencePageId`](vars/getConfluencePageId.groovy) — resolve a page id from title or URL ([docs](vars/getConfluencePageId.txt))
- [`labelConfluencePage`](vars/labelConfluencePage.groovy) — add labels
- [`updateConfluencePage`](vars/updateConfluencePage.groovy) — update title/content/version of an existing page ([docs](vars/updateConfluencePage.txt))

### GitLab
Merge-request and commit-status integration that makes Jenkins a first-class CI for
GitLab without the external-status plugin dance.

- [`gitlabSetCommitStatus`](vars/gitlabSetCommitStatus.groovy) — report pending/success/failed checks against a commit ([docs](vars/gitlabSetCommitStatus.txt))
- [`gitlabFindMR`](vars/gitlabFindMR.groovy) — resolve the MR that triggered the build, even for detached runs ([docs](vars/gitlabFindMR.txt))
- [`gitlabSetMrApproval`](vars/gitlabSetMrApproval.groovy) — approve/unapprove an MR from the pipeline ([docs](vars/gitlabSetMrApproval.txt))
- [`isDraftMr`](vars/isDraftMr.groovy) — detect draft/WIP MRs for conditional stages ([docs](vars/isDraftMr.txt))
- [`noteToMrGitlab`](vars/noteToMrGitlab.groovy) — post a comment on an MR
- [`publishArtifactsInGitlabComment`](vars/publishArtifactsInGitlabComment.groovy) — publish build artifacts as an MR comment
- [`mergeRequestsChangeLog`](vars/mergeRequestsChangeLog.groovy) — assemble a changelog from merged MRs
- [`tagToGitlab`](vars/tagToGitlab.groovy) — create/delete tags via the GitLab API

### Jira
- [`addJiraIssueLink`](vars/addJiraIssueLink.groovy) — attach a remote link (e.g. build URL) to an issue
- [`assignJiraFixVersion`](vars/assignJiraFixVersion.groovy) — assign a fix version to an issue ([docs](vars/assignJiraFixVersion.txt))
- [`createNewJiraVersion`](vars/createNewJiraVersion.groovy) — create a version in a project ([docs](vars/createNewJiraVersion.txt))
- [`getJiraIssueInfo`](vars/getJiraIssueInfo.groovy) — fetch an issue as a map ([docs](vars/getJiraIssueInfo.txt))
- [`guessJiraIssueInfo`](vars/guessJiraIssueInfo.groovy) — extract an issue key from branch/commit/MR context
- [`jiraAddComment`](vars/jiraAddComment.groovy) — post a comment ([docs](vars/jiraAddComment.txt))
- [`jiraAddFixVersion`](vars/jiraAddFixVersion.groovy) — bulk-assign fix versions ([docs](vars/jiraAddFixVersion.txt))
- [`jiraTransitionIssueToValidation`](vars/jiraTransitionIssueToValidation.groovy) — drive workflow transitions via the API ([docs](vars/jiraTransitionIssueToValidation.txt))

### NetBox (DCIM / IPAM)
- [`getNetboxDevices`](vars/getNetboxDevices.groovy) — list devices with role/tenant filters ([docs](vars/getNetboxDevices.txt))
- [`getNetboxRacks`](vars/getNetboxRacks.groovy) — list racks ([docs](vars/getNetboxRacks.txt))
- [`getNetboxTenants`](vars/getNetboxTenants.groovy) — list tenants ([docs](vars/getNetboxTenants.txt))

### Quality and telemetry
- [`launchSonarQube`](vars/launchSonarQube.groovy) — run analysis and poll the Quality Gate with configurable violation/severity thresholds ([docs](vars/launchSonarQube.txt))
- [`sendToInfluxDB`](vars/sendToInfluxDB.groovy) — ship build metrics in line protocol ([docs](vars/sendToInfluxDB.txt))
- [`readJUnit`](vars/readJUnit.groovy) — parse JUnit XML into a structured map for downstream publishing ([docs](vars/readJUnit.txt))
- [`reportUsage`](vars/reportUsage.groovy) — emit per-step usage telemetry (handy for deprecation tracking) ([docs](vars/reportUsage.txt))

### Artifact publishing
- [`publishArtifactory`](vars/publishArtifactory.groovy) — upload to Artifactory and return public URLs; writes a `signature.sha` with checksums
- [`publishNexus`](vars/publishNexus.groovy) — pure `httpRequest` PUT, no `nexusArtifactUploader` required
- [`copyFromArtifactory`](vars/copyFromArtifactory.groovy) — download artifact sets via AQL
- [`getArtifactsURLsByBuild`](vars/getArtifactsURLsByBuild.groovy) / [`getArtifactsURLsByTag`](vars/getArtifactsURLsByTag.groovy) — discover artifact URLs
- [`getFullSnapshotVersion`](vars/getFullSnapshotVersion.groovy) — resolve the latest `-SNAPSHOT` asset on Nexus ([docs](vars/getFullSnapshotVersion.txt))

### Notifications
- [`notifyTeams`](vars/notifyTeams.groovy) — post to an MS Teams channel webhook ([docs](vars/notifyTeams.txt))
- [`notifyRyver`](vars/notifyRyver.groovy) — post to a Ryver channel webhook
- [`publishSummary`](vars/publishSummary.groovy) — add a Jenkins build summary entry ([docs](vars/publishSummary.txt))

### Build introspection and plumbing
- [`addBageToBuild`](vars/addBageToBuild.groovy) / [`addLinkToBuild`](vars/addLinkToBuild.groovy) — decorate the build page with badges and links
- [`changeDefinition`](vars/changeDefinition.groovy) — pipeline-to-job parameter reconciliation
- [`dslMethodExists`](vars/dslMethodExists.groovy) — safely feature-detect a pipeline step
- [`getBuildCause`](vars/getBuildCause.groovy) — list the causes that triggered the build as a clean string array
- [`getChangeRequestBuildNumber`](vars/getChangeRequestBuildNumber.groovy) — resolve the upstream CR build number
- [`getJenkinsBuildChanges`](vars/getJenkinsBuildChanges.groovy) — render an HTML changelog from `changeSets` ([docs](vars/getJenkinsBuildChanges.txt))
- [`getJenkinsCommentSignatureStr`](vars/getJenkinsCommentSignatureStr.groovy) — build a stable "posted by Jenkins" signature
- [`getLatestApprover`](vars/getLatestApprover.groovy) — resolve the last approver of an upstream job
- [`getPromotedPipelineBuilds`](vars/getPromotedPipelineBuilds.groovy) — iterate builds promoted by the promoted-builds plugin
- [`getRelatedBranch`](vars/getRelatedBranch.groovy) — pick the target/related branch for a CR
- [`getRequestor`](vars/getRequestor.groovy) — identify who triggered the build
- [`getStackTrace`](vars/getStackTrace.groovy) — render an exception stack trace as a string
- [`getUpstreamBuild`](vars/getUpstreamBuild.groovy) — walk up the upstream build chain ([docs](vars/getUpstreamBuild.txt))
- [`iterateBuilds`](vars/iterateBuilds.groovy) — generic walker over a job's build history
- [`saveEnvVarsForChangeRequest`](vars/saveEnvVarsForChangeRequest.groovy) — stash env for cross-stage replay
- [`isMergeRequest`](vars/isMergeRequest.groovy) / [`isOrganizationProject`](vars/isOrganizationProject.groovy) / [`isOrigin`](vars/isOrigin.groovy) — predicates for conditional stages
- [`getOrigin`](vars/getOrigin.groovy) / [`getProjectRepos`](vars/getProjectRepos.groovy) / [`getGitBuildTriggerCommitID`](vars/getGitBuildTriggerCommitID.groovy) / [`getGitCommitInfoStr`](vars/getGitCommitInfoStr.groovy) — SCM helpers

### Workspace hygiene
- [`doCleanWorkspaceAfterStage`](vars/doCleanWorkspaceAfterStage.groovy) — `cleanWs` wrapper that keeps artifacts needed by downstream stages
- [`doCleanWorkspaceBeforeStage`](vars/doCleanWorkspaceBeforeStage.groovy)
- [`doCleanWorkspaceBeforeCheckout`](vars/doCleanWorkspaceBeforeCheckout.groovy)

### Utilities
- [`arrayToHrefList`](vars/arrayToHrefList.groovy) — render a list of URLs as an HTML `<ul>` ([docs](vars/arrayToHrefList.txt))
- [`arrayToString`](vars/arrayToString.groovy) — join a list with optional row/list wrappers ([docs](vars/arrayToString.txt))
- [`inflateTemplate`](vars/inflateTemplate.groovy) — `SimpleTemplateEngine` helper with CDATA-safe stringify mode ([docs](vars/inflateTemplate.txt))
- [`downloadList`](vars/downloadList.groovy) — download a list of URLs
- [`getExternalPort`](vars/getExternalPort.groovy) — resolve the mapped port of a container exposure
- [`makeYMD`](vars/makeYMD.groovy) — build a YMD timestamp
- [`readKeyValue`](vars/readKeyValue.groovy) — parse `key=value` text ([docs](vars/readKeyValue.txt))
- [`sayHello`](vars/sayHello.groovy) — smoke-test step

## Required Jenkins plugins

Install the plugins that correspond to the steps you actually use:

- [Git](https://plugins.jenkins.io/git/) — SCM
- [HTTP Request](https://plugins.jenkins.io/http_request/) — every API integration uses `httpRequest`
- [Pipeline Utility Steps](https://plugins.jenkins.io/pipeline-utility-steps/) — `readJSON`, `findFiles`, `readYaml`, zip helpers
- [HTML Publisher](https://plugins.jenkins.io/htmlpublisher/) — `publishHTML`
- [JFrog Artifactory](https://plugins.jenkins.io/artifactory/) — `Artifactory.server(...)` and build-info
- [Docker Pipeline](https://plugins.jenkins.io/docker-workflow/) — `docker.withRegistry`, `docker.image`
- [Pipeline Maven Integration](https://plugins.jenkins.io/pipeline-maven/) — `withMaven`
- [Credentials Binding](https://plugins.jenkins.io/credentials-binding/) — `withCredentials`
- [JUnit](https://plugins.jenkins.io/junit/) — `junit` step
- [Nexus Artifact Uploader](https://plugins.jenkins.io/nexus-jenkins-plugin/) — only if you prefer it over `publishNexus`
- [Job DSL](https://plugins.jenkins.io/job-dsl/) — used by some job-generation helpers
- [Jira](https://plugins.jenkins.io/jira/) — optional; most Jira steps in this library work via `httpRequest`

Some steps call `manager.createSummary(...)` — available in legacy build-step contexts and
some post-build plugins. Drop in an equivalent summary mechanism if you run in a context
that doesn't expose it.

## Template resources

A few Confluence-oriented steps (`createConfluencePageChain`, `getJenkinsBuildChanges`,
`addBageToBuild`) load default templates via `libraryResource('com/example/confluence/...')`.
This library ships without those templates on purpose — provide your own under
`resources/com/example/confluence/` or pass `CONTENT` explicitly when calling the step.

## Using the library

```groovy
@Library('jenkins-shared-library') _

pipeline {
  agent any
  stages {
    stage('MR checks') {
      when { expression { return isMergeRequest() && !isDraftMr() } }
      steps {
        gitlabSetCommitStatus(state: 'running', name: 'jenkins')
      }
    }
    stage('Publish release notes') {
      steps {
        script {
          def changes = getJenkinsBuildChanges()
          createConfluencePage(
            SPACE:          'ENG',
            TITLE:          "Release ${env.BUILD_TAG}",
            CONTENT:        changes,
            CREDENTIALS_ID: 'confluence-token'
          )
        }
      }
    }
  }
  post {
    success { gitlabSetCommitStatus(state: 'success', name: 'jenkins') }
    failure { gitlabSetCommitStatus(state: 'failed',  name: 'jenkins') }
  }
}
```

## Development

```bash
mvn test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for coding, documentation and testing conventions.

## License

MIT.
