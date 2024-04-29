// getIDURepoTopPath(): Get the top component of Nexus repository path
// for an IDU project (does NOT depend on build number or current date)
//
// Usage:
//
// Use together with getIDURepoVersionPath().
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
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    // NOTE: store IDU namespace artifacts under "idu/" by default
    String namespace = ((args && args.containsKey('namespace')) ? args.namespace : 'idu')
    String project = ((args && args.containsKey('project')) ? args.project : 'UNKNOWN_PROJECT')

    String repoTopPath = 'http://nexus.asatnet.net/repository/store/' + namespace + '/' + project

    if (env.getAt('CHANGE_ID') != null) {
        repoTopPath = 'http://nexus.asatnet.net/repository/store-DEV/' + namespace + '/' + project
    }

    return repoTopPath
}
