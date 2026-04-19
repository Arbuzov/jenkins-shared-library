String call() {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    def result = ''
    if (env.CHANGE_ID) {
        result = "mr${env.CHANGE_ID}"
    }/* else if (env.GIT_BRANCH) {
        result = env.GIT_BRANCH.replace('/', '-')
    } else if (env.GIT_TAG_NAME) {
        result = env.GIT_TAG_NAME.replace('/', '-')
    }*/
    return result
}