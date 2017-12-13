import hudson.model.*;
import java.util.regex.*

/*********************************************/

String hostLabel = 'jslave-step2';

String genericBranch = 'master';

String projectName = 'sit2.git';

String webRootUrl = 'https://git.advantech.ca/cgit.cgi'

String gitRootUrl = 'ssh://git7@192.168.21.19'

/*********************************************/

def projectRepos = getProjectRepos(projectName, webRootUrl, gitRootUrl);

println(projectRepos);

// create a separate view for all jobs generated here
listView("kernel-7000") {
    description('All builds of kernel-7000')
    filterBuildQueue()
    filterExecutors()
    jobs {
        name("${JOB_BASE_NAME}")
        regex(/kernel-7000.*/)
    }
    columns {
        status()
        weather()
        name()
        buildButton()
    }
}