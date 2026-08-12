// getIDURepoVersionPath(): Get the build-specific component of Nexus
// repository path for an IDU project (depends on build number and/or
// current date)
//
// Usage:
//
// Use together with getIDURepoTopPath().
//
// (a) Add the following to the first stage that always runs (not
//     conditionally) and has access to the source code (NOT agent none):
//
//         script {
//             env.putAt('PIPELINE_BIN_REPO_TOP_PATH',
//                         getIDURepoTopPath(project: 'example_project'))
//             env.putAt('PIPELINE_BIN_REPO_VERSION_PATH',
//                         getIDURepoVersionPath(project: 'example_project'))
//         }
//
// (b) Add the following snippet to each stage that publishes artifacts to
//     Nexus (there may be more than one stage that publishes artifacts):
//
//         script {
//             String repo_top_path = env.getAt('PIPELINE_BIN_REPO_TOP_PATH')
//             String repo_version_path = env.getAt('PIPELINE_BIN_REPO_VERSION_PATH')
//
//             def pub_arr = publishNexus(exampleProjectAntPattern, "${repo_top_path}/${repo_version_path}/")
//             // [...]
//         }
//
// NOTE: 1. If there is no access to the source code (agent none), then
//          the commit ID may show up as 200000000000
//
// NOTE: 2. For publishing to Nexus only, not Artifactory
//

String call(Map args) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)
    // NOTE: store IDU namespace artifacts under "idu/" by default
    String namespace = ((args && args.containsKey('namespace')) ? args.namespace : 'idu')
    String project = ((args && args.containsKey('project')) ? args.project : 'UNKNOWN_PROJECT')
    Boolean use_commit_id = ((args && args.containsKey('use_commit_id')) ? args.use_commit_id : true)

    String branchName = ''
    Date nowDate = new Date()
    String artifactsSubdirDate = nowDate.format('yyyy/MM')
    String artifactsDirnameDate = nowDate.format('yyyy-MM-dd-HHmmss')
    String buildNumberStr = ''
    String subDir = 'UNKNOWN_SUBDIR'

    if (env.getAt('BRANCH_NAME')) {
        // in merge requests, BRANCH_NAME is MR-NNNN
        branchName = env.getAt('BRANCH_NAME')
    } else if (env.getAt('CHANGE_ID') && env.getAt('CHANGE_BRANCH')) {
        // in merge requests, CHANGE_BRANCH is the original branch name in developer's fork
        branchName = env.getAt('CHANGE_BRANCH')
    }

    if (!branchName) {
        branchName = 'UNKNOWN_BRANCH'
    }

    buildNumberStr = env.getAt('BUILD_NUMBER')

    if (!buildNumberStr) {
        buildNumberStr = '1234567890'
    }

    if (env.getAt('CHANGE_ID')) {
        // We are in a change request (merge request)

        if (use_commit_id) {
            // publish under "yyyy/MM/MR-NNNN/DATE-bNNN-commitID"
            subDir = artifactsSubdirDate + '/' + branchName + '/' +
                     artifactsDirnameDate + '-b' + buildNumberStr + '-' + getGitBuildTriggerCommitID().take(12)
        } else {
            // publish under "yyyy/MM/MR-NNNN/bNNN"
            subDir = artifactsSubdirDate + '/' + branchName + '/' +
                     'b' + buildNumberStr
        }
    } else {
        // We are in master branch or another long-term branch (NOT merge request)

        if (use_commit_id) {
            // publish under "branchname/yyyy/MM/DATE-bNNN-commitID"
            subDir = branchName + '/' + artifactsSubdirDate + '/' +
                     artifactsDirnameDate + '-b' + buildNumberStr + '-' + getGitBuildTriggerCommitID().take(12)
        } else {
            // publish under "branchname/yyyy/MM/bNNN"
            subDir = branchName + '/' + artifactsSubdirDate + '/' +
                     'b' + buildNumberStr
        }
    }

    return subDir
}
