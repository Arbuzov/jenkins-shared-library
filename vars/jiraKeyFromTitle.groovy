
/**
 * Extract the Jira issue key from a MR title.
 * For example, given the MR title "A-123: Bugs fixed", the function returns "A-123".
 * Correctly handles the `Draft:` prefix.
 *
 * @param title The MR title.
 * @param projectKey Optional Jira project key (e.g. 'NMSNG'); when given, only keys of that project are matched.
 * @return Jira issue key, uppercased (null if no key found).
 */
def call(String title, String projectKey = null) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  title = (title ?: '').trim()
  if (title.startsWith('Draft: ')) {
    title = title.substring(7).trim()
  }
  def key = projectKey ? java.util.regex.Pattern.quote(projectKey.toUpperCase()) : /[A-Z0-9]+/
  // The lookbehind stops projectKey from matching as a suffix of a longer key
  // (e.g. 'NMSNG' inside 'SUBNMSNG-45')
  def matcher = (title.toUpperCase() =~ /(?<![A-Z0-9])(${key}-\d+):/)
  if (matcher) {
    return matcher[0][1]
  }
  return null
}
