package org.jenkinsci.plugins.workflow.steps

// Minimal stub so JenkinsPipelineUnit can compile pipeline scripts that
// `new` this Jenkins-internal class. Not exercised by the tests.
class FlowInterruptedException extends RuntimeException {
    FlowInterruptedException(Object result) { super("aborted") }
}
