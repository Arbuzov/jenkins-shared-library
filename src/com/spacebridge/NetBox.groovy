package com.spacebridge

import groovy.json.JsonSlurper

class NetBox {

  // Private helper methods
  private static String getTokenForCredentials(String credentialsId) {
    def token = Credentials.getTokenSafe(credentialsId, false)
    if (!token) {
      println "Netbox token not found for credentials ID: ${credentialsId}"
    }
    return token
  }

  private static URLConnection createConnection(String url, String token) {
    def connection = new URL(url).openConnection()
    connection.setRequestMethod('GET')
    connection.setRequestProperty('Authorization', "Token ${token}")
    connection.setRequestProperty('Accept', 'application/json')
    connection.setRequestProperty('Content-Type', 'application/json')
    return connection
  }

  private static List<Map> processResponse(URLConnection connection, String entityType) {
    if (connection.responseCode == 200) {
      def response = connection.inputStream.text
      def jsonSlurper = new JsonSlurper()
      def jsonResponse = jsonSlurper.parseText(response)
      return jsonResponse.results ?: []
    } else {
      println "Failed to fetch ${entityType} from Netbox. Response code: ${connection.responseCode}"
      return []
    }
  }

  private static List<Map> makeNetboxRequest(String baseUrl, String credentialsId, String endpoint, String entityType, Map<String, String> queryParams = [:]) {
    try {
      def token = getTokenForCredentials(credentialsId)
      if (!token) {
        return []
      }

      def url = "${baseUrl.replaceAll('/+$', '')}${endpoint}"
      if (queryParams && queryParams.size() > 0) {
        def queryString = queryParams.collect { k, v -> "${k}=${v}" }.join('&')
        url += "?${queryString}"
      }

      def connection = createConnection(url, token)
      return processResponse(connection, entityType)

    } catch (Exception e) {
      println "Error fetching ${entityType} from Netbox: ${e.message}"
      return []
    }
  }

  // Public API methods
  static List<Map> getTenants(Map arg = [:]) {
    def netboxUrl = arg.netboxUrl ?: 'https://netbox.sandbox.asatnet.net/netbox'
    def credentialsId = arg.credentialsId ?: 'netbox'

    return makeNetboxRequest(netboxUrl, credentialsId, '/api/tenancy/tenants/', 'tenants')
  }

  static List<Map> getRacks(Map arg = [:]) {
    def netboxUrl = arg.netboxUrl ?: 'https://netbox.sandbox.asatnet.net/netbox'
    def credentialsId = arg.credentialsId ?: 'netbox'

    return makeNetboxRequest(netboxUrl, credentialsId, '/api/dcim/racks/', 'racks')
  }

  static List<Map> getDevices(Map arg = [:]) {
    def netboxUrl = arg.netboxUrl ?: 'https://netbox.sandbox.asatnet.net/netbox'
    def credentialsId = arg.credentialsId ?: 'netbox'
    def role = arg.role
    def tenant = arg.tenant

    def queryParams = [:]
    if (role) {
      queryParams['role'] = role.toString()
    }
    if (tenant) {
      queryParams['tenant'] = tenant.toString()
    }

    return makeNetboxRequest(netboxUrl, credentialsId, '/api/dcim/devices/', 'devices', queryParams)
  }
}
