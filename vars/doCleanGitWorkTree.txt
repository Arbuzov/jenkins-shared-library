# doCleanGitWorkTree

Clean Git repository work tree (including submodules)


## Description

This function will restore the modified files to their copies in Git
repository and remove generated files and directories. This includes the
work tree for the current project and submodules.


## Usage

```groovy
    stage('Prepare') {
        steps {
            // ...
            doCleanGitWorkTree()
            // ...
        }
    }
```

Call this function at places that are not immediately following a
Git checkout, for example after a successful build of a stage. This way,
this function will remove leftover files or directories and reset the
modified files.

Note:

* If you are immediately following a Git checkout, call the function
  `requireCleanGitWorkTree()` instead.
* This function will not initialize and check out all submodules
  recursively, because Jenkins has to do it. By default, Jenkins does not
  do it. Please configure the **Advanced sub-modules behaviours** for the
  **Organization Folder** that contains the GitLab project, in Jenkins.


## Parameters

No parameters.


## News

* 2022-03-11 - Initial revision, without support for Git submodules
* 2025-12-11 - Added support for Git submodules
