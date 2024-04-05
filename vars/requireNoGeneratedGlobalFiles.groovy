// requireNoGeneratedGlobalFiles(): Require that no generated global files are present in build environment
//
// Usage: Call this function at a place that is immediately after a Git
// checkout. This way, this function will require that there are no
// generated global files or directories. If there are generated global
// files or directories, this function will abort with an error.
//
// Note: Currently, this function checks for ${HOME}/.flexlmrc file, left
// by license checking library in CEVA compiler.
//

def call() {
  reportUsage(getClass().protectionDomain.codeSource.location.path)
	sh '''#!/bin/sh
# Require that no generated global files are present in build environment

# shellcheck shell=sh

# set -x
set -e

require_no_generated_global_files()
{
	if [ -e "${HOME}/.flexlmrc" ] ; then
		# checks for ${HOME}/.flexlmrc file, left by license
		# checking library in CEVA compiler

		echo "ERROR: file ${HOME}/.flexlmrc exists in a container before build (home ${HOME} on $( hostname ), stage: ${STAGE_NAME})"  >&2
		{ ls -lad "${HOME}/.flexlmrc" || true ; }    >&2
		return 1
	fi
	return 0
}

require_no_generated_global_files

'''
// end of shell function
}
