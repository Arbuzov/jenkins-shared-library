for repo in ${repos[@]}; do
    cd $repo
    tagList=`git tag --sort=-committerdate --merged`
    repoSurname=`git remote`
    repoName=$(basename `git remote get-url --all $repoSurname`)
    repoName=${repoName%.git}
    repoName=${repoName^^}
    echo "">$WORKSPACE/$repoName.change.log
    firstTag="HEAD"
    for tag in $tagList; do
        logData=`git log --pretty=format:"%cI : %h - %an : $repoName %s" $tag..$firstTag`
        if [ "$logData" ]
        then
            echo "$firstTag-$tag">> $WORKSPACE/$repoName.change.log
            git log --pretty=format:"%cI : %h - %an : $repoName %s" $tag..$firstTag|sort --reverse --k=1,19 >> $WORKSPACE/$repoName.change.log
            echo "" >> $WORKSPACE/$repoName.change.log
            firstTag=$tag
        fi
    done
done;
