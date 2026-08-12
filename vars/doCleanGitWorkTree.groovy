// doCleanGitWorkTree(): Clean Git repository work tree (including submodules)

def call() {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)

    sh label: 'Cleaning Git repository work tree',
        script: '''#!/bin/sh
        # Clean Git repository work tree (including submodules)

        set -f

        clean_git_work_tree()
        {
            _host_name="$(hostname || echo "UNKNOWN_HOSTNAME")"
            _stage_name="${STAGE_NAME:-UNKNOWN_STAGE}"
            _hint_str="top work tree ${PWD}, stage: ${_stage_name}, hostname ${_host_name}"

            if ! git version >/dev/null; then
                # NOTE: git binary must be present in container or on the host
                echo "ERROR: git program is not working (${_hint_str})" >&2
                return 1
            fi
            if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
                echo "ERROR: Working directory is NOT inside the Git work tree (${_hint_str})" >&2
                return 1
            fi
            echo "    debug: Cleaning Git repository work tree... (${_hint_str})" >&2

            # HACK: this runs the commands "git status --porcelain", which
            # update Git filesystem cache. This makes later "git reset --hard"
            # commands only restore the files that were actually modified.
            count_unclean_files

            echo "    debug: ${count_modified} modified files in the work tree" >&2
            echo "    debug: ${count_unclean} generated files in the work tree" >&2

            echo "    debug: restoring modified files..." >&2
            git reset --hard --recurse-submodules

            echo "    debug: removing generated files..." >&2
            git clean -f -f -d -x --quiet
            git submodule foreach --recursive "git clean -f -f -d -x --quiet"

            return 0
        }

        count_unclean_files()
        {
            count_modified=0
            count_unclean=0
            count_submodules=0
            count_modified_sub=0
            count_unclean_sub=0

            # NOTE: the commands "git status --porcelain",
            # update Git filesystem cache. This makes later "git reset --hard"
            # commands only restore the files that were actually modified.

            count_modified="$(git status --porcelain 2>/dev/null | wc -l)"
            count_submodules="$(git submodule status --recursive 2>/dev/null | wc -l)"
            count_unclean="$(git clean -n -f -d -x 2>/dev/null | wc -l)"

            if [ "${count_submodules}" -eq 0 ]; then
                return 0
            fi

            count_modified_sub="$(git submodule --quiet foreach --recursive \\
                "git status --porcelain" \\
                2>/dev/null | wc -l)"

            count_unclean_sub="$(git submodule --quiet foreach --recursive \\
                "git clean -n -f -d -x" \\
                2>/dev/null | wc -l)"

            count_modified=$((count_modified + count_modified_sub))
            count_unclean=$((count_unclean + count_unclean_sub))

            return 0
        }

        clean_git_work_tree
        '''
}
