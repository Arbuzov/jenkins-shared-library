#!/bin/sh
# Jenkins shared library shell functions
# NOTE: This file is sourced by Jenkins pipeline, not executed

# shellcheck shell=dash


# Initialize SOURCE_DATE_EPOCH variable with committer date of current commit

pipelinelib_override_source_date_epoch() {
	local _tmp_date_epoch=
	_tmp_date_epoch="$( LC_ALL=C TZ=UTC git log -1 --format="tformat:%ct" HEAD )" ;
	_tmp_date_epoch="$( LC_ALL=C TZ=UTC date -u -d "@${_tmp_date_epoch}" '+%s' )" ;
	if [ -n "${_tmp_date_epoch:-}" ] ; then
		echo "    debug: Override SOURCE_DATE_EPOCH with ${_tmp_date_epoch}"    >&2
		SOURCE_DATE_EPOCH="${_tmp_date_epoch}"
		export SOURCE_DATE_EPOCH
	fi
	unset _tmp_date_epoch
}


# Add Change request ID to BUILD_NUMBER variable

pipelinelib_override_build_number() {
	if [ -n "${CHANGE_REQUEST_BUILD_NUMBER:-}" ] ; then
		echo "    debug: Override BUILD_NUMBER with ${CHANGE_REQUEST_BUILD_NUMBER}"    >&2
		BUILD_NUMBER="${CHANGE_REQUEST_BUILD_NUMBER}"
		export BUILD_NUMBER
	fi
	return 0
}


# Unset BUILD_TAG variable for reproducible builds (of U-Boot)

pipelinelib_override_build_tag() {
	echo "WARNING: unsetting BUILD_TAG for reproducible builds"    >&2
	unset BUILD_TAG
}


# Require that no ${HOME}/.flexlmrc is present for SDK-S8000 build

pipelinelib_require_no_home_config_flexlmrc() {
	if [ -e "${HOME}/.flexlmrc" ] ; then
		echo "    ERROR: file ${HOME}/.flexlmrc exists in a container before build"  >&2
		{ ls -lad "${HOME}/.flexlmrc" || true ; }    >&2
		exit 1
	fi
	return 0
}


# Generate checksums file for files under specific subdirectory

pipelinelib_generate_checksums_file() {
	local _chk_subdir="${1:-}"
	local _chk_find_root="${2:-}"
	local _chk_checksum_tool="${3:-}"
	local _chk_output_file="${4:-}"
	local _chk_tmp_file=

	rm -v -f "${_chk_output_file}"

	_chk_tmp_file="$( mktemp /tmp/tmp_gen_checksums.XXXXXXXXXX )"
	if [ -z "${_chk_tmp_file}" ] ; then
		echo "ERROR: could not create temporary file"  >&2
		return 1
	fi

	(
		cd "${_chk_subdir}" ;
		set +e ;
		# NOTE: Here, we process symbolic links to files, only for Yocto SDK output
		find ${_chk_find_root} -mindepth 1 ! -type d ! -xtype d ! -xtype l | sed -e 's,^[.]/,,'  \
			| LC_ALL=C sort | xargs -r "${_chk_checksum_tool}" -b >"${_chk_tmp_file}"
		set -e ;
	)

	install -m 0644 "${_chk_tmp_file}" "${_chk_output_file}"
	rm -f "${_chk_tmp_file}"
	return 0
}


# Debug print ccache statistics - part A

pipelinelib_debug_print_ccache_stats_A() {
	rm -f .tmp_ccache_stats_A.log .tmp_ccache_stats_B.log
	ccache -s -p | tee .tmp_ccache_stats_A.log
}


# Debug print ccache statistics - part B

pipelinelib_debug_print_ccache_stats_B() {
	ccache -s -p | tee .tmp_ccache_stats_B.log
	{ diff -u .tmp_ccache_stats_A.log .tmp_ccache_stats_B.log || true ; }
}


# Debug print ccache statistics for private Buildroot ccache - part A

pipelinelib_debug_print_buildroot_ccache_stats_A() {
	rm -f .tmp_ccache_stats_A.log .tmp_ccache_stats_B.log
	CCACHE_DIR="${HOME}/.buildroot-ccache" ccache -s -p | tee .tmp_ccache_stats_A.log
}


# Debug print ccache statistics for private Buildroot ccache - part B

pipelinelib_debug_print_buildroot_ccache_stats_B() {
	CCACHE_DIR="${HOME}/.buildroot-ccache" ccache -s -p | tee .tmp_ccache_stats_B.log
	{ diff -u .tmp_ccache_stats_A.log .tmp_ccache_stats_B.log || true ; }
}


# Print general debug information

pipelinelib_do_debug_show_info()
{
	set -x

	{ env || true ; }    >&2
	{ id || true ; }    >&2

	if { git rev-parse --is-inside-work-tree >/dev/null 2>&1 ; } ; then
		{ git remote -v || true ; }    >&2
	fi
	echo "    debug: Listing home directory (home ${HOME} on $( hostname ), stage: ${STAGE_NAME})"    >&2
	{ ls -la "${HOME}" || true ; }    >&2
	echo "    debug: Listing current directory (current directory ${PWD} on $( hostname ), stage: ${STAGE_NAME})"    >&2
	{ ls -la "${PWD}" || true ; }    >&2
}
