// requireCleanGitWorkTree(): Require that Git repository work tree is clean
//
// Usage: Call this function at a place that is immediately after a Git
// checkout. This way, this function will require that there are no
// leftover files or directories. If there are leftover files or
// directories or modified files, this function will abort with an error.
//
// Note: If you are not immediately following a Git checkout, call the
// function doCleanGitWorkTree() instead.

def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
	sh '''#!/bin/sh
# Require that Git repository work tree is clean

# shellcheck shell=sh

# set -x
set -e

require_git_clean_work_tree()
{
	if ! git version >/dev/null ; then
		# NOTE: git binary must be present in container or on the host
		echo "ERROR: git program is not working (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
		exit 1
	fi
	if { git rev-parse --is-inside-work-tree >/dev/null 2>&1 ; } ; then
		echo "debug: Checking that work tree is clean after SCM checkout (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
		## HACK: Update filesystem cache with git status --porcelain, so that git reset --hard touches only changed files
		count_modified="$( git status --porcelain 2>/dev/null | wc -l )"
		count_unclean="$( git clean -n -f -d -x | wc -l )"

		if [ "${count_unclean}" = "0" ] && [ "${count_modified}" = "0" ] ; then
			true
			echo "debug: Work tree is clean (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
			return 0
		else
			git status --porcelain
			git clean -n -f -d -x
			echo "ERROR: Work tree is NOT clean after SCM checkout (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
			return 1
		fi
	else
		echo "WARNING: Working directory is NOT inside the Git work tree (work tree ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
		return 0
	fi
	return 0
}

require_git_clean_work_tree

'''
// end of shell function
}
