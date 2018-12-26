Boolean call() {
    return currentBuild.getRawBuild().project.getFullName().replaceAll('%2F','/').split('/').length>2;
}