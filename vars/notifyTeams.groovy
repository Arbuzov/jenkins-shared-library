import groovy.json.JsonOutput

/**
 * Post a chat message to Microsoft Teams via the Graph API.
 *
 * Authenticates with delegated permissions via the OAuth2 Resource Owner
 * Password Credentials grant: messages are posted as a dedicated
 * service-account user, not as the AAD app itself, because Graph
 * requires Teamwork.Migrate.All for app-only chat posts (intended for
 * one-off chat history migrations only).
 *
 * Setup checklist (one-time):
 *   1. AAD app must have Delegated permission Chat.ReadWrite (or
 *      ChatMessage.Send), with admin consent granted.
 *   2. A dedicated service-account user must exist with no
 *      MFA / Conditional-Access blocks; the user must be a member of
 *      every chat the pipeline posts to.
 *   3. Jenkins username/password credentials:
 *        - app  : client_id + client_secret of the AAD app
 *        - user : UPN + password of the service-account user
 *
 * Example:
 *   notifyTeams(
 *     CHAT_ID: '19:....@unq.gbl.spaces',
 *     MESSAGE: '<p>Build <b>OK</b></p>'
 *   )
 *
 * @param CHAT_ID             Single chat ID or List of IDs.
 * @param MESSAGE             Message body. HTML by default, plain text if FORMAT='text'.
 * @param FORMAT              'html' (default) or 'text'.
 * @param TENANT_ID           Azure AD tenant ID. Default: 'b177c9bd-46b9-4bf4-86d3-eefbb46ba01a'.
 * @param APP_CREDENTIALS_ID  Jenkins ID of credential holding client_id/client_secret. Default: 'azure-teams-app'.
 * @param USER_CREDENTIALS_ID Jenkins ID of credential holding UPN/password. Default: 'virtual-user-azure'.
 * @param GRAPH_BASE_URL      Base URL for Graph API. Default: 'https://graph.microsoft.com/v1.0'.
 * @param DEBUG               Whether to log non-secret request/response detail.
 * @return List of created chatMessage IDs (one per CHAT_ID), or null if CHAT_ID was empty.
 */
def call(Map arg = [:]) {
  reportUsage(path: getClass().protectionDomain.codeSource.location.path)

  def chatIds = arg.CHAT_ID instanceof List ? arg.CHAT_ID : (arg.CHAT_ID ? [arg.CHAT_ID] : [])
  String message = arg.MESSAGE ?: ''
  String format = (arg.FORMAT ?: 'html').toString().toLowerCase()
  String tenantId = arg.TENANT_ID ?: 'b177c9bd-46b9-4bf4-86d3-eefbb46ba01a'
  String appCredsId = arg.APP_CREDENTIALS_ID ?: 'azure-teams-app'
  String userCredsId = arg.USER_CREDENTIALS_ID ?: 'virtual-user-azure'
  String graphBaseUrl = arg.GRAPH_BASE_URL ?: 'https://graph.microsoft.com/v1.0'
  Boolean debug = (arg.DEBUG instanceof Boolean) ? arg.DEBUG : "${arg.DEBUG ?: 'false'}".toBoolean()

  if (chatIds.isEmpty()) {
    if (debug) echo 'notifyTeams(): CHAT_ID is empty, skipping'
    return null
  }
  if (!message) {
    error 'notifyTeams(): MESSAGE must be supplied'
  }
  if (!(format in ['html', 'text'])) {
    error "notifyTeams(): FORMAT must be 'html' or 'text', got '${format}'"
  }

  def token = acquireGraphToken(tenantId, appCredsId, userCredsId, graphBaseUrl, debug)

  def messageIds = []
  chatIds.each { chatId ->
    messageIds << postChatMessage(graphBaseUrl, chatId.toString(), token, message, format, debug)
  }
  return messageIds
}

def acquireGraphToken(String tenantId, String appCredsId, String userCredsId, String graphBaseUrl, Boolean debug) {
  String tokenUrl = "https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token"
  String resource = graphBaseUrl.replaceFirst('(?i)/(v[0-9.]+|beta)/?$', '')
  String scope = "${resource}/.default"
  def token = null
  withCredentials([
    usernamePassword(
      credentialsId: appCredsId,
      usernameVariable: 'NT_CLIENT_ID',
      passwordVariable: 'NT_CLIENT_SECRET'
    ),
    usernamePassword(
      credentialsId: userCredsId,
      usernameVariable: 'NT_USER',
      passwordVariable: 'NT_PASSWORD'
    )
  ]) {
    if (debug) echo "notifyTeams(): requesting Graph token via ROPC (user=${env.NT_USER}, client_id=${env.NT_CLIENT_ID})"
    def resp = httpRequest(
      url: tokenUrl,
      httpMode: 'POST',
      contentType: 'APPLICATION_FORM',
      requestBody: 'client_id=' + URLEncoder.encode(env.NT_CLIENT_ID, 'UTF-8') +
                   '&client_secret=' + URLEncoder.encode(env.NT_CLIENT_SECRET, 'UTF-8') +
                   '&username=' + URLEncoder.encode(env.NT_USER, 'UTF-8') +
                   '&password=' + URLEncoder.encode(env.NT_PASSWORD, 'UTF-8') +
                   '&scope=' + URLEncoder.encode(scope, 'UTF-8') +
                   '&grant_type=password',
      validResponseCodes: '100:599',
      quiet: !debug,
      consoleLogResponseBody: false
    )
    if (resp.status < 200 || resp.status >= 300) {
      error "notifyTeams(): token request failed: HTTP ${resp.status} body=${resp.content}"
    }
    def parsed = readJSON(text: resp.content)
    token = parsed.access_token
    if (debug) echo "notifyTeams(): token acquired (expires_in=${parsed.expires_in}s, scope=${parsed.scope})"
  }
  return token
}

def postChatMessage(String graphBaseUrl, String chatId, String token, String message, String format, Boolean debug) {
  String url = "${graphBaseUrl}/chats/${chatId}/messages"
  if (debug) echo "notifyTeams(): POST ${url} (length=${message.length()}, format=${format})"
  def resp = httpRequest(
    url: url,
    httpMode: 'POST',
    contentType: 'APPLICATION_JSON',
    customHeaders: [[name: 'Authorization', value: "Bearer ${token}", maskValue: true]],
    requestBody: JsonOutput.toJson([body: [contentType: format, content: message]]),
    validResponseCodes: '100:599',
    quiet: !debug,
    consoleLogResponseBody: false
  )
  if (resp.status < 200 || resp.status >= 300) {
    error "notifyTeams(): POST chat message failed for chatId=${chatId}: HTTP ${resp.status} body=${resp.content}"
  }
  def parsed = readJSON(text: resp.content)
  if (debug) echo "notifyTeams(): chat message posted (id=${parsed.id}, chatId=${chatId})"
  return parsed.id
}
