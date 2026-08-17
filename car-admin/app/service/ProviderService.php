<?php

declare(strict_types=1);

namespace app\service;

class ProviderService
{
    public static function query(array $service, array $input): array
    {
        $secret = CryptoService::decrypt((string) ($service['request_url_cipher'] ?? ''));
        $configuredUrl = trim((string) ($secret['url'] ?? ''));
        if ($configuredUrl === '') throw new \RuntimeException('查询接口未配置');
        $resolvedAddress = self::assertPublicHttpsUrl($configuredUrl);

        $method = strtoupper((string) ($service['request_method'] ?? 'GET'));
        if (!in_array($method, ['GET', 'POST'], true)) throw new \RuntimeException('查询接口请求方式无效');
        $url = self::buildUrl($configuredUrl, $method === 'GET' ? $input : [], $method === 'POST' ? array_keys($input) : []);
        $curl = curl_init($url);
        $options = [
            CURLOPT_RETURNTRANSFER=>true,
            CURLOPT_CONNECTTIMEOUT=>5,
            CURLOPT_TIMEOUT=>(int) config('car.query_timeout'),
            CURLOPT_SSL_VERIFYPEER=>true,
            CURLOPT_SSL_VERIFYHOST=>2,
            CURLOPT_FOLLOWLOCATION=>false,
            CURLOPT_PROTOCOLS=>CURLPROTO_HTTPS,
            CURLOPT_HTTPHEADER=>['Accept: application/json'],
            CURLOPT_USERAGENT=>'CarInquiry/1.2',
        ];
        $urlParts = parse_url($url);
        if ($resolvedAddress !== (string) $urlParts['host']) $options[CURLOPT_RESOLVE] = [$urlParts['host'] . ':' . ($urlParts['port'] ?? 443) . ':' . $resolvedAddress];
        if ($method === 'POST') {
            $options[CURLOPT_POST] = true;
            $options[CURLOPT_POSTFIELDS] = http_build_query($input);
            $options[CURLOPT_HTTPHEADER][] = 'Content-Type: application/x-www-form-urlencoded';
        }
        curl_setopt_array($curl, $options);
        $body = curl_exec($curl);
        $httpCode = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $error = curl_error($curl);
        curl_close($curl);
        if ($body === false || $httpCode !== 200) {
            throw new \RuntimeException($body === false ? ($error ?: '供应商连接失败') : '供应商 HTTP 状态异常：' . $httpCode);
        }
        if (strlen($body) > 1048576) throw new \RuntimeException('供应商响应内容过大');

        $payload = json_decode($body, true);
        if (!is_array($payload)) throw new \RuntimeException('供应商未返回有效 JSON');
        $codePath = trim((string) ($service['response_code_path'] ?? 'code'));
        $successValue = (string) ($service['response_success_value'] ?? '200');
        $providerCode = $codePath === '' ? $successValue : (string) self::valueByPath($payload, $codePath);
        $requestId = mb_substr(trim((string) ($payload['request_id'] ?? $payload['requestId'] ?? $payload['rid'] ?? '')), 0, 100);
        if ($codePath !== '' && $providerCode !== $successValue) {
            $providerMessage = mb_substr(trim((string) ($payload['msg'] ?? $payload['message'] ?? '')),0,160);
            throw new ProviderQueryException(
                '供应商查询未成功' . ($providerMessage !== '' ? '：' . $providerMessage : ''),
                $providerCode !== '' ? $providerCode : 'missing_code',
                $requestId
            );
        }
        $dataPath = trim((string) ($service['response_data_path'] ?? 'data'));
        $data = $dataPath === '' ? $payload : self::valueByPath($payload, $dataPath);
        if (!is_array($data)) throw new \RuntimeException('供应商结果数据格式无效');
        return ['code'=>$providerCode ?: $successValue, 'request_id'=>$requestId, 'data'=>self::normalize($service, $data)];
    }

    private static function buildUrl(string $url, array $input, array $removeKeys): string
    {
        $parts = parse_url($url);
        if (!is_array($parts)) throw new \RuntimeException('查询接口 URL 无效');
        $query = [];
        parse_str((string) ($parts['query'] ?? ''), $query);
        foreach ($removeKeys as $key) unset($query[$key]);
        foreach ($input as $key => $value) $query[$key] = $value;
        $host = str_contains((string) $parts['host'], ':') ? '[' . $parts['host'] . ']' : $parts['host'];
        $port = isset($parts['port']) ? ':' . $parts['port'] : '';
        return 'https://' . $host . $port . ($parts['path'] ?? '/') . ($query ? '?' . http_build_query($query) : '');
    }

    private static function assertPublicHttpsUrl(string $url): string
    {
        $parts = parse_url($url);
        if (!is_array($parts) || strtolower((string) ($parts['scheme'] ?? '')) !== 'https' || empty($parts['host'])) throw new \RuntimeException('查询接口必须使用 HTTPS');
        $host = strtolower((string) $parts['host']);
        if ($host === 'localhost' || str_ends_with($host, '.local')) throw new \RuntimeException('查询接口地址不安全');
        $addresses = filter_var($host, FILTER_VALIDATE_IP) ? [$host] : (gethostbynamel($host) ?: []);
        if (!$addresses) throw new \RuntimeException('查询接口域名无法解析');
        foreach ($addresses as $address) {
            if (!filter_var($address, FILTER_VALIDATE_IP, FILTER_FLAG_NO_PRIV_RANGE | FILTER_FLAG_NO_RES_RANGE)) throw new \RuntimeException('查询接口禁止访问内网地址');
        }
        return $addresses[0];
    }

    private static function valueByPath(array $data, string $path): mixed
    {
        $value = $data;
        foreach (explode('.', $path) as $segment) {
            if (!is_array($value) || !array_key_exists($segment, $value)) return null;
            $value = $value[$segment];
        }
        return $value;
    }

    private static function normalize(array $service, array $data): array
    {
        $schema = json_decode((string) $service['result_schema'], true) ?: [];
        $result = [];
        foreach ($schema as $path => $label) {
            $value = self::valueByPath($data, (string) $path);
            $lastSegment = substr((string) strrchr('.' . $path, '.'), 1);
            if (in_array($lastSegment, ['state', 'status'], true) && is_numeric($value)) {
                $value = match ((int) $value) { 1=>'一致', 2=>'不一致', 3=>'查询成功无结果', default=>'未知' };
            }
            if (is_array($value) && (!array_is_list($value) || array_filter($value, 'is_array'))) $value = json_encode($value, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
            if (is_string($value)) $value = mb_substr($value,0,2000);
            $result[] = ['key'=>$path, 'label'=>$label, 'value'=>$value === null || $value === '' ? '暂无数据' : $value];
        }
        return $result;
    }
}
