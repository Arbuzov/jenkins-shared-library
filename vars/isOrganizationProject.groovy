Boolean call() {
    reportUsage(getClass().protectionDomain.codeSource.location.path)
    return currentBuild.getRawBuild().project.getFullName().replaceAll('%2F','/').split('/').length>2;
}