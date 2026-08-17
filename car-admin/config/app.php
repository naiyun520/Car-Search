<?php

return [
    'app_namespace' => 'app',
    'app_debug' => (bool) env('app.debug', false),
    'default_timezone' => env('app.default_timezone', 'Asia/Shanghai'),
    'show_error_msg' => (bool) env('app.debug', false),
    'exception_handle' => app\ExceptionHandle::class,
    'exception_tmpl' => app()->getThinkPath() . 'tpl/think_exception.tpl',
];
