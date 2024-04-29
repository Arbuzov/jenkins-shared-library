// getIDURepoPath(): Get the Nexus repository path for an IDU project
// (both top level component and build-specific component that depends
// on build number and/or current date)
//
// (a) Add the following to the first stage that always runs (not
//     conditionally) and has access to the source code (NOT agent none):
//
//         script {
//             env.putAt('PIPELINE_BIN_REPO_FULL_PATH',
//                         getIDURepoPath(project: 'example_project'))
//         }
//
// (b) Add the following snippet to each stage that publishes artifacts to
//     Nexus (there may be more than one stage that publishes artifacts):
//
//         script {
//             String repo_full_path = env.getAt('PIPELINE_BIN_REPO_FULL_PATH')
//
//             def pub_arr = publishNexus(exampleProjectAntPattern, "${repo_full_path}/")
//
//             // [...]
//         }
//
// NOTE: 1. If there is no access to the source code (agent none), then
//          the commit ID may show up as 200000000000
//
// NOTE: 2. For publishing to Nexus only, not Artifactory
//

String call(Map args) {
    reportUsage(getClass().protectionDomain.codeSource.location.path)

    String repoTopPath = getIDURepoTopPath(args)
    String repoVersPath = getIDURepoVersionPath(args)

    String repoPath = repoTopPath + "/" + repoVersPath

    return repoPath
}
