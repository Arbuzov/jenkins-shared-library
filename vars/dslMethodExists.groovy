import org.jenkinsci.plugins.workflow.cps.CpsScript

boolean call(CpsScript script, String name) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)
  return script.getBinding().hasVariable(name)
}