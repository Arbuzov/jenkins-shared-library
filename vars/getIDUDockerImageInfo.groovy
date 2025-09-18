// getIDUDockerImageInfo(): Get information needed to build a Docker image for the current build on Jenkins
//
// Used inside runIDUDockerBuild() function.
//
// Arguments in args:
//
// namespace: top-level namespace (required, lowercase)
// packageName: package name (required, lowercase)
// baseVersion: version of the top level image (optional)
//              Forwarded as --build-arg "BASE_VERSION=${baseVersion}"
//              when building the Docker image from Dockerfile
// branchForBV: branch which builds this baseVersion (optional).
//              If arg.branchForBV is specified, and if the source
//              or target branch name matches ${arg.branchForBV},
//              the tag prefix is set to ${iminfo.baseVersion} and
//              not the current branch name
//
// Returns: image info map iminfo, with the following fields:
//
// iminfo.registryHost: registry host with port
//                      (example: registry-dev.nexus.asatnet.net)
// iminfo.registryUrl: registry URL schema, host and port)
//                     (example: http://registry-dev.nexus.asatnet.net)
// iminfo.credentialsId: credentialsId to access the registry
//                       from Jenkins (example: local-registry-access)
// iminfo.packageName: package name for current project (example: my-project)
// iminfo.imageID: image ID for current project
//                 (example: my-ns/2024/06/mr-532/my-project)
// iminfo.tag: image tag for current project (example: MR-532-b4)
// iminfo.extraTags: array of extra tags to push after building (example: latest)
// iminfo.prevTags: array of tags to pull before building, to warm up the Docker
//                  cache of the build host (two previous builds of the
//                  same branch) (example: MR-532-b2, MR-532-b3)
//
// Usage example:
//
//      Map iminfo = [:]
//      iminfo = getIDUDockerImageInfo(
//          namespace: 'my-ns',
//          packageName: 'my-project',
//          baseVersion: 'bver-20220531',
//          branchForBV: 'master',
//      )
//

def call(Map arg) {
    String topNamespace = ''
    String packageName = ''
    String buildNumberStr = ''
    int buildNumberNum = 1234567890
    Map iminfo = [:]
    def prevTags = []
    def extraTags = []
    def envP

    if (arg && arg.containsKey('_dbgComment')) {
        iminfo.dbgComment = arg._dbgComment
    }

    envP = env
    if (arg && arg.containsKey('_envP')) {
        envP = arg._envP
    }

    if (arg.namespace) {
        topNamespace = arg.namespace
    }
    if (!(topNamespace && topNamespace.equals(topNamespace.toLowerCase()))) {
        throw new Exception("namespace argument must be lowercase")
    }

    if (arg.packageName) {
        packageName = arg.packageName
    }
    if (!(packageName && packageName.equals(packageName.toLowerCase()))) {
        throw new Exception("packageName argument must be lowercase")
    }

    try {
        buildNumberStr = envP.BUILD_NUMBER
        buildNumberNum = buildNumberStr as Integer
    } catch (Exception e) {
        throw new Exception("Could not determine build number")
    }

    String sourceBranch = ''
    if (envP.BRANCH_NAME) {
        sourceBranch = envP.BRANCH_NAME
    }
    if (!sourceBranch) {
        throw new Exception("Could not determine current branch name from BRANCH_NAME")
    }

    // Compute strings from current date
    Date nowDate = new Date()
    String nowYMStr = nowDate.format('yyyy/MM')

    Boolean isLongTermBranch = false
    Boolean isPrimaryBranch = false
    if ('true'.equals(envP['CI']) && sourceBranch && !(envP.CHANGE_ID)) {
        isLongTermBranch = true
        if ('true'.equals(envP['BRANCH_IS_PRIMARY'])) {
            isPrimaryBranch = true
        }
    }

    String targetBranch = ''
    if (isLongTermBranch) {
        targetBranch = sourceBranch
    } else {
        if (envP.GITLAB_OA_TARGET_BRANCH) {
            targetBranch = envP.GITLAB_OA_TARGET_BRANCH
        } else if (envP.CHANGE_TARGET) {
            targetBranch = envP.CHANGE_TARGET
        } else {
            targetBranch = 'UNKNOWN_TARGET_BRANCH'
        }
    }

    String repositoryName = packageName.toLowerCase()

    String tagPrefix = "${sourceBranch}"
    // Override tag prefix with baseVersion if source or target branch matches arg.branchForBV
    if (arg.baseVersion && arg.branchForBV) {
        String branchRef = targetBranch;
        if (isLongTermBranch) {
            branchRef = sourceBranch
        }
        if (branchRef.equals(arg['branchForBV'])) {
            // Tag prefix for merge requests: MR-123-baseversion
            tagPrefix = "${sourceBranch}-" + arg.baseVersion
            if (isLongTermBranch) {
                // Tag prefix for long term branches: baseversion
                tagPrefix = "" + arg.baseVersion
            }
        }
    }

    iminfo.registryHost = "registry-dev.nexus.asatnet.net"
    iminfo.registryUrl = "http://registry-dev.nexus.asatnet.net"
    iminfo.credentialsId = "local-registry-access"
    if (isLongTermBranch) {
        iminfo.registryHost = "registry.nexus.asatnet.net"
        iminfo.registryUrl = "http://registry.nexus.asatnet.net"
        iminfo.credentialsId = "local-registry-access"
    }

    iminfo.packageName = packageName

    iminfo.imageID = "${topNamespace}/${nowYMStr}/${sourceBranch.toLowerCase()}/${repositoryName}"
    if (isLongTermBranch) {
        iminfo.imageID = "${topNamespace}/${repositoryName}"
    }

    iminfo.tag = "${tagPrefix}-b${buildNumberStr}"

    // Compute list of additional image tags to push to registry after build
    if (isLongTermBranch) {
        // NOTE: deployments of and updates to artifacts are not allowed (Disable redeploy)
        // cannot redeploy the tag for current branch with extraTags.add("${tagPrefix}")
        if (isPrimaryBranch) {
            // It is allowed to redeploy the 'latest' tag
            extraTags.add("latest")
        }
    }
    iminfo.extraTags = extraTags

    // Compute the list of images to pull, to warm up the Docker cache before building current image

    // Pull the previous builds of the same branch (both merge requests and long term branches)
    for (int prevBuildNumberNum = buildNumberNum - 2; prevBuildNumberNum < buildNumberNum ; prevBuildNumberNum++) {
        if (prevBuildNumberNum <= 0) {
            continue
        }
        String prevBuildNumberStr = prevBuildNumberNum as String
        prevTags.add("${tagPrefix}-b${prevBuildNumberStr}")
    }

    // For long term branches, pull the latest build of the same branch
    if (isLongTermBranch) {
        // NOTE: deployments of and updates to artifacts are not allowed (Disable redeploy)
        // See the note above
        // will not pull the tag for current branch with prevTags.add("${tagPrefix}")
        if (isPrimaryBranch) {
            prevTags.add("latest")
        }
    }

    iminfo.prevTags = prevTags

    return iminfo
}
