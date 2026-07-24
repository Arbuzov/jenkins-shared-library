/**
 * Restore the upstream Cause link that a GitLab push-webhook boundary destroys.
 *
 * When the test-orchestrator pipeline pushes an auto-bump commit into a build
 * repo (see manifest.groovy `bumpManifestTarget`), a GitLab push webhook starts
 * the build over that repo as a graph *root*: the orchestrator run that produced
 * the commit is missing from Jenkins' upstream/downstream graph. This reads the
 * triggering commit message, recovers the orchestrator's build coordinates from
 * it, and calls linkUpstreamBuild to re-inject the UpstreamCause — so the build
 * shows "Started by upstream project <test-orchestrator> #<N>".
 *
 * It is a safe no-op for any commit that carries no orchestrator coordinates
 * (ordinary developer pushes), so it can be called unconditionally right after
 * SCM checkout.
 *
 * See basic-memory "Jenkins causal tracing across the GitLab webhook boundary
 * (test-orchestrator -> build repo)".
 *
 * Upstream coordinates are recovered from the commit message, trying in order:
 *   1. explicit trailers (preferred, wording-independent):
 *          Jenkins-Upstream-Project: <full job name>
 *          Jenkins-Upstream-Build:   <number>
 *   2. a Jenkins build URL  https://.../job/a/job/b/<number>/  — the ${BUILD_URL}
 *      the orchestrator appends to its auto-bump commit message.
 *   3. the orchestrator's own auto-bump sentence:
 *          "Auto-bump verified versions from <full job name> #<number>"
 *
 * The commit message is resolved from, in order: args.message; the env var named
 * by args.messageEnvVar; env.GITLAB_OA_LAST_COMMIT_MESSAGE (set by the GitLab
 * branch-source plugin — same GITLAB_OA_ family as getGitBuildTriggerCommitID).
 * A `git log -1 --pretty=%B` read of HEAD is available only opt-in
 * (gitFallback: true) — see the note on resolveMessage.
 *
 * Args:
 *   - message       (String):  commit message to parse (overrides every source below).
 *   - messageEnvVar (String):  name of an env var holding the commit message.
 *   - gitFallback   (boolean): when no message env var is set, read HEAD's message
 *                              via git (default false — only safe when this build
 *                              was actually started by the push of that commit).
 *   - debug         (boolean): echo parsing / linking details (default true).
 *
 * Returns a map [project, build, source, linked] describing what was linked, or
 * null when the message carried no recognisable upstream coordinates.
 */
def call(Map args = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  boolean debug = args.containsKey('debug') ? args.debug : true

  String message = resolveMessage(args, debug)
  if (!message?.trim()) {
    if (debug) {
      echo '[linkUpstreamFromCommit] no commit message available; skipping'
    }
    return null
  }

  Map coords = parseUpstreamCoordinates(message)
  if (coords == null) {
    if (debug) {
      echo '[linkUpstreamFromCommit] no upstream coordinates in commit message; skipping'
    }
    return null
  }

  if (debug) {
    echo "[linkUpstreamFromCommit] found upstream ${coords.project} #${coords.build} (via ${coords.source})"
  }
  coords.linked = linkUpstreamBuild(project: coords.project, build: coords.build, debug: debug)
  return coords
}

/**
 * Resolve the commit message from the first available source. Never throws — a
 * failing `git` read degrades to null so linking is simply skipped.
 */
private String resolveMessage(Map args, boolean debug) {
  if (args.message != null) {
    return args.message.toString()
  }
  if (args.messageEnvVar) {
    def value = env.getAt(args.messageEnvVar.toString())
    if (value) {
      return value.toString()
    }
  }
  def pluginMessage = env.getAt('GITLAB_OA_LAST_COMMIT_MESSAGE')
  if (pluginMessage) {
    return pluginMessage.toString()
  }
  // Reading HEAD via git is only correct when THIS build was actually started by
  // the push of that commit. It is therefore opt-in: a build started by something
  // else (e.g. a GenericTrigger deletion webhook checking out master) would
  // otherwise mis-attribute the branch's latest auto-bump commit as its upstream.
  // Strict boolean: a Jenkins boolean parameter arrives as the STRING "false",
  // which is Groovy-truthy — so coerce rather than test the raw value.
  if (args.gitFallback?.toString()?.toBoolean()) {
    try {
      return sh(script: 'git log -1 --pretty=%B', returnStdout: true)?.trim()
    } catch (InterruptedException interrupted) {
      throw interrupted
    } catch (Throwable throwable) {
      // Never swallow a pipeline abort — let it propagate so the build stops.
      if (throwable.class.name == 'org.jenkinsci.plugins.workflow.steps.FlowInterruptedException') {
        throw throwable
      }
      if (debug) {
        echo "[linkUpstreamFromCommit] could not read commit message via git: ${throwable.message}"
      }
      return null
    }
  }
  return null
}

/**
 * Pure parser: commit message text -> [project, build (int), source] or null.
 * Deliberately free of Jenkins APIs so it is unit-testable via loadScript, and
 * self-contained (no pipeline steps) so it is CPS-safe without @NonCPS.
 */
Map parseUpstreamCoordinates(String message) {
  if (message == null) {
    return null
  }

  // 1. Explicit trailers — the robust, wording-independent contract.
  //    Capture the whole value (job full names may contain spaces), not just \S+.
  def projectTrailer = (message =~ /(?im)^\s*Jenkins-Upstream-Project:\s*(.+?)\s*$/)
  def buildTrailer = (message =~ /(?im)^\s*Jenkins-Upstream-Build:\s*(\d+)\s*$/)
  if (projectTrailer.find() && buildTrailer.find()) {
    return [project: projectTrailer.group(1).trim(), build: buildTrailer.group(1) as int, source: 'trailer']
  }

  // 2. A Jenkins build URL — .../job/<a>/job/<b>/.../<number>/ — machine-readable
  //    and already emitted by the orchestrator as ${BUILD_URL}. \S (non-whitespace)
  //    deliberately includes '/', so the lazy group spans the whole job path
  //    ("<a>/job/<b>/job/<c>") up to the trailing /<number>/.
  //    Scan every candidate: if the first /job/.../<n>/ is malformed and rejected
  //    by decodeJobUrlPath, a later valid BUILD_URL in the same message still wins.
  def buildUrl = (message =~ /(?i)\/job\/(\S+?)\/(\d+)\/?(?=\s|["'<)]|$)/)
  while (buildUrl.find()) {
    String jobPath = decodeJobUrlPath(buildUrl.group(1))
    if (jobPath) {
      return [project: jobPath, build: buildUrl.group(2) as int, source: 'buildUrl']
    }
  }

  // 3. The orchestrator's own auto-bump sentence (human text, anchored on its
  //    exact phrasing to avoid false positives on ordinary commits). The lazy
  //    (.+?) allows spaces in the job full name; it is bounded by " #<digits>".
  def sentence = (message =~ /(?im)Auto-bump verified versions from\s+(.+?)\s+#(\d+)\b/)
  if (sentence.find()) {
    return [project: sentence.group(1).trim(), build: sentence.group(2) as int, source: 'sentence']
  }

  return null
}

/**
 * Turn the "<a>/job/<b>/job/<c>" path captured between the first /job/ and the
 * trailing /<number>/ of a Jenkins build URL into a job full name "a/b/c".
 * Segments alternate real-name / literal "job"; the real names are at even
 * indices. URL-decodes each segment (folder names may be percent-encoded).
 */
String decodeJobUrlPath(String rawPath) {
  if (!rawPath) {
    return null
  }
  def parts = rawPath.split('/')
  List<String> segments = []
  try {
    for (int index = 0; index < parts.length; index++) {
      String segment = parts[index]
      if (index % 2 == 0) {
        if (segment) {
          segments << java.net.URLDecoder.decode(segment, 'UTF-8')
        }
      } else if (segment != 'job') {
        // A genuine Jenkins job URL alternates "<name>/job/<name>/job/...": every
        // odd segment must be the literal "job". Anything else is an incidental
        // "/job/<x>/<n>/" string, not a build URL — reject so the caller falls
        // through instead of inventing a bogus project name.
        return null
      }
    }
  } catch (IllegalArgumentException ignored) {
    // Malformed percent-escape — treat as "not a build URL" so this step stays a
    // safe no-op for ordinary commits instead of failing the build.
    return null
  }
  return segments.isEmpty() ? null : segments.join('/')
}
