# requireCleanGitWorkTree

Require that Git repository work tree is clean (including submodules)


## Description

This function will ensure that the Git work tree does not contain
modified files or generated files or directories. If this is not true,
the function will return an error to abort the pipeline. This check
includes the work tree for the current project and submodules.


## Usage

```groovy
    stage('Prepare') {
        steps {
            // ...
            requireCleanGitWorkTree()
            // ...
        }
    }
```

Call this function at a place that is immediately after a Git checkout.
This way, this function will require that there are no leftover files or
directories. If there are leftover files or directories or modified
files, this function will abort with an error.

Note:

* If you are not immediately following a Git checkout, call the
  function `doCleanGitWorkTree()` instead.
* This function will not initialize and check out all submodules
  recursively, because Jenkins has to do it. By default, Jenkins does not
  do it. Please configure the **Advanced sub-modules behaviours** for the
  **Organization Folder** that contains the GitLab project, in Jenkins.


## Parameters

No parameters.


## News

* 2022-03-11 - Initial revision, without support for Git submodules
* 2025-12-11 - Added support for Git submodules
