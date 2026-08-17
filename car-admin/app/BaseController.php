<?php

declare(strict_types=1);

namespace app;

use think\Request;
use think\facade\Log;
use think\db\exception\DbException;
use think\exception\ValidateException;

abstract class BaseController
{
    public function __construct(protected Request $request)
    {
    }

    protected function ok(mixed $data = null, string $message = 'success'): \think\response\Json
    {
        return json(['code' => 0, 'message' => $message, 'data' => $data]);
    }

    protected function fail(string $message, int $code = 400, int $status = 400): \think\response\Json
    {
        return json(['code' => $code, 'message' => $message, 'data' => null], $status);
    }

    /**
     * 异常消息安全化：业务校验与业务异常显示原始中文文案，
     * 框架/数据库/PHP 内部错误一律不外泄，只记日志返回通用提示。
     */
    protected function safeError(\Throwable $error): string
    {
        if ($error instanceof \InvalidArgumentException || $error instanceof ValidateException) {
            return $error->getMessage();
        }
        if ($error instanceof \RuntimeException && !($error instanceof DbException)) {
            return $error->getMessage();
        }
        Log::warning('api exception hidden | ' . $error->getMessage() . ' in ' . $error->getFile() . ':' . $error->getLine());
        return '服务暂时不可用，请稍后重试';
    }
}
