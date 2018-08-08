/**
 * Get information about the person approved last build dialog
 */
String call() {
    def latestId = null;
    def latestName = null;
    def acts = currentBuild.rawBuild.getAllActions()
    for (act in acts) {
        if (act instanceof org.jenkinsci.plugins.workflow.support.steps.input.ApproverAction) {
            latestId = act.userId;
            latestName = User.get(latestId).getDisplayName();
        }
    }
    return "${latestName}";
}