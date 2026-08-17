<?php

return [
    'wechat' => [
        'app_id' => env('wechat.app_id', ''),
        'app_secret' => env('wechat.app_secret', ''),
        'offer_id' => env('wechat.offer_id', ''),
        'sandbox_app_key' => env('wechat.sandbox_app_key', ''),
        'production_app_key' => env('wechat.production_app_key', ''),
        'pay_env' => (int) env('wechat.pay_env', 0),
    ],
    'data_encrypt_key' => env('security.data_encrypt_key', ''),
    'query_timeout' => 15,
];
