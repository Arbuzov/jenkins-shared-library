Boolean call() {
    return env.getAt('gitlabMergeRequestId')!=null && !env.getAt('gitlabMergeRequestId').equals("");
}