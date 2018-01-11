#!/bin/bash

GITLAB_REP0_ROOT=/var/opt/gitlab/git-data/repositories
MAP_REPO_ROOT=/home/git777/oldrepos-map

rm -rf $MAP_REPO_ROOT # DANGER!!
su git -c "mkdir $MAP_REPO_ROOT"
cat table.csv | while read REPO_TO SEMICOLON REPO_FROM; do
    REPO_FROM=${REPO_FROM#*:}
    echo $REPO_FROM "->" $REPO_TO
    if [ ! -h $GITLAB_REP0_ROOT/$REPO_FROM.git ]; then
        su git -c "mkdir -p $(dirname $MAP_REPO_ROOT/$REPO_TO)"
        su git -c "ln -s $GITLAB_REP0_ROOT/$REPO_FROM.git $MAP_REPO_ROOT/$REPO_TO.git"
    fi
done
