<?php

declare(strict_types=1);

namespace app\service;

use think\Request;

final class ApiRequestService
{
    private const MAX_BODY_BYTES = 32768;

    public static function json(Request $request): array
    {
        $raw = (string) $request->getContent();
        if (strlen($raw) > self::MAX_BODY_BYTES) {
            throw new \InvalidArgumentException('请求内容过大');
        }
        if ($raw !== '') {
            try {
                $decoded = json_decode($raw, true, 32, JSON_THROW_ON_ERROR);
            } catch (\JsonException) {
                throw new \InvalidArgumentException('请求数据格式错误，请更新小程序后重试');
            }
            if (!is_array($decoded)) throw new \InvalidArgumentException('请求数据格式错误');
            return $decoded;
        }
        $form = (array) $request->post();
        if ($form) return $form;
        throw new \InvalidArgumentException('未收到请求内容，请检查服务器是否允许接收 POST 请求体');
    }

    public static function requiredHeader(Request $request, string $name, string $pattern, string $message): string
    {
        $value = trim((string) $request->header($name, ''));
        if ($value === '' || !preg_match($pattern, $value)) throw new \InvalidArgumentException($message);
        return $value;
    }

    /**
     * Business identifiers are carried in JSON/query parameters and repeated in
     * headers.  Some gateways and old WebView stacks can drop non-standard
     * headers, so a valid body value must remain sufficient to process a request.
     */
    public static function requiredTransportValue(
        Request $request,
        string $header,
        mixed $fallback,
        string $pattern,
        string $message
    ): string {
        $headerValue = trim((string) $request->header($header, ''));
        $bodyValue = trim(is_scalar($fallback) ? (string) $fallback : '');

        if ($headerValue !== '' && $bodyValue !== '' && !hash_equals($headerValue, $bodyValue)) {
            throw new \InvalidArgumentException('请求中的业务标识不一致，请重新打开最新版小程序');
        }

        $value = $bodyValue !== '' ? $bodyValue : $headerValue;
        if ($value === '' || !preg_match($pattern, $value)) throw new \InvalidArgumentException($message);
        return $value;
    }

    public static function requireProtocol(Request $request): void
    {
        $version = trim((string) $request->header('x-car-client-version', ''));
        if (!hash_equals(CheckoutService::CLIENT_PROTOCOL, $version)) {
            throw new \InvalidArgumentException('客户端版本已过期，请完全退出并重新打开最新版小程序');
        }
    }
}
