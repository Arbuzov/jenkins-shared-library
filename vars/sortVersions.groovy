/**
 * Keep the build versions of a list and order them newest first.
 *
 * A version is kept when its whole string matches `pattern`. The kept versions
 * are ordered by X, Y, Z and the build number N read from a leading
 * 'X.Y.Z' or 'X.Y.Z-bN' (so '2.0.3-b70-offline' has N = 70), all numerically and
 * descending. A version without a build number ranks below '-b0' of the same
 * X.Y.Z; a version without an X.Y.Z prefix (only a permissive pattern such as
 * '.*' keeps one) comes after all others. Ties are broken by plain string
 * order, ascending.
 *
 * Args (Map):
 *  - versions:  Collection of versions, e.g. Nexus versions or Docker tags
 *               (required). Entries are compared by toString(); null entries
 *               are skipped and duplicates removed.
 *  - pattern:   regex the whole version must match
 *               (default: '\d+\.\d+\.\d+(-b\d+)?', X.Y.Z with an optional -bN)
 *
 * Returns List<String> - a new ArrayList, newest first; the caller's collection
 * is left unchanged.
 *
 * Fails through error(...) when 'versions' is missing or is not a Collection.
 */
def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (args.versions == null) {
    error("sortVersions(): 'versions' is required: pass the Collection of versions to sort.")
  }
  if (!(args.versions instanceof Collection)) {
    error("sortVersions(): 'versions' must be a Collection. Got ${args.versions.getClass().name}.")
  }
  String pattern = args.pattern ?: /\d+\.\d+\.\d+(-b\d+)?/

  return newestFirst(args.versions as Collection, pattern)
}

@NonCPS
List newestFirst(Collection versions, String pattern) {
  def kept = new LinkedHashSet<String>()
  versions.each { version ->
    if (version != null && version.toString() ==~ pattern) {
      kept << version.toString()
    }
  }
  // Absent build number is -1, so 'X.Y.Z' ranks below 'X.Y.Z-b0'; null means no X.Y.Z prefix
  Map keys = kept.collectEntries { version ->
    def matcher = version =~ /^(\d+)\.(\d+)\.(\d+)(?:-b(\d+))?/
    def key = matcher.find() ? (1..4).collect { group ->
      matcher.group(group) != null ? new BigInteger(matcher.group(group)) : BigInteger.valueOf(-1)
    } : null
    [(version): key]
  }
  def sorted = new ArrayList<String>(kept)
  sorted.sort { left, right ->
    def leftKey = keys[left]
    def rightKey = keys[right]
    if (leftKey == null || rightKey == null) {
      if (leftKey != rightKey) {
        return leftKey == null ? 1 : -1
      }
      return left <=> right
    }
    for (int index = 0; index < 4; index++) {
      if (leftKey[index] != rightKey[index]) {
        return rightKey[index] <=> leftKey[index]
      }
    }
    return left <=> right
  }
  return sorted
}
