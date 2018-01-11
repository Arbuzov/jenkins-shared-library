<?php

class Gitlab extends HTTPRequester
{

    static $secret = null;

    static $apiUrl = null;
    
    static $userDict = array(
//        'abashmach' => '',
        'adanilov' => array('email'=>'Alexander.Danilov@advantechwireless.com', 'login'=>'adanilov'),
        'ahu' => array('email'=>'Aiping.Hu@advantechwireless.com', 'login'=>'ahu'),
        'alandovskis' => array('email'=>'Alex.Landovskis@advantechwireless.com', 'login'=>'alandovskis'),
        'arbuzov' => array('email'=>'Serge.Arbuzov@advantechwireless.com', 'login'=>'arbuzov'),
        'drichard' => array('email'=>'Denys.Richard@advantechwireless.com', 'login'=>'drichard'),
        'edymchenko' => array('email'=>'Evgeny.Dymchenko@advantechwireless.com', 'login'=>'edymchenko'),
        'esoule' => array('email'=>'Evgueni.Souleimanov@advantechwireless.com', 'login'=>'souleimanov'),
        'hcioca' => array('email'=>'HoreaAdrian.Cioca@advantechwireless.com', 'login'=>'hcioca'),
        'igor' => array('email'=>'Igor.Mordkovitch@advantechwireless.com', 'login'=>'imordkovit'),
        'jemkov' => array('email'=>'Alexei.Jemkov@advantechwireless.com', 'login'=>'jemkov'),
//        'jguo' => '',
        'jlynes' => array('email'=>'Jon.Lynes@advantechwireless.com', 'login'=>'jlynes'),
        'jtu' => array('email'=>'Jun.Tu@advantechwireless.com', 'login'=>'jtu'),
        'kolotseys' => array('email'=>'Sergey.Kolotsey@advantechwireless.com', 'login'=>'kolotseys'),
        'krot' => array('email'=>'Dmitri.Krot@advantechwireless.com', 'login'=>'krot'),
        'lesnov' => array('email'=>'Maks.Lesnov@advantechwireless.com', 'login'=>'lesnov'),
        'mikesm' => array('email'=>'Michael.Smirnov@advantechwireless.com', 'login'=>'msmirnov'),
        'mrobert' => array('email'=>'Menard.Robert@advantechwireless.com', 'login'=>'mrobert'),
        'muththaiah' => array('email'=>'Ranesh.Muththaiah@advantechwireless.com', 'login'=>'muththaiah'),
//        'romanovici' => '',
        'sburgida' => array('email'=>'Steve.Burgida@advantechwireless.com', 'login'=>'sburgida'),
        'spaul' => array('email'=>'Samuel.Paul@advantechwireless.com', 'login'=>'spaul'),
        'ssamaha' => array('email'=>'Samir.Samaha@advantechwireless.com', 'login'=>'ssamaha'),
        'starkov' => array('email'=>'Andrey.Starkov@advantechwireless.com', 'login'=>'starkov'),
        'tcoochey' => array('email'=>'Tim.Coochey@advantechwireless.com', 'login'=>'tcoochey'),
        'vobraztov' => array('email'=>'Vitali.Obraztov@advantechwireless.com', 'login'=>'vobraztov'),
        'xyu' => array('email'=>'Xiaode.Yu@advantechwireless.com', 'login'=>'xyu'),
        'yakutovich' => array('email'=>'Nickolas.Yakutovich@advantechwireless.com', 'login'=>'yakutovich'),
    );
    
    private static function _validate() {
        if (is_null(self::$apiUrl) || is_null(self::$secret)) {
            throw new Exception("Url and secret must be set");
        }
    }
    
    static function init($url, $secret) {
            self::$secret = $secret;
            self::$apiUrl = $url;
            self::_validate();
    }
    
    static function log($message, $filename='gitlab_result.log') {
        file_put_contents ($filename, date(DATE_ATOM) . " : " . print_r($message, true) . "\n", FILE_APPEND);
    }
    
    static function getUserEmail($username)
    {
        $result = $username . '@advantech.ca';
        if (array_key_exists($username, self::$userDict)) {
            $result = self::$userDict[$username]['email'];
        }
        return $result;
    }
    
    static function getExistingGroups()
    {
        self::_validate();
        $groups = array();
        $result = json_decode(HTTPRequester::HTTPGet(self::$apiUrl . '/groups/', array(
            'private_token' => self::$secret
        )));
        self::log($result);
        foreach ($result as $info) {
            $groups[$info->id] = $info->path;
        }
        return $groups;
    }
    
    static function addUserKey($userId, $key) {
        self::_validate();
        $result = json_decode(self::HTTPPost(self::$apiUrl . "/users/$userId/keys/", array(
            'private_token' => self::$secret,
            'id' => $userId,
            'key' => $key,
            'title' => uniqid()
        )));
        self::log($result);
        return $result;
    }

    static function getExistingUsers()
    {
        self::_validate();
        $users = array();
        $result = json_decode(HTTPRequester::HTTPGet(self::$apiUrl . '/users/', array(
            'private_token' => self::$secret
        )));
        self::log($result);
        foreach ($result as $info) {
            $users[$info->id] = $info->username;
        }
        return $users;
    }

    static function getGroupId($path)
    {
        self::_validate();
        $result = 0;
        if (count($path)>0) {
            $data = json_decode(HTTPRequester::HTTPGet(self::$apiUrl . '/groups/', array(
                'private_token' => self::$secret,
                'search' => $path[count($path)-1]
            )));
            self::log($data);
            if (is_array($data) && ! empty($data)) {
                foreach ($data as $info) {
                    if ($info->full_path == implode('/', $path)) {
                        $result = $info->id;
                    }
                }
            }
        }
        return $result;
    }

    static function addUserByName($username)
    {
        self::_validate();
        $result = json_decode(self::HTTPPost(self::$apiUrl . '/users/', array(
            'private_token' => self::$secret,
            'username' => $username,
            'name' => $username,
            'email' => self::getUserEmail($username),
            'password' => 'password',
            'reset_password' => false,
            'skip_confirmation'=>false
        )));
        self::log($result);
        return $result;
    }

    function getProjectId($path)
    {
        self::_validate();
        $result = 0;
        $data = json_decode(HTTPRequester::HTTPGet(self::$apiUrl . '/projects/', array(
            'private_token' => self::$secret,
            'search' => $path[count($path) - 1]
        )));
        self::log($data);
        if (is_array($data) && ! empty($data)) {
            foreach ($data as $info) {
                if ($info->namespace->full_path == implode('/', array_slice($path, 0, count($path) - 1))) {
                    $result = $info->id;
                }
            }
        }
        return $result;
    }
    
    function getSourceProjectId($path)
    {
        self::_validate();
        $result = 0;
        $data = json_decode(HTTPRequester::HTTPGet(self::$apiUrl . '/projects/', array(
            'private_token' => self::$secret,
            'search' => $path[count($path) - 1]
        )));
        self::log($data);
        if (is_array($data) && ! empty($data)) {
            $result = $data[0]->id;
            foreach ($data as $info) {
                if (!isset($info->forked_from_project)) {
                    $result = $info->id;
                    break;
                }
            }
        }
        return $result;
    }

    static function addGroupByName($username, $parentId = null)
    {
        self::_validate();
    }
    
    static function dropKeys() {
        file_put_contents ('/var/opt/gitlab/.ssh/authorized_keys', '');
    }
}