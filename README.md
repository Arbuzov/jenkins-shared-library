# Jenkins shared library for the Spacebridge DevOps activities

[About the Jenkins shared libraries](https://www.jenkins.io/doc/book/pipeline/shared-libraries/)

This library keeps Jenkinsfiles declarative and thin by pushing the integration
logic — Confluence, Jira, GitLab, NetBox, Artifactory, Nexus, SonarQube,
InfluxDB, MS Teams / Ryver — into reusable `vars/` steps built on top of
`httpRequest` and the standard pipeline API, plus a set of Spacebridge
product-specific helpers for SBC, IDU, Hubtest and the Reprepro Debian repo.

## Project structure

- [`vars/`](vars) — pipeline steps (global vars), grouped by purpose below
- [`src/`](src) — reusable Groovy classes that don't depend on Jenkins step context
- [`resources/`](resources) — templates and static assets loaded via `libraryResource(...)`
- [`email-templates/`](email-templates) — Email-ext templates
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — coding, documentation and testing conventions

## Pipeline steps

### Confluence
`httpRequest`-based REST client for Confluence Server/DC: read, create, update,
attach, label, comment, walk page trees.

- [addConfluenceComment](vars/addConfluenceComment.groovy) — post a comment on a page ([docs](vars/addConfluenceComment.txt))
- [attachFilesToConfluencePage](vars/attachFilesToConfluencePage.groovy) — upload one or many files, resolve page by title when needed ([docs](vars/attachFilesToConfluencePage.txt))
- [createConfluencePage](vars/createConfluencePage.groovy) — create a page, idempotent by title ([docs](vars/createConfluencePage.txt))
- [createConfluencePageChain](vars/createConfluencePageChain.groovy) — build a nested or flat chain of child pages from a spec ([docs](vars/createConfluencePageChain.txt))
- [downloadConfluenceAttachments](vars/downloadConfluenceAttachments.groovy) — download all or selected attachments to a local directory ([docs](vars/downloadConfluenceAttachments.txt))
- [getConfluenceComment](vars/getConfluenceComment.groovy) — fetch a comment by id ([docs](vars/getConfluenceComment.txt))
- [getConfluencePageChildren](vars/getConfluencePageChildren.groovy) — enumerate children of a page ([docs](vars/getConfluencePageChildren.txt))
- [getConfluencePageContent](vars/getConfluencePageContent.groovy) — fetch storage-format content ([docs](vars/getConfluencePageContent.txt))
- [getConfluencePageId](vars/getConfluencePageId.groovy) — resolve a page id from title or URL ([docs](vars/getConfluencePageId.txt))
- [labelConfluencePage](vars/labelConfluencePage.groovy) — add labels
- [updateConfluencePage](vars/updateConfluencePage.groovy) — update title/content/version of an existing page ([docs](vars/updateConfluencePage.txt))

### GitLab
Merge-request and commit-status integration so Jenkins can act as a first-class
CI for GitLab projects.

- [gitlabSetCommitStatus](vars/gitlabSetCommitStatus.groovy) — report pending/success/failed checks against a commit ([docs](vars/gitlabSetCommitStatus.txt))
- [gitlabFindMR](vars/gitlabFindMR.groovy) — resolve the MR that triggered the build ([docs](vars/gitlabFindMR.txt))
- [gitlabSetMrApproval](vars/gitlabSetMrApproval.groovy) — approve/unapprove an MR from the pipeline ([docs](vars/gitlabSetMrApproval.txt))
- [isDraftMr](vars/isDraftMr.groovy) — detect draft/WIP MRs for conditional stages ([docs](vars/isDraftMr.txt))
- [noteToMrGitlab](vars/noteToMrGitlab.groovy) — post a comment on an MR
- [publishArtifactsInGitlabComment](vars/publishArtifactsInGitlabComment.groovy) — publish build artifacts as an MR comment
- [mergeRequestsChangeLog](vars/mergeRequestsChangeLog.groovy) — assemble a changelog from merged MRs
- [tagToGitlab](vars/tagToGitlab.groovy) — create/delete tags via the GitLab API
- [gitlabUpdateFile](vars/gitlabUpdateFile.groovy) — update a single repo file via the GitLab API ([docs](vars/gitlabUpdateFile.txt))
- [gitlabGetRepositoryTree](vars/gitlabGetRepositoryTree.groovy) — list the files and directories of a repository via the GitLab API ([docs](vars/gitlabGetRepositoryTree.txt))
- [gitlabGetFile](vars/gitlabGetFile.groovy) — read a repo file via the GitLab API, decoded, with the commit it was read at ([docs](vars/gitlabGetFile.txt))
- [gitlabApiGet](vars/gitlabApiGet.groovy) — read any resource of a project via the GitLab API, for the endpoints no other step covers ([docs](vars/gitlabApiGet.txt))

### Jira
- [jiraAddComment](vars/jiraAddComment.groovy) — post a comment ([docs](vars/jiraAddComment.txt))
- [jiraAddFixVersion](vars/jiraAddFixVersion.groovy) — add a fix version to a single issue ([docs](vars/jiraAddFixVersion.txt))
- [jiraAddRemoteIssueLink](vars/jiraAddRemoteIssueLink.groovy) — attach a remote link (e.g. build URL) to an issue ([docs](vars/jiraAddRemoteIssueLink.txt))
- [jiraCreateVersion](vars/jiraCreateVersion.groovy) — create a version in a project ([docs](vars/jiraCreateVersion.txt))
- [jiraGetIssueComments](vars/jiraGetIssueComments.groovy) — read the whole comment thread of an issue, walking every page ([docs](vars/jiraGetIssueComments.txt))
- [jiraGetIssueInfo](vars/jiraGetIssueInfo.groovy) — fetch an issue as a map, with `withComments: true` for the issue and its comments in one call ([docs](vars/jiraGetIssueInfo.txt))
- [jiraGetRemoteIssueLinks](vars/jiraGetRemoteIssueLinks.groovy) — list the remote links of an issue, e.g. to skip a duplicate ([docs](vars/jiraGetRemoteIssueLinks.txt))
- [jiraGuessIssueInfo](vars/jiraGuessIssueInfo.groovy) — resolve the issue of the current build from the MR title ([docs](vars/jiraGuessIssueInfo.txt))
- [jiraKeyFromTitle](vars/jiraKeyFromTitle.groovy) — extract an issue key from an MR title ([docs](vars/jiraKeyFromTitle.txt))
- [jiraAssignFixVersionToIssues](vars/jiraAssignFixVersionToIssues.groovy) — bulk-assign a fix version to matching issues ([docs](vars/jiraAssignFixVersionToIssues.txt))
- [jiraTransitionIssue](vars/jiraTransitionIssue.groovy) — transition an issue and optionally reassign it ([docs](vars/jiraTransitionIssue.txt))

Deprecated steps, kept as thin wrappers — they log a deprecation warning and are
reported as deprecated in the usage statistics:

- [addJiraIssueLink](vars/addJiraIssueLink.groovy) → `jiraAddRemoteIssueLink` ([docs](vars/addJiraIssueLink.txt))
- [assignJiraFixVersion](vars/assignJiraFixVersion.groovy) → `jiraAssignFixVersionToIssues` ([docs](vars/assignJiraFixVersion.txt))
- [createNewJiraVersion](vars/createNewJiraVersion.groovy) → `jiraCreateVersion` ([docs](vars/createNewJiraVersion.txt))
- [getJiraIssueInfo](vars/getJiraIssueInfo.groovy) → `jiraGetIssueInfo` ([docs](vars/getJiraIssueInfo.txt))
- [guessJiraIssueInfo](vars/guessJiraIssueInfo.groovy) → `jiraGuessIssueInfo` ([docs](vars/guessJiraIssueInfo.txt))
- [jiraTransitionIssueToValidation](vars/jiraTransitionIssueToValidation.groovy) → `jiraTransitionIssue` ([docs](vars/jiraTransitionIssueToValidation.txt))

### NetBox (DCIM / IPAM)
- [getNetboxDevices](vars/getNetboxDevices.groovy) — list devices with role/tenant filters ([docs](vars/getNetboxDevices.txt))
- [getNetboxRacks](vars/getNetboxRacks.groovy) — list racks ([docs](vars/getNetboxRacks.txt))
- [getNetboxTenants](vars/getNetboxTenants.groovy) — list tenants ([docs](vars/getNetboxTenants.txt))

### AI pipelines (Claude Code)
Steps for pipelines that delegate work to the Claude Code CLI. The CLI writes an
NDJSON stream (`--output-format=stream-json`); these turn it into a reviewable
record of what the model did.

- [parseClaudeStream](vars/parseClaudeStream.groovy) — parse the stream-json into events, tool calls and run totals ([docs](vars/parseClaudeStream.txt))
- [publishClaudeTranscript](vars/publishClaudeTranscript.groovy) — render the transcript as HTML and publish it on the build page ([docs](vars/publishClaudeTranscript.txt))

### Quality and telemetry
- [launchSonarQube](vars/launchSonarQube.groovy) — run analysis and poll the Quality Gate with configurable violation/severity thresholds ([docs](vars/launchSonarQube.txt))
- [readJUnit](vars/readJUnit.groovy) — parse JUnit XML into a structured map for downstream publishing ([docs](vars/readJUnit.txt))
- [reportUsage](vars/reportUsage.groovy) — emit per-step usage telemetry (used by most helpers) ([docs](vars/reportUsage.txt))
- [sendToInfluxDB](vars/sendToInfluxDB.groovy) — ship build metrics in line protocol ([docs](vars/sendToInfluxDB.txt))

### Artifact storage (Artifactory / Nexus)
- [publishArtifactory](vars/publishArtifactory.groovy) — upload artifacts to Artifactory and return public URLs; writes a `signature.sha` with checksums
- [copyFromArtifactory](vars/copyFromArtifactory.groovy) — download artifact sets via AQL
- [publishNexus](vars/publishNexus.groovy) — pure `httpRequest` PUT to Nexus
- [getArtifactsURLsByBuild](vars/getArtifactsURLsByBuild.groovy) / [getArtifactsURLsByTag](vars/getArtifactsURLsByTag.groovy) — discover artifact URLs on past builds
- [getFullSnapshotVersion](vars/getFullSnapshotVersion.groovy) — resolve the latest `-SNAPSHOT` asset on Nexus ([docs](vars/getFullSnapshotVersion.txt))
- [getRepReproArtifacts](vars/getRepReproArtifacts.groovy) — list `.deb` artifacts in the internal Reprepro Debian repository
- [nexusSearchAssets](vars/nexusSearchAssets.groovy) — every asset of a Nexus search, all pages ([docs](vars/nexusSearchAssets.txt))
- [dockerGetImageTags](vars/dockerGetImageTags.groovy) — the tags of an image in a Docker registry, all pages ([docs](vars/dockerGetImageTags.txt))

### Notifications and summaries
- [notifyTeams](vars/notifyTeams.groovy) — post to an MS Teams channel webhook ([docs](vars/notifyTeams.txt))
- [notifyRyver](vars/notifyRyver.groovy) — post to a Ryver channel webhook
- [publishSummary](vars/publishSummary.groovy) — add a Jenkins build summary entry ([docs](vars/publishSummary.txt))
- [addBageToBuild](vars/addBageToBuild.groovy) / [addLinkToBuild](vars/addLinkToBuild.groovy) — decorate the build page with badges and links

### Build introspection and plumbing
- [buildInfoPublisher](vars/buildInfoPublisher.groovy) — publish brief and full build-info text reports via `publishHTML`
- [changeDefinition](vars/changeDefinition.groovy) — pipeline-to-job parameter reconciliation
- [dslMethodExists](vars/dslMethodExists.groovy) — safely feature-detect a pipeline step
- [getBuildCause](vars/getBuildCause.groovy) — list the causes that triggered the build
- [getChangeRequestBuildNumber](vars/getChangeRequestBuildNumber.groovy) — resolve the upstream CR build number
- [getJenkinsBuildChanges](vars/getJenkinsBuildChanges.groovy) — render an HTML changelog from `changeSets` ([docs](vars/getJenkinsBuildChanges.txt))
- [getJenkinsCommentSignatureStr](vars/getJenkinsCommentSignatureStr.groovy) — build a stable "posted by Jenkins" signature
- [getLatestApprover](vars/getLatestApprover.groovy) — resolve the last approver of an upstream job
- [getPromotedPipelineBuilds](vars/getPromotedPipelineBuilds.groovy) — iterate builds promoted by the promoted-builds plugin
- [getRequestor](vars/getRequestor.groovy) — identify who triggered the build
- [getStackTrace](vars/getStackTrace.groovy) — render an exception stack trace as a string
- [getUpstreamBuild](vars/getUpstreamBuild.groovy) — walk up the upstream build chain ([docs](vars/getUpstreamBuild.txt))
- [iterateBuilds](vars/iterateBuilds.groovy) — generic walker over a job's build history
- [isOrganizationProject](vars/isOrganizationProject.groovy) / [isOrigin](vars/isOrigin.groovy) — predicates for conditional stages
- [publishChangeLog](vars/publishChangeLog.groovy) — generate an HTML changelog from multi-repo checkouts
- [saveEnvVarsForChangeRequest](vars/saveEnvVarsForChangeRequest.groovy) — stash env for cross-stage replay

### SCM / Git helpers
- [getOrigin](vars/getOrigin.groovy) — resolve the remote origin URL
- [getProjectRepos](vars/getProjectRepos.groovy) — list repositories that belong to the current build
- [getRelatedBranch](vars/getRelatedBranch.groovy) — pick the target/related branch for a CR
- [getGitBuildTriggerCommitID](vars/getGitBuildTriggerCommitID.groovy) — commit that triggered the build
- [getGitCommitInfoStr](vars/getGitCommitInfoStr.groovy) — render commit meta as a string
- [getLastTag](vars/getLastTag.groovy) — last tag on the current branch (cross-platform sh/bat)
- [doCleanGitWorkTree](vars/doCleanGitWorkTree.groovy) / [requireCleanGitWorkTree](vars/requireCleanGitWorkTree.groovy) — clean / enforce a clean working tree (submodule-aware)

### Workspace hygiene
- [doCleanWorkspaceAfterStage](vars/doCleanWorkspaceAfterStage.groovy) — `cleanWs` wrapper that keeps artifacts needed by downstream stages
- [doCleanWorkspaceBeforeStage](vars/doCleanWorkspaceBeforeStage.groovy)
- [doCleanWorkspaceBeforeCheckout](vars/doCleanWorkspaceBeforeCheckout.groovy)
- [requireNoGeneratedGlobalFiles](vars/requireNoGeneratedGlobalFiles.groovy) — assert no licensing/tool side-effects were left in the container

### Build pipelines (product entry points)
End-to-end declarative pipelines that wrap repeated build flows for product
teams. Use these as templates loaded straight from a Jenkinsfile.

- [runDockerBuild](vars/runDockerBuild.groovy) — generic Docker image build with Nexus/Artifactory publishing ([docs](vars/runDockerBuild.txt))
- [runIDUDockerBuild](vars/runIDUDockerBuild.groovy) — Docker build tailored for the Spacebridge IDU firmware layout ([docs](vars/runIDUDockerBuild.txt))
- [runLoopBackBuild](vars/runLoopBackBuild.groovy) — LoopBack (Node.js) service build + deploy ([docs](vars/runLoopBackBuild.txt))
- [runAngularContainerBuild](vars/runAngularContainerBuild.groovy) / [runAngularDebBuild](vars/runAngularDebBuild.groovy) — Angular UI container and Debian package builds
- [runApplicationPluginBuild](vars/runApplicationPluginBuild.groovy) — application plugin build and publish ([docs](vars/runApplicationPluginBuild.txt))
- [runApplicationMetaPluginBuild](vars/runApplicationMetaPluginBuild.groovy) — meta-plugin (bundle of plugins) build and publish ([docs](vars/runApplicationMetaPluginBuild.txt))
- [runApplicationMetaPluginConfigurator](vars/runApplicationMetaPluginConfigurator.groovy) — pick the plugin and image versions of a meta plugin from drop-down lists and commit them to its `Manifest`, driven by a `jenkins.yml` ([docs](vars/runApplicationMetaPluginConfigurator.txt))
- [runPIPtoDebBuild](vars/runPIPtoDebBuild.groovy) — convert a PyPI package to a Debian package and publish ([docs](vars/runPIPtoDebBuild.txt))
- [runFirmwarePipeline](vars/runFirmwarePipeline.groovy) — SBC firmware build, publish, deploy, tag and release notes, driven by a per-branch `jenkins.yml` ([docs](vars/runFirmwarePipeline.txt))
- [runSmbToNexusPipeline](vars/runSmbToNexusPipeline.groovy) — publish archives from an SMB share to Nexus, zips to a Maven repository or directories zipped into a raw repository: scan, analyze, parallel upload, driven by a per-branch `jenkins.yml` ([docs](vars/runSmbToNexusPipeline.txt))

### Spacebridge product helpers
Domain-specific steps used by Spacebridge product pipelines. Not expected to be
reusable outside the Spacebridge ecosystem, but documented here for
completeness.

- [findSBCVersion](vars/findSBCVersion.groovy) — resolve the SBC (Spacebridge Controller) version for the current build
- [getIDUDockerImageInfo](vars/getIDUDockerImageInfo.groovy) / [IDUDockerImageInfoToString](vars/IDUDockerImageInfoToString.groovy) — describe an IDU Docker image ([docs](vars/getIDUDockerImageInfo.txt), [docs](vars/IDUDockerImageInfoToString.txt))
- [getIDURepoPath](vars/getIDURepoPath.groovy) / [getIDURepoTopPath](vars/getIDURepoTopPath.groovy) / [getIDURepoVersionPath](vars/getIDURepoVersionPath.groovy) — compute IDU artifact repo paths ([docs](vars/getIDURepoPath.txt), [docs](vars/getIDURepoTopPath.txt), [docs](vars/getIDURepoVersionPath.txt))
- [getIDUversions](vars/getIDUversions.groovy) — fetch SIDU `.sit` artifacts from Nexus as a `version -> path` map ([docs](vars/getIDUversions.txt))
- [getHubtestTestsuites](vars/getHubtestTestsuites.groovy) — enumerate Hubtest test-suite files ([docs](vars/getHubtestTestsuites.txt))

### Versioning helpers
- [getNPMPackageName](vars/getNPMPackageName.groovy) / [getNPMPackageVersion](vars/getNPMPackageVersion.groovy) — read NPM package metadata
- [sortVersions](vars/sortVersions.groovy) — keep the build versions of a list and order them newest first, numerically ([docs](vars/sortVersions.txt))
- [applyManifestVersions](vars/applyManifestVersions.groovy) — set key values in a `Manifest` text, keeping its formatting ([docs](vars/applyManifestVersions.txt))

### Templating and small utilities
- [arrayToHrefList](vars/arrayToHrefList.groovy) — render a list of URLs as an HTML `<ul>` ([docs](vars/arrayToHrefList.txt))
- [arrayToString](vars/arrayToString.groovy) — join a list with optional row/list wrappers ([docs](vars/arrayToString.txt))
- [inflateTemplate](vars/inflateTemplate.groovy) — `SimpleTemplateEngine` helper with CDATA-safe stringify mode ([docs](vars/inflateTemplate.txt))
- [downloadList](vars/downloadList.groovy) — download a list of URLs
- [getExternalPort](vars/getExternalPort.groovy) — resolve the mapped port of a container exposure
- [loadReports](vars/loadReports.groovy) — read files by glob into a single string for report embedding
- [loadSharedLibraryFile](vars/loadSharedLibraryFile.groovy) — write a library resource to disk and set permissions
- [makeYMD](vars/makeYMD.groovy) — build a YMD timestamp
- [readKeyValue](vars/readKeyValue.groovy) — parse `key=value` text ([docs](vars/readKeyValue.txt))
- [sayHello](vars/sayHello.groovy) — smoke-test step

## Required Jenkins plugins

The shared library makes use of several Jenkins pipeline steps and
integrations; install these plugins on your Jenkins controller if you plan to
use the corresponding functions from `vars/`:

* [Git plugin](https://plugins.jenkins.io/git/) — repository checkout and SCM triggers
* [Job DSL plugin](https://plugins.jenkins.io/job-dsl/) — used by some job-generation helpers
* [Jira plugin](https://plugins.jenkins.io/jira/) — Jira integration and version builders (used by `jiraCreateVersion` / `jiraAssignFixVersionToIssues`)
* [HTTP Request Plugin](https://plugins.jenkins.io/http_request/) — `httpRequest` is used widely for API calls (GitLab, Confluence, Jira, NetBox, etc.)
* [Pipeline Utility Steps plugin](https://plugins.jenkins.io/pipeline-utility-steps/) — utilities used by several helpers (file/zip helpers, `findFiles`, `readYaml`, etc.)
* [HTML Publisher Plugin](https://plugins.jenkins.io/htmlpublisher/) — `publishHTML` is used to publish generated site reports
* [JFrog Artifactory Plugin](https://plugins.jenkins.io/artifactory/) — `Artifactory.server(...)` and build-info publishing
* [Docker Pipeline plugin](https://plugins.jenkins.io/docker-workflow/) — `docker.withRegistry`, `docker.image` usage
* [Pipeline Maven Integration (withMaven)](https://plugins.jenkins.io/pipeline-maven/) — used in the Jenkinsfile for Maven lifecycle integration
* [Credentials Binding Plugin](https://plugins.jenkins.io/credentials-binding/) — `withCredentials` usage across many helpers
* [JUnit Plugin](https://plugins.jenkins.io/junit/) — `junit` step is used to publish test results (looks for JUnit XML under `target/surefire-reports`)
* [Nexus Artifact Uploader plugin](https://plugins.jenkins.io/nexus-jenkins-plugin/) — `nexusArtifactUploader` is used in some publishing helpers
* [Pipeline: Basic Steps](https://plugins.jenkins.io/workflow-basic-steps/) — `archiveArtifacts`, `sh`, and other basic steps used in pipelines
* [Parameter Separator](https://plugins.jenkins.io/parameter-separator/) — `separator` section headers in the parameters of `runFirmwarePipeline` and `runApplicationMetaPluginConfigurator`
* [URLTrigger](https://plugins.jenkins.io/urltrigger/) — `URLTrigger`, with which `runApplicationMetaPluginConfigurator` polls Nexus

Notes:
- Some library helpers call `manager.createSummary(...)` to add build summaries. That API is provided by legacy build-step contexts (and some post-build plugins). If you use those helpers inside non-classic contexts, ensure the plugin providing `createSummary` (or an equivalent summary mechanism) is available in your environment.
- Install only the plugins you actually need for your pipelines; many helpers are optional and call external services (Artifactory, Nexus, Confluence, GitLab, Jira).
