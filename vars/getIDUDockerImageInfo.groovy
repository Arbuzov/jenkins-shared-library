def call(Map args) {
    Map iminfo = [:]
    def prevTags = []
    def extraTags = []
    def envP

    if (args && args.containsKey('_dbgComment')) {
        iminfo['dbgComment'] = args['_dbgComment']
    }

    envP = env
    if (args && args.containsKey('_envP')) {
        envP = args['_envP']
    }

    String topNamespace = 'idu'
    if (args['namespace']) {
        topNamespace = args['namespace']
    }

    String projectName = 'UNKNOWN_PROJECT'
    if (args['packageName']) {
        projectName = args['packageName']
    }

    String buildNumberStr = '1234567890'
    if (envP['BUILD_NUMBER']) {
        buildNumberStr = envP['BUILD_NUMBER']
    }
    int buildNumberNum = buildNumberStr as Integer

    String sourceBranch = ''
    if (envP['BRANCH_NAME']) {
        sourceBranch = envP['BRANCH_NAME']
    }
    if (!sourceBranch) {
        throw new Exception("Could not determine current branch name from BRANCH_NAME")
    }

    if (!envP['GITLAB_PROJECT_SSH_URL']) {
        throw new Exception("This build was not started by GitLab (branch indexing?)")
    }

    // Compute strings from current date
    Date nowDate = new Date()
    String nowYMStr = nowDate.format('yyyy/MM')

    Boolean isLongTermBranch = false
    Boolean isPrimaryBranch = false

    if ('true'.equals(envP['CI']) && sourceBranch && !envP['CHANGE_ID']) {
        isLongTermBranch = true
        if ('true'.equals(envP['BRANCH_IS_PRIMARY'])) {
            isPrimaryBranch = true
        }
    }

    String targetBranch = ''
    if (envP['GITLAB_OA_TARGET_BRANCH']) {
        targetBranch = envP['GITLAB_OA_TARGET_BRANCH']
    } else if (envP['CHANGE_TARGET']) {
        targetBranch = envP['CHANGE_TARGET']
    } else {
        if (isLongTermBranch) {
            targetBranch = sourceBranch
        } else {
            targetBranch = 'UNKNOWN_TARGET_BRANCH'
        }
    }

    String repositoryName = projectName
    String tagPrefix = "${sourceBranch}"

    // Override tag prefix for sourceTagPrefix and targetTagPrefix
    if (args['baseVersion'] && args['branchForBV']) {
        String branchRef = targetBranch;
        if (isLongTermBranch) {
            branchRef = sourceBranch
        }
        if (branchRef.equals(args['branchForBV'])) {
            // Tag prefix for merge requests: MR-123-baseversion
            tagPrefix = "${sourceBranch}-" + args['baseVersion']
            if (isLongTermBranch) {
                // Tag prefix for long term branches: baseversion
                tagPrefix = "" + args['baseVersion']
            }
        }
    }


    iminfo['registryHost'] = "nexus.asatnet.net:9913"
    iminfo['registryUrl'] = "http://nexus.asatnet.net:9913"
    iminfo['credentialsId'] = "local-registry-access"
    if (isLongTermBranch) {
        iminfo['registryHost'] = "registry.nexus.asatnet.net"
        iminfo['registryUrl'] = "http://registry.nexus.asatnet.net"
        iminfo['credentialsId'] = "local-registry-access"
    }
    if (true) {
        // FIXME: remove this when docker login works again
        iminfo['registryHost'] = "registry.nexus.asatnet.net"
        iminfo['registryUrl'] = "http://registry.nexus.asatnet.net"
        iminfo['credentialsId'] = "local-registry-access"
    }

    iminfo['imageID'] = "${topNamespace}/${nowYMStr}/${sourceBranch.toLowerCase()}/${repositoryName}"
    if (isLongTermBranch) {
        iminfo['imageID'] = "${topNamespace}/${repositoryName}"
    }

    iminfo['tag'] = "${tagPrefix}-b${buildNumberStr}"

    // Compute list of additional image tags to push to registry after build
    if (isLongTermBranch) {
        extraTags.add("${tagPrefix}")
        if (isPrimaryBranch) {
            extraTags.add("latest")
        }
    }
    iminfo['extraTags'] = extraTags

    for (int prevBuildNumberNum = buildNumberNum - 2; prevBuildNumberNum < buildNumberNum ; prevBuildNumberNum++) {
        if (prevBuildNumberNum <= 0) {
            continue
        }
        String prevBuildNumberStr = prevBuildNumberNum as String
        prevTags.add("${tagPrefix}-b${prevBuildNumberStr}")
    }

    // For long term branches, pull the latest build of the same branch
    if (isLongTermBranch) {
        prevTags.add("${tagPrefix}")
    }

    iminfo['prevTags'] = prevTags

    return iminfo
}
