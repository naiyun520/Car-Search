<?php

declare(strict_types=1);

namespace app;

use think\exception\Handle;
use think\facade\Log;
use think\Request;
use think\Response;
use Throwable;

class ExceptionHandle extends Handle
{
    public function render($request, Throwable $exception): Response
    {
        if ($this->isApiRequest($request)) {
            $errorId = date('YmdHis') . '-' . strtoupper(bin2hex(random_bytes(3)));
            try { Log::error("[{$errorId}] {$exception->getMessage()} in {$exception->getFile()}:{$exception->getLine()}\n{$exception->getTraceAsString()}"); } catch (Throwable) {}
            // 无论调试开关如何，API 响应永不泄露内部异常（英文/堆栈一律进日志，前端只看到可追踪的中文编号）
            $message = '后台服务异常，请联系管理员并提供错误编号 ' . $errorId;
            return json(['code' => 500, 'message' => $message, 'data' => null, 'error_id' => $errorId], 500);
        }
        return parent::render($request, $exception);
    }

    private function isApiRequest(Request $request): bool
    {
        $path = ltrim($request->pathinfo(), '/');
        return str_starts_with($path, 'api/') || str_starts_with($path, 'admin-api/');
    }
}
