package jenkins.model

// Minimal stub so JenkinsPipelineUnit can compile pipeline scripts that name
// this Jenkins-internal class. Not exercised by the tests.
class CauseOfInterruption implements Serializable {
    static class UserInterruption extends CauseOfInterruption {
        String description
        UserInterruption(String description) { this.description = description }
        String getShortDescription() { return description }
    }
}
