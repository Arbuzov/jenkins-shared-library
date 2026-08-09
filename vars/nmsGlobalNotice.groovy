import groovy.json.JsonOutput

def graphql(String nmsUrl, String query, Map variables, String token = null, Boolean debug = false, Boolean logResponseBody = true) {
  def headers = []
  if (token) {
    headers.add([name: 'AsatAuthentication', value: token, maskValue: true])
  }
  def response = httpRequest(
    url: "${nmsUrl.replaceAll(/\/+$/, '')}/api/graphql",
    httpMode: 'POST',
    contentType: 'APPLICATION_JSON',
    acceptType: 'APPLICATION_JSON',
    requestBody: JsonOutput.toJson([query: query, variables: variables]),
    customHeaders: headers,
    ignoreSslErrors: true,
    timeout: 15,
    consoleLogResponseBody: debug && logResponseBody,
    quiet: !debug,
  )
  return readJSON(text: response.getContent())
}

def applyNotice(Map arg, Boolean debug, String username, String password) {
  def login = graphql(
    arg.nmsUrl,
    'mutation Login($username: String!, $password: String!) { login(username: $username, password: $password) { access } }',
    [username: username, password: password],
    null,
    debug,
    false, // the response body carries the access token — never log it, even in debug
  )
  def token = login?.data?.login?.access
  if (!token) {
    throw new Exception("NMS login as '${username}' failed: ${login?.errors ?: login}")
  }

  def result
  if (arg.action == 'set') {
    result = graphql(
      arg.nmsUrl,
      'mutation SetGlobalSetting($key: String!, $value: String!) { setGlobalSetting(key: $key, value: $value) { key } }',
      [key: 'global_notice', value: JsonOutput.toJson([text: arg.text, author: arg.author ?: username])],
      token,
      debug,
    )
  } else {
    result = graphql(
      arg.nmsUrl,
      'mutation DeleteGlobalSetting($key: String!) { deleteGlobalSetting(key: $key) { key } }',
      [key: 'global_notice'],
      token,
      debug,
    )
  }
  if (result?.errors) {
    // Removing an absent notice is fine — the desired state is reached
    if (arg.action == 'remove' && result.errors.toString().toLowerCase().contains('not found')) {
      if (debug) {
        echo "nmsGlobalNotice(): notice was not set — nothing to remove"
      }
      return
    }
    throw new Exception("NMS API error: ${result.errors}")
  }
}

/**
 * Set or remove the NMS global notice — the modal every logged-in NMS user sees
 * (within about a minute; the frontend polls the notice every 60 s). Implemented
 * through the NMS GraphQL API as the global setting 'global_notice'.
 *
 * Intended use: mark a test HUB as "under automated test" for the duration of a
 * pipeline run, and clear the notice from post { always }.
 *
 * @param arg.nmsUrl Base NMS URL, e.g. 'https://192.168.71.120/nms-ng/' (required)
 * @param arg.action 'set' or 'remove' (required)
 * @param arg.text Notice text (required for 'set')
 * @param arg.author Name shown to users as "Created by: ..." (optional; defaults
 *        to the username the step authenticates as)
 * @param arg.credentialsId usernamePassword credentials of an NMS admin
 *        (optional; defaults to superadmin/superadmin — the test-HUB default)
 * @param arg.failOnError Throw on failure (default: false — the notice is a courtesy;
 *        'remove' is typically called from post { always } and must not mask the
 *        build result)
 * @param arg.debug Log requests and responses (default: false)
 * @return Boolean true on success
 */
def call(Map arg = [:]) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)

  def debug = arg.debug ?: false

  if (!arg.nmsUrl) {
    error("nmsGlobalNotice(): 'nmsUrl' is required.")
  }
  if (!(arg.action in ['set', 'remove'])) {
    error("nmsGlobalNotice(): 'action' must be 'set' or 'remove'.")
  }
  if (arg.action == 'set' && !arg.text) {
    error("nmsGlobalNotice(): 'text' is required for action 'set'.")
  }

  try {
    if (arg.credentialsId) {
      withCredentials([usernamePassword(
        credentialsId: arg.credentialsId,
        usernameVariable: 'NMS_NOTICE_USER',
        passwordVariable: 'NMS_NOTICE_PASSWORD'
      )]) {
        applyNotice(arg, debug, NMS_NOTICE_USER, NMS_NOTICE_PASSWORD)
      }
    } else {
      applyNotice(arg, debug, 'superadmin', 'superadmin')
    }
    if (debug) {
      echo "nmsGlobalNotice(): '${arg.action}' done on ${arg.nmsUrl}"
    }
    return true

  } catch (Exception e) {
    def message = "nmsGlobalNotice(): Failed to ${arg.action} the global notice on ${arg.nmsUrl}: ${e?.message ?: 'unknown error'}"
    if (arg.failOnError) {
      throw new Exception(message)
    }
    echo message
    return false
  }
}
