<?php

declare(strict_types=1);

use think\App;

const APP_ROOT = __DIR__ . '/..';

header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');
header('Permissions-Policy: camera=(), microphone=(), geolocation=()');
if ((!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') || (string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '') === 'https') {
    header('Strict-Transport-Security: max-age=31536000; includeSubDomains');
}

$requestPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
if ($requestPath === '/' || $requestPath === '/index.php') {
    $installed = is_file(APP_ROOT . '/runtime/install.lock');
    if (!$installed && is_file(APP_ROOT . '/.env')) {
        $environment = parse_ini_file(APP_ROOT . '/.env', true, INI_SCANNER_TYPED) ?: [];
        $installed = (bool) ($environment['APP']['INSTALLED'] ?? false);
    }
    header('Location: ' . ($installed ? '/admin/' : '/install/'), true, 302);
    exit;
}

require dirname(__DIR__) . '/vendor/autoload.php';

$http = (new App())->http;
$response = $http->run();
$response->send();
$http->end($response);
