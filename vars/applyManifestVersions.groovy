import java.util.regex.Pattern

/**
 * Set key values in a Manifest text, keeping its formatting.
 *
 * Format-agnostic: a key is found on `key: value` (YAML) and on `key=value`
 * (properties) lines, at any indentation. The line keeps its indentation, its
 * separator, the whitespace after its value and its CR/LF terminator; only the
 * value is replaced, and an empty YAML value ('key:') gets a space before the new
 * one. A key with no line is left out, and every line that
 * carries the key is set, so a repeated key stays consistent.
 *
 * Args (Map):
 *  - text:      the Manifest content (required; '' is allowed)
 *  - versions:  key -> new value (default: [:]). A null, empty or multi-line
 *               value is skipped: it would empty or split the line
 *
 * Returns Map [text: <new text>, changed: [key: value]] - changed lists only
 * the keys whose text really changed, with the value written as a String.
 *
 * Fails through error(...) when text is missing or not a String, when
 * versions is not a Map, and when versions has a null or blank key.
 */
def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (args.text == null) {
    error("applyManifestVersions(): 'text' is required: the Manifest content as a String ('' is allowed).")
  }
  if (!(args.text instanceof CharSequence)) {
    error("applyManifestVersions(): 'text' must be a String. Got ${args.text.getClass().name}.")
  }
  def versions = args.versions != null ? args.versions : [:]
  if (!(versions instanceof Map)) {
    error("applyManifestVersions(): 'versions' must be a Map of key -> value. Got ${versions.getClass().name}.")
  }
  if (hasBlankKey(versions)) {
    error("applyManifestVersions(): 'versions' has a null or blank key. Name every key as it appears in the Manifest.")
  }

  return applyVersions(args.text.toString(), versions)
}

@NonCPS
boolean hasBlankKey(Map versions) {
  return versions.keySet().any { key -> key == null || key.toString().trim().isEmpty() }
}

// @NonCPS: the regex Matcher is not serializable, so it must stay off the CPS thread.
@NonCPS
Map applyVersions(String text, Map versions) {
  Map changed = [:]
  String result = text
  versions.each { key, value ->
    if (value == null) {
      return
    }
    String newValue = value.toString()
    // \R is any line break, so a value can never split the line
    if (newValue.isEmpty() || newValue =~ /\R/) {
      return
    }
    // [ \t]* rather than \s*: \s also matches a line break, so an empty value
    // ('ncc=') would take the next line as its value.
    Pattern line = Pattern.compile('(?m)^([ \t]*' + Pattern.quote(key.toString()) + '[ \t]*[:=][ \t]*)(.*?)([ \t]*)$')
    // An empty YAML value ('key:') needs a space before the new one to stay a mapping entry
    String updated = result.replaceAll(line) { all, prefix, oldValue, trailing ->
      prefix + (oldValue.isEmpty() && prefix.endsWith(':') ? ' ' : '') + newValue + trailing
    }
    if (updated != result) {
      changed[key] = newValue
      result = updated
    }
  }
  return [text: result, changed: changed]
}
