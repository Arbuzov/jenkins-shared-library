def call() {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    if (isUnix()) {
        return sh(
            script: 'git describe --tags --abbrev=0',
            returnStdout: true
        ).trim()
    } else {
        return bat(
            script: '@git describe --tags --abbrev=0',
            returnStdout: true
        ).trim()
    }
}
