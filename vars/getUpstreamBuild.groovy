/**
 * Return information about the upstream build that triggered the current one.
 *
 * Walks the full cause tree — top-level causes plus any nested causes reachable
 * via UpstreamCause.getUpstreamCauses() — and returns the first UpstreamCause
 * whose project differs from the current job (to ignore RebuildCause, which
 * extends UpstreamCause and points back at the current job). Falls back to the
 * first UpstreamCause of any kind if no foreign one is found. Returns null
 * when the build was not triggered by another job (manual, SCM, timer, etc.).
 *
 * Args:
 *  - root: if true, walk the upstream chain from the chosen cause and return
 *          the top-most foreign UpstreamCause (default: false)
 *
 * Returns:
 *  Map with keys:
 *    - project: full job name, e.g. "folder/parent-job"
 *    - build:   upstream build number
 *    - url:     relative URL to the upstream build
 *    - displayName: "<project> #<build>"
 *  or null if no UpstreamCause is present anywhere in the tree.
 */
@NonCPS
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def wantRoot = args.root ?: false
  def currentProject = env.JOB_NAME

  def queue = new ArrayList(currentBuild.rawBuild.getCauses())
  def visited = [] as Set
  def upstreamCauses = []
  while (!queue.isEmpty()) {
    def c = queue.remove(0)
    if (!(c instanceof hudson.model.Cause.UpstreamCause)) continue
    if (!visited.add(System.identityHashCode(c))) continue
    upstreamCauses << c
    def nested = c.getUpstreamCauses()
    if (nested) queue.addAll(nested)
  }
  if (upstreamCauses.isEmpty()) {
    return null
  }

  def cause = upstreamCauses.find { it.upstreamProject != currentProject } ?: upstreamCauses.first()

  if (wantRoot) {
    while (true) {
      def nested = cause.getUpstreamCauses() ?: []
      def next = nested.find {
        it instanceof hudson.model.Cause.UpstreamCause && it.upstreamProject != currentProject
      } ?: nested.find { it instanceof hudson.model.Cause.UpstreamCause }
      if (next == null) break
      cause = next
    }
  }

  return [
    project: cause.upstreamProject,
    build: cause.upstreamBuild,
    url: cause.upstreamUrl,
    displayName: "${cause.upstreamProject} #${cause.upstreamBuild}",
  ]
}
