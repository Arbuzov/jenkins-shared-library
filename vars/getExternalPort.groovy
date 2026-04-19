import org.apache.commons.io.FilenameUtils;

def call(String nodeId, Integer internalPort, String crId) {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
  def port = internalPort;
  def remote = [:]
  withCredentials([
    sshUserPrivateKey(
    credentialsId: crId,
    keyFileVariable: 'SSH_KEY',
    usernameVariable: 'USER'
  )]) {
    remote.name = 'Docker Host'
    remote.host = env.NODE_NAME
    remote.user = USER
    remote.identityFile = SSH_KEY
    remote.allowAnyHosts = true
    port = sshCommand(
      remote: remote,
      sudo: true,
      command: "docker inspect -f '{{ (index (index .NetworkSettings.Ports \"${internalPort}/tcp\") 0).HostPort }}' ${nodeId}"
    )
  }
  return port;
}
