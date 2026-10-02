import hudson.model.Cause
import hudson.model.CauseAction
import hudson.model.Job
import hudson.model.Run
import jenkins.model.Jenkins

/**
 * Link a specific build to the CURRENT build as an upstream cause.
 *
 * After this runs, the current build's page shows "Started by upstream project
 * <X> #<N>" and the upstream job appears to its left in the Pipeline
 * upstream/downstream graph — exactly the link a native `build job:` step would
 * have recorded.
 *
 * This is the low-level primitive. Its purpose is to restore a Cause that never
 * got written: when the test-orchestrator pushes a commit into a build repo, a
 * GitLab push webhook starts the consumer build as a graph *root* with an empty
 * UpstreamCause, and no plugin can reconstruct the link after the fact. Calling
 * this from the consumer pipeline re-injects it. See basic-memory
 * "Jenkins causal tracing across the GitLab webhook boundary (test-orchestrator
 * -> build repo)". For the commit-driven wrapper, see linkUpstreamFromCommit.
 *
 * The injection uses rawBuild / replaceAction / save / Jenkins.get, which are
 * outside the Groovy sandbox whitelist, so this MUST live in a trusted global
 * library (this one is — getUpstreamBuild.groovy relies on the same APIs).
 *
 * The upstream build is identified one of two ways (checked in this order):
 *   - run:             a hudson.model.Run or pipeline RunWrapper to link directly
 *                      (e.g. the value returned by getUpstreamBuild or a `build`
 *                      step). Takes precedence over project/build.
 *   - project + build: the upstream job's full name ("folder/sub/job", or a
 *                      multibranch branch/MR job like ".../master" / ".../MR-12")
 *                      plus the upstream build number.
 *
 * The existing CauseAction is *merged*, not replaced: any cause already present
 * (the GitLab push cause, a manual cause, ...) is preserved and the new
 * UpstreamCause is appended. Re-linking the same upstream build is idempotent.
 *
 * With reverse: true the link goes the other way: the CURRENT build becomes the
 * upstream of the given build, which is the one that gets the cause. The meta
 * plugin configurator uses it for a run that commits nothing, to point at the
 * build of the Manifest it confirmed.
 *
 * Args:
 *   - run     (Object):     upstream Run/RunWrapper to link.
 *   - project (String):     full job name of the upstream job.
 *   - build   (int/String): upstream build number.
 *   - reverse (boolean):    make the current build the upstream of the given one
 *                           (default false).
 *   - debug   (boolean):    echo the outcome to the build log (default true).
 *
 * Returns true when the link is present after the call (added now, or already
 * there); false when the upstream build could not be resolved. Never throws on a
 * missing / unresolvable upstream — it logs and returns false, so callers can
 * treat linking as best-effort.
 */
def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  boolean debug = args.containsKey('debug') ? args.debug : true
  Map outcome = injectUpstream(args)

  if (debug) {
    echo "[linkUpstreamBuild] ${outcome.message}"
  }
  return outcome.linked
}

/**
 * Resolve the given Run from args and merge an UpstreamCause for it into the current
 * build's CauseAction, or with reverse one for the current build into the given
 * build's. @NonCPS because it touches non-serializable Jenkins model
 * objects (Run, Cause, CauseAction) and must not yield across a pipeline step.
 * Returns [linked: boolean, message: String]; never throws.
 */
@NonCPS
private Map injectUpstream(Map args) {
  try {
    Run given = resolveRun(args)
    if (given == null) {
      return [linked: false, message: "upstream build not resolved (${describeArgs(args)})"]
    }

    boolean reverse = args.reverse?.toString()?.toBoolean()
    Run upstreamRun = reverse ? currentBuild.rawBuild : given
    Run build = reverse ? given : currentBuild.rawBuild
    String upstreamProject = upstreamRun.getParent().getFullName()
    int upstreamNumber = upstreamRun.getNumber()
    String link = "${upstreamProject} #${upstreamNumber} -> ${build.getParent().getFullName()} #${build.getNumber()}"

    def existing = build.getAction(CauseAction)
    def causes = new ArrayList(existing ? existing.getCauses() : [])

    boolean already = causes.any { cause ->
      cause instanceof Cause.UpstreamCause &&
        cause.getUpstreamProject() == upstreamProject &&
        cause.getUpstreamBuild() == upstreamNumber
    }

    String message
    if (already) {
      message = "already linked: ${link}"
    } else {
      causes.add(new Cause.UpstreamCause(upstreamRun))
      build.replaceAction(new CauseAction(causes))
      build.save()
      message = "linked ${link}"
    }
    // Refresh the graph cache on both paths: the cause may be persisted while the
    // cache is still stale (an earlier refresh failed, or the plugin was absent then).
    refreshDownstreamCache(build)
    return [linked: true, message: message]
  } catch (Throwable throwable) {
    return [linked: false, message: "failed to link upstream: ${throwable.getClass().getName()}: ${throwable.message}"]
  }
}

/** Resolve args -> a hudson.model.Run, or null when it cannot be found. */
@NonCPS
private Run resolveRun(Map args) {
  if (args.run != null) {
    def run = args.run
    // Unwrap a pipeline RunWrapper to the underlying hudson.model.Run.
    if (run.metaClass.respondsTo(run, 'getRawBuild')) {
      return run.getRawBuild()
    }
    return (run instanceof Run) ? run : null
  }

  String project = args.project?.toString()?.trim()
  def buildRef = args.build
  if (!project || buildRef == null) {
    return null
  }

  Job job = Jenkins.get().getItemByFullName(project, Job)
  if (job == null) {
    return null
  }
  int number = (buildRef instanceof Number) ? buildRef.intValue() : Integer.parseInt(buildRef.toString().trim())
  return job.getBuildByNumber(number)
}

@NonCPS
private String describeArgs(Map args) {
  return "run=${args.run != null}, project=${args.project}, build=${args.build}"
}

// yabv (downstream-build-cache) indexes downstream edges only at onStarted, before
// this late injection; re-index best-effort — absent/changed plugin is a no-op, and
// the cache self-heals on its next full rebuild since the cause is persisted.
@NonCPS
private void refreshDownstreamCache(Run downstreamBuild) {
  try {
    ClassLoader pluginClassLoader = Jenkins.get().getPluginManager().uberClassLoader
    Class buildCacheClass = pluginClassLoader.loadClass('com.axis.system.jenkins.plugins.downstream.cache.BuildCache')
    Object buildCache = buildCacheClass.getMethod('getCache').invoke(null)
    def updateCache = buildCacheClass.getDeclaredMethod('updateCache', Run)
    updateCache.setAccessible(true)
    updateCache.invoke(buildCache, downstreamBuild)
  } catch (Throwable ignored) {
    // plugin absent or internals changed — the injected cause still persists
  }
}
