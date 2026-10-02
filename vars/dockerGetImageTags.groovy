/**
 * List the tags of an image in a Docker registry (v2 API).
 *
 * Wraps GET https://<registryHost>/v2/<image>/tags/list and follows the
 * registry's Link: <...>; rel="next" header, so every page is returned
 * instead of only the first one.
 *
 * Args (Map):
 *  - image:          image name inside the registry, e.g. 'spacebridge/nms-ng-backend' (required)
 *  - registryHost:   registry host name (default: 'registry.nexus.asatnet.net')
 *  - credentialsId:  Jenkins username/password credentials ID with read access,
 *                    passed as httpRequest authentication (default: 'jira-username-password')
 *  - maxPages:       page walk limit, >= 1 (default: 100)
 *  - debug:          log request URLs and response bodies (default: false)
 *
 * Returns List<String> - the tags in registry order, all pages joined; [] when
 * the registry answers 200 with no tags.
 *
 * Fails through error(...) when image is missing or empty, when maxPages < 1,
 * when the registry answers >= 400 (a missing image answers 404 NAME_UNKNOWN),
 * and when a next page is still announced after maxPages pages.
 */
/**
 * The target of the rel="next" entry of a Link header, or null. The header name
 * is matched case-insensitively; its value may be a List or a String.
 */
@NonCPS
String nextPageLink(Map headers) {
  if (!headers) {
    return null
  }
  def entry = headers.find { name, value -> name?.toString()?.toLowerCase() == 'link' }
  if (!entry) {
    return null
  }
  String linkHeader = (entry.value instanceof List) ? entry.value.join(', ') : entry.value?.toString()
  if (!linkHeader) {
    return null
  }
  // RFC 8288: a link value is <target> followed by ;-separated parameters in any order, and rel may list several relations
  def links = linkHeader =~ /<([^>]+)>([^,]*)/
  while (links.find()) {
    def relation = links.group(2).split(';')*.trim().find { it.toLowerCase().startsWith('rel=') }
    if (relation && relation.substring(4).replace('"', '').toLowerCase().split(/\s+/).contains('next')) {
      return links.group(1)
    }
  }
  return null
}

def call(Map args = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  if (!args.image) {
    error("dockerGetImageTags(): 'image' is required, e.g. 'spacebridge/nms-ng-backend'.")
  }
  String image = args.image.toString()
  String registryHost = args.registryHost ?: 'registry.nexus.asatnet.net'
  String credentialsId = args.credentialsId ?: 'jira-username-password'
  Boolean debug = args.debug ?: false

  int maxPages = (args.maxPages != null ? args.maxPages : 100) as Integer
  if (maxPages < 1) {
    error("dockerGetImageTags(): 'maxPages' must be >= 1. Got ${maxPages}.")
  }

  String registryBase = "https://${registryHost}"
  String firstUrl = "${registryBase}/v2/${image}/tags/list"
  String pageUrl = firstUrl
  int pagesRead = 0
  def tags = []
  while (pageUrl) {
    if (pagesRead == maxPages) {
      error("dockerGetImageTags(): more than ${maxPages} pages for ${firstUrl}. Raise 'maxPages'.")
    }
    if (debug) {
      echo("[dockerGetImageTags] GET ${pageUrl}")
    }
    def response = httpRequest(
      url: pageUrl,
      httpMode: 'GET',
      contentType: 'APPLICATION_JSON',
      acceptType: 'APPLICATION_JSON',
      authentication: credentialsId,
      validResponseCodes: '100:599',
      consoleLogResponseBody: debug,
      quiet: !debug,
    )
    int status = (response?.status ?: 0) as Integer
    if (status >= 400) {
      error("dockerGetImageTags(): GET ${pageUrl} answered ${status}. " +
            "Check the image '${image}' in ${registryHost} and the credentials '${credentialsId}'.")
    }
    pagesRead++
    def page = readJSON(text: response?.content, returnPojo: true)
    if (page?.tags) {
      tags.addAll(page.tags)
    }
    String link = nextPageLink(response?.headers ?: [:])
    if (!link) {
      pageUrl = null
    } else if (link.toLowerCase().startsWith('http://') || link.toLowerCase().startsWith('https://')) {
      pageUrl = link
    } else {
      pageUrl = registryBase + link
    }
  }
  return tags
}
