<?php

class Gitolite {
    
    const sshKeysPath = '/var/lib/gitolite3/.ssh/authorized_keys';
    
    static function getInfo($fileName) {
        exec("ssh git7@localhost info > $fileName");
    }
    
    static function dropRepos() {
        exec('rm -rf /var/opt/gitlab/git-data/repositories/*');
    }
    
    static function getUserKeys($username) {
        $login = $username;
        if (array_key_exists($username, Gitlab::$userDict)) {
            $login = Gitlab::$userDict[$username]['login'];
        }
        $lines = file(self::sshKeysPath);
        $result = array();
        foreach ($lines as $line) {
            if (preg_match('/(?<=command="\/home\/git7\/bin\/gitolite-shell '.$login.'",no-port-forwarding,no-X11-forwarding,no-agent-forwarding,no-pty ).*/', $line, $matches)) {
                $result[] = $matches[0];
            }
        }
        return $result;
    }
}