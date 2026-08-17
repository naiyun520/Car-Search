<?php

return [
    'default' => 'mysql',
    'connections' => [
        'mysql' => [
            'type' => 'mysql',
            'hostname' => env('database.hostname', '127.0.0.1'),
            'database' => env('database.database', ''),
            'username' => env('database.username', ''),
            'password' => env('database.password', ''),
            'hostport' => env('database.hostport', '3306'),
            'charset' => 'utf8mb4',
            'prefix' => env('database.prefix', 'ci_'),
            'debug' => (bool) env('app.debug', false),
            'fields_strict' => true,
            'break_reconnect' => true,
        ],
    ],
];
