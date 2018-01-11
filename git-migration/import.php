<?php
include 'HTTPRequester.php';
include 'Gitlab.php';
include 'Gitolite.php';

function stripGitUrl($url){
    return str_replace(array('.git', 'ssh://git7@pobeda.advantech.ca/', 'git@gitlab.advantech.ca:archive/'),'',$url);
}

function cleanDir(){
    unlink(__DIR__ . '/repo.txt');
    unlink(__DIR__ . '/gitlab_result.log');
    unlink(__DIR__ . '/output.log');
    unlink(__DIR__ . '/table.csv');
}

function outputLog($message){
    file_put_contents ('output.log', date(DATE_ATOM) . " : " . print_r($message, true). "\n", FILE_APPEND);
}

function reExport($srcUrl, $dstUrl) {
    $repoDir = uniqid('/tmp/repository/');
    exec ("git clone --bare  $srcUrl  $repoDir");
    exec ("cd  $repoDir; git remote add dest $dstUrl");
    exec ("cd  $repoDir; git push -f --tags dest refs/heads/*:refs/heads/*");
    exec ("rm -rf $repoDir");
    file_put_contents ('table.csv', stripGitUrl($srcUrl) . " ; " . stripGitUrl($dstUrl) . "\n", FILE_APPEND);
}

function parseUsers(String $fileName)
{
    $users = array();
    $lines = file($fileName);
    foreach ($lines as $line) {
        if (preg_match('/(?<=repo\/users\/)\w*/', $line, $matches)) {
            if (! in_array($matches[0], $users) && ! empty($matches[0])) {
                $users[] = $matches[0];
            }
        }
    }
    return $users;
}

function parseGroups(String $fileName)
{
    $groups = array();
    $lines = file($fileName);
    foreach ($lines as $line) {
        if (preg_match('/(?<=repo\/advantech\/)(\w|-|\/|\.)*/', $line, $matches)) {
            if (! in_array($matches[0], $groups) && ! empty($matches[0])) {
                $groups['ssh://git7@pobeda.advantech.ca/repo/advantech/'.$matches[0]] = $matches[0];
            }
        } elseif (preg_match('/(?<=repo\/mirror\/)(\w|-|\/|\.)*/', $line, $matches)) {
            if (! in_array($matches[0], $groups) && ! empty($matches[0])) {
                $groups['ssh://git7@pobeda.advantech.ca/repo/mirror/'.$matches[0]] = 'mirror/'.$matches[0];
            }
        } elseif (preg_match('/(?<=repo\/centos\/)(\w|-|\/|\.)*/', $line, $matches)) {
            if (! in_array($matches[0], $groups) && ! empty($matches[0])) {
                $groups['ssh://git7@pobeda.advantech.ca/repo/centos/'.$matches[0]] = 'centos/'.$matches[0];
            }
        } elseif (preg_match('/(?<=repo\/z\/archive\/)(\w|-|\/|\.)*/', $line, $matches)) {
            if (! in_array($matches[0], $groups) && ! empty($matches[0])) {
                $groups['ssh://git7@pobeda.advantech.ca/repo/z/archive/'.$matches[0]] = 'archive/'.$matches[0];
            }
        }
    }
    return $groups;
}

function parseUserGroups(String $fileName)
{
    $groups = array();
    $lines = file($fileName);
    foreach ($lines as $line) {
        if (preg_match('/(?<=repo\/users\/)(\w|-|\/|\.)*/', $line, $matches)) {
            if (! in_array($matches[0], $groups) && ! empty($matches[0])) {
                $groups['ssh://git7@pobeda.advantech.ca/repo/users/'.$matches[0]] = str_replace('public/', '',$matches[0]);
            }
        }
    }
    return $groups;
}

$settings=parse_ini_file(__DIR__ . '/settings.ini');
cleanDir();
Gitlab::init($settings['gitlabUrl'], $settings['secret']);
Gitolite::getInfo("repo.txt");
Gitlab::dropKeys();
Gitlab::addUserKey(1, file_get_contents($settings['gitlabAdminPubKeyPath']));

$parsedUsers = parseUsers(__DIR__ . '/repo.txt');
outputLog($parsedUsers);
$existingUsers = Gitlab::getExistingUsers();
$createdUsers = array();
outputLog("Process users");

foreach ($parsedUsers as $username) {
    if (! in_array($username, $existingUsers)) {
        outputLog("Create user $username");
        $result = Gitlab::addUserByName($username);
        Gitlab::log($result);
        if (isset($result->id)) {
            $createdUsers[$username]=$result->id;
            $keys = Gitolite::getUserKeys($username);
            foreach ($keys as $key) {
                Gitlab::addUserKey($result->id, $key);
            }
        }
    }
}

outputLog($createdUsers);

$parsedGroups = parseGroups(__DIR__ . '/repo.txt');

$parsedUserGroups = parseUserGroups(__DIR__ . '/repo.txt');

Gitolite::dropRepos();

outputLog("Process shared repos");

foreach ($parsedGroups as $srcUrl=>$group) {
    $levels = explode('/', $group);
    foreach ($levels as $index => $levelName) {
        if ($levelName == '..') 
            continue;
        if (($index + 1) == count($levels)) {
            outputLog("Create shared repo from  '$srcUrl' using path '$group' and project name is '$levelName'");
            $id = Gitlab::getProjectId($levels);
            if ($id == 0) {
                $projectValues = array(
                    'private_token' => Gitlab::$secret,
                    'name' => $levelName,
                    'path' => $levelName,
                    'namespace_id' => Gitlab::getGroupId(array_slice($levels, 0, $index))
                );
                outputLog("Create project using path ". implode('/', array_slice($levels, 0, $index)));
                $result = json_decode(HTTPRequester::HTTPPost(Gitlab::$apiUrl . '/projects/', $projectValues));
                Gitlab::log($result);
                outputLog("Created ". $result->name_with_namespace);
                reExport($srcUrl, $result->ssh_url_to_repo);
            } else {
                outputLog("Project allready exists and its id $id");
            }
        } else {
            outputLog("Process group for  $srcUrl using root '".implode('/', array_slice($levels, 0, $index)) . "' with the name '$levelName' from record '$group'");
            $id = Gitlab::getGroupId(array_slice($levels, 0, $index+1));
            if ($id == 0) {
                outputLog("Group '$levelName' not found");
                $groupValues = array(
                    'private_token' => Gitlab::$secret,
                    'name' => $levelName,
                    'path' => $levelName
                );
                if ($index > 0) {
                    $parentId = Gitlab::getGroupId(array_slice($levels, 0, $index));
                    if ($parentId) {
                        $groupValues['parent_id'] = $parentId;
                        outputLog("Found parent group $parentId");
                    }
                }
                $result = json_decode(HTTPRequester::HTTPPost(Gitlab::$apiUrl . '/groups/', $groupValues));
                Gitlab::log($result);
            } else {
                outputLog("Group allready exists and its id $id");
            }
        }
    }
}

outputLog("Process private repos");

foreach ($parsedUserGroups as $srcUrl=>$group) {
    $levels = explode('/', $group);
    $id = Gitlab::getSourceProjectId($levels);
    outputLog("Process private repo from  $srcUrl using path $group");
    if ($id != 0) {
        $forkValues = array(
            'private_token' => Gitlab::$secret,
            'id'=>$id,
            'namespace'=>$levels[0]
        );
        $result = json_decode(HTTPRequester::HTTPPost(Gitlab::$apiUrl . '/projects/'.$id.'/fork', $forkValues));
        Gitlab::log($result);
        reExport($srcUrl, $result->ssh_url_to_repo);
        $memberValues = array(
            'private_token' => Gitlab::$secret,
            'user_id'=>$createdUsers[$levels[0]],
            'access_level'=>30
        );
        $result = json_decode(HTTPRequester::HTTPPost(Gitlab::$apiUrl . '/projects/'.$id.'/members', $memberValues));
        Gitlab::log($result);
    }
}

exec(__DIR__ . '/create_link_map.sh');
