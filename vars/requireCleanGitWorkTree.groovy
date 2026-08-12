// requireCleanGitWorkTree(): Require that Git repository work tree is clean (including submodules)

def call() {
    reportUsage(path: getClass().protectionDomain.codeSource.location.path)

    sh label: 'Ensuring that Git repository work tree is clean',
        script: '''#!/bin/sh
        # Require that Git repository work tree is clean (including submodules)

        set -f

        require_git_clean_work_tree()
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
            echo "    debug: Ensuring that Git repository work tree is clean... (${_hint_str})" >&2

            count_unclean_files
            if [ "${count_modified}" -eq 0 ] && [ "${count_unclean}" -eq 0 ]; then
                echo "    debug: Git repository work tree is clean (${_hint_str})" >&2
                return 0
            fi

            echo "    debug: ${count_modified} modified files in the work tree" >&2
            echo "" >&2
            git status --porcelain >&2
            git submodule foreach --recursive "git status --porcelain" >&2
            echo "" >&2

            echo "    debug: ${count_unclean} generated files in the work tree" >&2
            echo "" >&2
            git clean -n -f -d -x >&2
            git submodule foreach --recursive "git clean -n -f -d -x" >&2
            echo "" >&2

            echo "ERROR: Git repository work tree is NOT clean (${_hint_str})" >&2

            return 1
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

        require_git_clean_work_tree
        '''
}
