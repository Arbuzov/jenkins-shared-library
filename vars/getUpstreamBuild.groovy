/**
 * Return information about the upstream build that triggered the current one.
 *
 * Walks the full cause tree — top-level causes plus any nested causes reachable
 * via UpstreamCause.getUpstreamCauses() — and returns the first UpstreamCause
 * whose project differs from the current job (to ignore RebuildCause-like
 * causes pointing back at the same job). Falls back to the first
 * UpstreamCause of any kind if no foreign one is found. Returns null when the
 * build was not triggered by another job.
 *
 * When the upstream Run is reachable, its SCM details (git URL and commit
 * SHA) are also pulled from the `hudson.plugins.git.util.BuildData` action
 * so callers can, for example, report a commit status back to the upstream
 * project.
 *
 * Args:
 *  - root:  if true, walk the upstream chain from the chosen cause and return
 *           the top-most foreign UpstreamCause (default: false)
 *  - debug: echo the full cause tree and the selected cause to the build
 *           console (default: true)
 *
 * Returns:
 *  Map with keys:
 *    - project:     full job name, e.g. "folder/parent-job"
 *    - build:       upstream build number
 *    - url:         relative URL to the upstream build
 *    - displayName: "<project> #<build>"
 *    - gitUrl:      remote URL of the upstream build's SCM (or null)
 *    - gitCommit:   commit SHA checked out by the upstream build (or null)
 *  or null if no UpstreamCause is present anywhere in the tree.
 */
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def wantRoot = args.root ?: false
  def debug = args.containsKey('debug') ? args.debug : true

  def data = inspectCauses(wantRoot)

  if (debug) {
    echo "[getUpstreamBuild] currentProject=${env.JOB_NAME} scannedCauses=${data.trace.size()}"
    data.trace.eachWithIndex { entry, i ->
      echo "[getUpstreamBuild] #${i}: ${entry}"
    }
    echo "[getUpstreamBuild] selected: ${data.result ?: 'null (no upstream found)'}"
  }

  return data.result
}

@NonCPS
def inspectCauses(boolean wantRoot) {
  def currentProject = env.JOB_NAME
  def trace = []
  def upstreamCauses = []

  def queue = new ArrayList(currentBuild.rawBuild.getCauses())
  def visited = [] as Set
  while (!queue.isEmpty()) {
    def c = queue.remove(0)
    if (!visited.add(System.identityHashCode(c))) continue

    def className = c.getClass().getName()
    def shortDesc
    try { shortDesc = c.getShortDescription() } catch (Throwable e) { shortDesc = "(getShortDescription failed: ${e.message})" }

    def isUpstream = isUpstreamCause(c)
    def line = "class=${className} isUpstream=${isUpstream} desc=${shortDesc}"
    if (isUpstream) {
      def proj = safeGet(c, 'getUpstreamProject')
      def num  = safeGet(c, 'getUpstreamBuild')
      line += " project=${proj} build=${num}"
      upstreamCauses << c
      def nested = safeGet(c, 'getUpstreamCauses')
      if (nested instanceof Collection) queue.addAll(nested)
    }
    trace << line
  }

  if (upstreamCauses.isEmpty()) {
    return [result: null, trace: trace]
  }

  def cause = upstreamCauses.find { safeGet(it, 'getUpstreamProject') != currentProject } ?: upstreamCauses.first()

  if (wantRoot) {
    while (true) {
      def nested = safeGet(cause, 'getUpstreamCauses') ?: []
      def next = nested.find { isUpstreamCause(it) && safeGet(it, 'getUpstreamProject') != currentProject } ?:
                 nested.find { isUpstreamCause(it) }
      if (next == null) break
      cause = next
    }
  }

  def project = safeGet(cause, 'getUpstreamProject')
  def build = safeGet(cause, 'getUpstreamBuild')
  def url = safeGet(cause, 'getUpstreamUrl')

  def scm = fetchUpstreamScm(project, build)
  trace << "scm project=${project} build=${build} -> ${scm}"

  return [
    result: [
      project: project,
      build: build,
      url: url,
      displayName: "${project} #${build}",
      gitUrl: scm.gitUrl,
      gitCommit: scm.gitCommit,
    ],
    trace: trace,
  ]
}

@NonCPS
private boolean isUpstreamCause(cause) {
  if (cause instanceof hudson.model.Cause.UpstreamCause) return true
  def name = cause.getClass().getName()
  return name.endsWith('UpstreamCause') || name.endsWith('RebuildCause') || name.contains('UpstreamCause$')
}

@NonCPS
private Object safeGet(Object obj, String method) {
  try {
    return obj.metaClass.respondsTo(obj, method) ? obj."$method"() : null
  } catch (Throwable e) {
    return null
  }
}

@NonCPS
private Map fetchUpstreamScm(String project, Object build) {
  try {
    if (project == null || build == null) return [:]
    def item = jenkins.model.Jenkins.get().getItemByFullName(project)
    if (item == null) return [error: "item not found: ${project}"]
    def run = item.getBuildByNumber(build instanceof Number ? build.intValue() : Integer.parseInt(build.toString()))
    if (run == null) return [error: "build not found: ${project} #${build}"]
    def bd = (run.getActions() ?: []).find { it.getClass().getName() == 'hudson.plugins.git.util.BuildData' }
    if (bd == null) return [error: 'no BuildData action']
    String gitUrl = null
    String gitCommit = null
    try {
      def urls = bd.remoteUrls
      if (urls) gitUrl = urls.iterator().next()?.toString()
    } catch (Throwable e) {}
    try {
      def rev = bd.lastBuiltRevision
      if (rev) gitCommit = rev.sha1String
    } catch (Throwable e) {}
    return [gitUrl: gitUrl, gitCommit: gitCommit]
  } catch (Throwable e) {
    return [error: "fetchUpstreamScm failed: ${e.message}"]
  }
}
