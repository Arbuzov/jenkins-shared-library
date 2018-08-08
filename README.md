# Builds and view description for Jenkins

## Project structure

 * [firmware](firmware) - builds and views for IDU 7000
 * [rt-router](rt-router) - builds and views for the router firmware
 * [vars](vars) - shared librarys

###General library of the Jenkins scripts used in several projects

getProjectRepos.groovy returns list of the git forks of one project
works due to unrestricted access to the cgit interface 

usage:
```
String projectName = 'meta-awl.git';

String webRootUrl = 'https://git.advantech.ca/cgit.cgi'

String gitRootUrl = 'ssh://git7@192.168.21.19'

def projectRepos = getProjectRepos(projectName, webRootUrl, gitRootUrl); 
```
## How to start using this repo

 * create a pipeline using Jenkinsfile from this project
 * launch the pipeline

## Required Jenkins plugins

* [Git plugin](https://wiki.jenkins.io/display/JENKINS/Git+Plugin)
* [Job DSL plugin](https://github.com/jenkinsci/job-dsl-plugin)