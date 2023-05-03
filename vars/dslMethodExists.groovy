import org.jenkinsci.plugins.workflow.cps.CpsScript

boolean call(CpsScript script, String name) {
  return script.getBinding().hasVariable(name)
}