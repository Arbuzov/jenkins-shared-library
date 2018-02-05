General library of the Jenkins scripts used in several projects
===============================================================

getProjectRepos.groovy returns list of the git forks of one project
works due to unrestricted access to the cgit interface 

usage:
```
String projectName = 'meta-awl.git';

String webRootUrl = 'https://git.advantech.ca/cgit.cgi'

String gitRootUrl = 'ssh://git7@192.168.21.19'

def projectRepos = getProjectRepos(projectName, webRootUrl, gitRootUrl); 
```