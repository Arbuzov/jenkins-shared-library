/**
 * Return information about the upstream build that triggered the current one.
 *
 * Walks the causes of the current build and returns the first UpstreamCause
 * found. Returns null when the build was not triggered by another job (manual
 * run, SCM poll, timer, webhook, etc.).
 *
 * Args:
 *  - root: if true, walk the upstream chain and return the top-most upstream
 *          cause (default: false — returns the immediate parent)
 *
 * Returns:
 *  Map with keys:
 *    - project: full job name, e.g. "folder/parent-job"
 *    - build:   upstream build number
 *    - url:     relative URL to the upstream build
 *    - displayName: "<project> #<build>"
 *  or null if no UpstreamCause is present.
 */
@NonCPS
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def root = args.root ?: false
  def cause = currentBuild.rawBuild.getCauses().find {
    it instanceof hudson.model.Cause.UpstreamCause
  }
  if (cause == null) {
    return null
  }

  if (root) {
    def current = cause
    while (true) {
      def parents = current.getUpstreamCauses() ?: []
      def nextParent = parents.find { it instanceof hudson.model.Cause.UpstreamCause }
      if (nextParent == null) break
      current = nextParent
    }
    cause = current
  }

  return [
    project: cause.upstreamProject,
    build: cause.upstreamBuild,
    url: cause.upstreamUrl,
    displayName: "${cause.upstreamProject} #${cause.upstreamBuild}",
  ]
}
