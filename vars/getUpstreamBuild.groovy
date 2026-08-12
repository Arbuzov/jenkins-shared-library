/**
 * Return information about the upstream build that triggered the current one.
 *
 * Walks the full cause tree — top-level causes plus any nested causes reachable
 * via UpstreamCause.getUpstreamCauses() — and returns the first UpstreamCause
 * whose project differs from the current job. Returns null when no such
 * foreign upstream exists (manual runs, SCM/timer triggers, and
 * RebuildCause-only rebuilds of the current job).
 *
 * When the upstream Run is reachable, its SCM details (git URL and commit
 * SHA) are pulled from the upstream Run's environment (main SCM's
 * GIT_URL / GIT_COMMIT), falling back to the first BuildData action.
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
 *    - gitUrl:      remote URL of the upstream build's main SCM (or null)
 *    - gitCommit:   commit SHA checked out by the upstream build (or null)
 *    - mrIid:       GitLab MR IID when the upstream is a branch source MR job
 *                   (project name ends with "/MR-<digits>"), else null
 *  or null if no foreign UpstreamCause is present anywhere in the tree.
 */
def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def wantRoot = args.root ?: false
  def debug = args.containsKey('debug') ? args.debug : true

  def data = inspectCauses(wantRoot)

  if (debug) {
    echo "[getUpstreamBuild] currentProject=${env.JOB_NAME} scannedCauses=${data.trace.size()}"
    data.trace.eachWithIndex { entry, index ->
      echo "[getUpstreamBuild] #${index}: ${entry}"
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
    def cause = queue.remove(0)
    if (!visited.add(System.identityHashCode(cause))) continue

    def className = cause.getClass().getName()
    def shortDescription
    try {
      shortDescription = cause.getShortDescription()
    } catch (Throwable exception) {
      shortDescription = "(getShortDescription failed: ${exception.message})"
    }

    def isUpstream = isUpstreamCause(cause)
    def line = "class=${className} isUpstream=${isUpstream} desc=${shortDescription}"
    if (isUpstream) {
      def causeProject = safeGet(cause, 'getUpstreamProject')
      def causeBuild = safeGet(cause, 'getUpstreamBuild')
      line += " project=${causeProject} build=${causeBuild}"
      upstreamCauses << cause
      def nestedCauses = safeGet(cause, 'getUpstreamCauses')
      if (nestedCauses instanceof Collection) queue.addAll(nestedCauses)
    }
    trace << line
  }

  if (upstreamCauses.isEmpty()) {
    return [result: null, trace: trace]
  }

  def selectedCause = upstreamCauses.find { safeGet(it, 'getUpstreamProject') != currentProject }
  if (selectedCause == null) {
    trace << "no foreign upstream found (only same-project rebuild(s)); returning null"
    return [result: null, trace: trace]
  }

  if (wantRoot) {
    while (true) {
      def nestedCauses = safeGet(selectedCause, 'getUpstreamCauses') ?: []
      def nextCause = nestedCauses.find { isUpstreamCause(it) && safeGet(it, 'getUpstreamProject') != currentProject } ?:
                      nestedCauses.find { isUpstreamCause(it) }
      if (nextCause == null) break
      selectedCause = nextCause
    }
  }

  def project = safeGet(selectedCause, 'getUpstreamProject')
  def build = safeGet(selectedCause, 'getUpstreamBuild')
  def url = safeGet(selectedCause, 'getUpstreamUrl')

  def scmInfo = fetchUpstreamScm(project, build)
  trace << "scm project=${project} build=${build} -> ${scmInfo}"

  def mrIid = null
  def mrMatcher = (project =~ /\/MR-(\d+)$/)
  if (mrMatcher) mrIid = mrMatcher[0][1]

  return [
    result: [
      project: project,
      build: build,
      url: url,
      displayName: "${project} #${build}",
      gitUrl: scmInfo.gitUrl,
      gitCommit: scmInfo.gitCommit,
      mrIid: mrIid,
    ],
    trace: trace,
  ]
}

@NonCPS
private boolean isUpstreamCause(cause) {
  if (cause instanceof hudson.model.Cause.UpstreamCause) return true
  def className = cause.getClass().getName()
  return className.endsWith('UpstreamCause') || className.endsWith('RebuildCause') || className.contains('UpstreamCause$')
}

@NonCPS
private Object safeGet(Object target, String methodName) {
  try {
    return target.metaClass.respondsTo(target, methodName) ? target."$methodName"() : null
  } catch (Throwable exception) {
    return null
  }
}

@NonCPS
private Map fetchUpstreamScm(String project, Object build) {
  try {
    if (project == null || build == null) return [:]
    def item = jenkins.model.Jenkins.get().getItemByFullName(project)
    if (item == null) return [error: "item not found: ${project}"]
    def buildNumber = build instanceof Number ? build.intValue() : Integer.parseInt(build.toString())
    def upstreamRun = item.getBuildByNumber(buildNumber)
    if (upstreamRun == null) return [error: "build not found: ${project} #${build}"]

    def mainScmUrl = resolveJobMainScmUrl(item)
    def scmCommit = resolveRunScmCommit(upstreamRun, mainScmUrl)
    if (mainScmUrl && scmCommit) {
      return [gitUrl: mainScmUrl, gitCommit: scmCommit, source: 'Job+Run']
    }

    try {
      def environmentVars = upstreamRun.getEnvironment(hudson.model.TaskListener.NULL)
      def envGitUrl = environmentVars?.get('GIT_URL')
      def envGitCommit = environmentVars?.get('GIT_COMMIT')
      if (envGitUrl && envGitCommit) {
        return [gitUrl: envGitUrl, gitCommit: envGitCommit, source: 'env']
      }
    } catch (Throwable exception) {
      // fall through
    }

    def buildData = (upstreamRun.getActions() ?: []).find { it.getClass().getName() == 'hudson.plugins.git.util.BuildData' }
    if (buildData == null) {
      return [error: "could not resolve SCM (scmUrl=${mainScmUrl}, commit=${scmCommit})"]
    }
    String gitUrl = null
    String gitCommit = null
    try {
      def remoteUrls = buildData.remoteUrls
      if (remoteUrls) gitUrl = remoteUrls.iterator().next()?.toString()
    } catch (Throwable exception) {}
    try {
      def revision = buildData.lastBuiltRevision
      if (revision) gitCommit = revision.sha1String
    } catch (Throwable exception) {}
    return [gitUrl: gitUrl, gitCommit: gitCommit, source: 'BuildData']
  } catch (Throwable exception) {
    return [error: "fetchUpstreamScm failed: ${exception.message}"]
  }
}

/**
 * Resolve the main SCM remote URL for a Jenkins item.
 * - Multibranch Pipeline branch job: take the parent folder's first SCMSource.
 * - Plain Pipeline from SCM: take the first userRemoteConfig of the job's SCM.
 */
@NonCPS
private String resolveJobMainScmUrl(Object item) {
  try {
    def parent = item.metaClass.respondsTo(item, 'getParent') ? item.getParent() : null
    if (parent?.metaClass?.respondsTo(parent, 'getSCMSources')) {
      def sources = parent.getSCMSources() ?: []
      if (!sources.isEmpty()) {
        def firstSource = sources.first()
        if (firstSource?.metaClass?.respondsTo(firstSource, 'getRemote')) {
          return firstSource.getRemote()?.toString()
        }
      }
    }
    if (item.metaClass.respondsTo(item, 'getDefinition')) {
      def definition = item.getDefinition()
      if (definition?.metaClass?.respondsTo(definition, 'getScm')) {
        def scm = definition.getScm()
        if (scm?.metaClass?.respondsTo(scm, 'getUserRemoteConfigs')) {
          def configs = scm.getUserRemoteConfigs() ?: []
          if (!configs.isEmpty()) return configs.first().getUrl()?.toString()
        }
      }
    }
  } catch (Throwable exception) {
    // swallow
  }
  return null
}

/**
 * Resolve the commit SHA checked out by the main SCM of the given Run.
 * - Prefer SCMRevisionAction (set by multibranch branch sources).
 * - Otherwise pick the BuildData action whose remoteUrls contain the expected
 *   main SCM URL, returning its lastBuiltRevision.
 */
@NonCPS
private String resolveRunScmCommit(Object upstreamRun, String mainScmUrl) {
  try {
    def scmRevisionAction = (upstreamRun.getActions() ?: []).find {
      it.getClass().getName() == 'jenkins.scm.api.SCMRevisionAction'
    }
    if (scmRevisionAction?.metaClass?.respondsTo(scmRevisionAction, 'getRevision')) {
      def revision = scmRevisionAction.getRevision()
      if (revision?.metaClass?.respondsTo(revision, 'getHash')) {
        def hash = revision.getHash()?.toString()
        if (hash) return hash
      }
    }
    if (mainScmUrl) {
      def matchingBuildData = (upstreamRun.getActions() ?: []).find {
        if (it.getClass().getName() != 'hudson.plugins.git.util.BuildData') return false
        try {
          def urls = it.remoteUrls ?: []
          return urls.any { remoteUrl -> remoteUrl?.toString() == mainScmUrl }
        } catch (Throwable exception) {
          return false
        }
      }
      if (matchingBuildData?.lastBuiltRevision) {
        return matchingBuildData.lastBuiltRevision.sha1String
      }
    }
  } catch (Throwable exception) {
    // swallow
  }
  return null
}
