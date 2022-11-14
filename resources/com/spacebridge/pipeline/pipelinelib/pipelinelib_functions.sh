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


_pipelinelib_generate_checksum_for_file()
{
	local _checksum_tool="${1}"
	local _prefix_name="${2}"
	local _checksum_chars_len="${3}"
	local _file_path="${4}"
	local _file_checksum=

	if [ -f "${_file_path}" ] ; then
		_file_checksum="$( "${_checksum_tool}" -b "${_file_path}" | cut -d ' ' -f 1 | grep -E -o '^[0-9A-Fa-f]{'"${_checksum_chars_len}"'}$' )"
	fi
	if [ -z "${_file_checksum}" ] ; then
		# repeat a space N times, the replace spaces with 'f' s
		_file_checksum="$( printf "%${_checksum_chars_len}s" ' ' | tr ' ' 'f' )"
	fi
	printf '%s (%s) = %s\n' "${_prefix_name}" "${_file_path}" "${_file_checksum}"
}

# Generate sha256, sha1 and md5 checksums for one file, in BSD checksum style format
# Argument: FILE_PATH, path to one file
pipelinelib_generate_checksums_bsd_for_file()
{
	local _file_path="${1}"
	local _file_size=

	if [ -f "${_file_path}" ] ; then
		# NOTE: do not use stat command, it is not working in some environments on Jenkins
		_file_size="$( wc -c <"${_file_path}" | cut -d ' ' -f 1 | grep -E -o '^[0-9]+$' )"
	else
		_file_size="-1"
	fi
	if [ -z "${_file_path}" ] ; then
		_file_size="-1"
	fi

	printf '%s (%s) = %s\n' 'SIZE' "${_file_path}" "${_file_size}"

	# Disable SHA512 checksums for now
	#_pipelinelib_generate_checksum_for_file "sha512sum" "SHA512" 128 "${_file_path}"
	_pipelinelib_generate_checksum_for_file "sha256sum" "SHA256" 64 "${_file_path}"
	_pipelinelib_generate_checksum_for_file "sha1sum" "SHA1" 40 "${_file_path}"
	_pipelinelib_generate_checksum_for_file "md5sum" "MD5" 32 "${_file_path}"
	printf '\n'
}


# Generate sha256, sha1 and md5 checksums for files, in BSD checksum style format
# Input is newline-separated list of files on standard input
pipelinelib_generate_checksums_bsd_for_list()
{
	( set +x ; set +e ; _pipelinelib_generate_checksums_bsd_for_list_sub "$@" ; )
}

_pipelinelib_generate_checksums_bsd_for_list_sub()
{
	set +x
	set +e
	local _file_path=
	while IFS="$( printf '\n' )" read -r _file_path ; do
		pipelinelib_generate_checksums_bsd_for_file "${_file_path}"
	done
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
