<?php

declare(strict_types=1);

namespace app\service;

use think\facade\Db;

class ConfigService
{
    public static function value(string $key, mixed $default = ''): mixed
    {
        $value = Db::name('setting')->where('key', $key)->value('value');
        return $value === null ? $default : $value;
    }

    public static function secure(string $key, string $envFallback = ''): string
    {
        $cipher = Db::name('secure_setting')->where('key', $key)->value('value_cipher');
        if (is_string($cipher) && $cipher !== '') {
            $decoded = CryptoService::decrypt($cipher);
            if (!array_key_exists('value', $decoded)) throw new \RuntimeException('安全配置数据格式无效：' . $key);
            return (string) $decoded['value'];
        }
        return $envFallback;
    }

    public static function saveSecure(string $key, string $value): void
    {
        Db::name('secure_setting')->strict(false)->replace()->insert([
            'key' => $key,
            'value_cipher' => CryptoService::encrypt(['value' => $value]),
            'updated_at' => date('Y-m-d H:i:s'),
        ]);
    }

    public static function configured(string $key, string $envFallback = ''): bool
    {
        return self::secure($key, $envFallback) !== '';
    }

    public static function wechat(): array
    {
        $fallback = (array) config('car.wechat');
        return [
            'app_id' => (string) self::value('wechat_app_id', $fallback['app_id'] ?? ''),
            'app_secret' => self::secure('wechat_app_secret', (string) ($fallback['app_secret'] ?? '')),
            'offer_id' => (string) self::value('wechat_offer_id', $fallback['offer_id'] ?? ''),
            'sandbox_app_key' => self::secure('wechat_sandbox_app_key', (string) ($fallback['sandbox_app_key'] ?? '')),
            'production_app_key' => self::secure('wechat_production_app_key', (string) ($fallback['production_app_key'] ?? '')),
            'pay_env' => (int) self::value('wechat_pay_env', $fallback['pay_env'] ?? 0),
        ];
    }
}
