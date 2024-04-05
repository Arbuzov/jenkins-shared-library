// doCleanGitWorkTree(): Clean Git repository work tree
//
// Usage: Call this function at places that are not immediately following a
// Git checkout, for example after a successful build of a stage. This way,
// this function will remove leftover files or directories and reset the
// modified files.
//
// Note: If you are immediately following a Git checkout, call the function
// requireCleanGitWorkTree() instead.

def call() {
	reportUsage(getClass().protectionDomain.codeSource.location.path)
	sh '''#!/bin/sh
# Clean Git repository work tree

# shellcheck shell=sh

# set -x
set -e

clean_git_work_tree()
{
	if { git rev-parse --is-inside-work-tree >/dev/null 2>&1 ; } ; then
		echo "debug: Cleaning Git work tree after SCM checkout (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
		## HACK: Update filesystem cache with git status --porcelain, so that git reset --hard touches only changed files
		# shellcheck disable=SC2034
		count_modified="$( git status --porcelain 2>/dev/null | wc -l )"
		# shellcheck disable=SC2034
		count_unclean="$( git clean -n -f -d -x | wc -l )"

		git reset --hard
		git clean -f -d -x --quiet
	else
		echo "WARNING: Working directory is NOT inside the Git work tree (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
		return 0
	fi
	return 0
}

clean_git_work_tree

'''
// end of shell function
}
