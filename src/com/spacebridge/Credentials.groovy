package com.spacebridge

import com.cloudbees.plugins.credentials.CredentialsProvider
import org.jenkinsci.plugins.plaincredentials.StringCredentials
import jenkins.model.Jenkins

class Credentials {

  /**
   * Retrieves token from Jenkins credentials store by ID
   * @param credentialsId Credentials ID in Jenkins
   * @return token as string or null if not found
   */
  static String getToken(String credentialsId) {
    if (!credentialsId) {
      throw new IllegalArgumentException("Credentials ID is required")
    }

    try {
      def creds = CredentialsProvider.lookupCredentials(
          StringCredentials.class,
          Jenkins.instance,
          null,
          null
      )

      for (c in creds) {
        if (c.id == credentialsId) {
          return c.secret.toString()
        }
      }

      return null
    } catch (Exception e) {
      throw new RuntimeException("Failed to retrieve credentials for ID: ${credentialsId}", e)
    }
  }

  /**
   * Checks if credentials exist by ID
   * @param credentialsId Credentials ID
   * @return true if credentials found, false otherwise
   */
  static boolean credentialsExists(String credentialsId) {
    return getToken(credentialsId) != null
  }

  /**
   * Retrieves token with existence check
   * @param credentialsId Credentials ID
   * @param throwOnMissing throw exception if token not found
   * @return token or null/exception depending on throwOnMissing parameter
   */
  static String getTokenSafe(String credentialsId, boolean throwOnMissing = true) {
    def token = getToken(credentialsId)

    if (!token && throwOnMissing) {
      throw new IllegalStateException("Token not found for credentials ID: ${credentialsId}")
    }

    return token
  }
}
