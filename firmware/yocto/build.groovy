import hudson.model.*;

hudson.FilePath workspace = (new hudson.FilePath(new File("${WORKSPACE}"))).child('images');

String hostLabel = 'parent-docker';

def remoteConfig = (Hudson.getInstance().getJob("${JOB_NAME}").getSCMs())[0].getUserRemoteConfigs().get(0);

String gitUrl = remoteConfig.getUrl();

String repoCreds = remoteConfig.getCredentialsId();