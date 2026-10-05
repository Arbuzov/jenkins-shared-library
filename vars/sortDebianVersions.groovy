/** Sort Debian versions newest first, including epochs, revisions and '~'. */
def call(Map args = [:]) {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)
    if (!(args.versions instanceof Collection)) {
        error("sortDebianVersions: 'versions' must be a Collection.")
    }
    return newestFirst(args.versions)
}

@NonCPS
List newestFirst(Collection versions) {
    List<String> result = versions.findAll { it != null }.collect { it.toString() }.unique()
    result.sort { a, b -> compareVersion(b, a) ?: (a <=> b) }
    return result
}

@NonCPS
List splitVersion(String version) {
    int colon = version.indexOf(':')
    BigInteger epoch = colon < 0 ? BigInteger.ZERO : new BigInteger(version.substring(0, colon))
    String rest = colon < 0 ? version : version.substring(colon + 1)
    int dash = rest.lastIndexOf('-')
    return [epoch, dash < 0 ? rest : rest.substring(0, dash), dash < 0 ? '0' : rest.substring(dash + 1)]
}

@NonCPS
int compareVersion(String a, String b) {
    def left = splitVersion(a)
    def right = splitVersion(b)
    return (left[0] <=> right[0]) ?: comparePart(left[1], right[1]) ?: comparePart(left[2], right[2])
}

@NonCPS
boolean digit(int c) { c >= 48 && c <= 57 }

@NonCPS
int order(int c) {
    if (c == 126) { return -1 } // tilde sorts even before the end of a part
    if (c == 0 || digit(c)) { return 0 }
    if ((c >= 65 && c <= 90) || (c >= 97 && c <= 122)) { return c }
    return c + 256
}

@NonCPS
int comparePart(String a, String b) {
    int i = 0
    int j = 0
    while (i < a.length() || j < b.length()) {
        while ((i < a.length() && !digit(a.charAt(i) as int)) ||
               (j < b.length() && !digit(b.charAt(j) as int))) {
            int left = order(i < a.length() ? a.charAt(i) as int : 0)
            int right = order(j < b.length() ? b.charAt(j) as int : 0)
            if (left != right) { return left <=> right }
            if (i < a.length()) { i++ }
            if (j < b.length()) { j++ }
        }
        while (i < a.length() && a.charAt(i) == '0' as char) { i++ }
        while (j < b.length() && b.charAt(j) == '0' as char) { j++ }
        int firstDifference = 0
        while (i < a.length() && j < b.length() && digit(a.charAt(i) as int) && digit(b.charAt(j) as int)) {
            if (!firstDifference) { firstDifference = (a.charAt(i) as int) - (b.charAt(j) as int) }
            i++
            j++
        }
        if (i < a.length() && digit(a.charAt(i) as int)) { return 1 }
        if (j < b.length() && digit(b.charAt(j) as int)) { return -1 }
        if (firstDifference) { return firstDifference <=> 0 }
    }
    return 0
}
