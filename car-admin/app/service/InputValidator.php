<?php

declare(strict_types=1);

namespace app\service;

class InputValidator
{
    public static function validate(array $schema, array $input): array
    {
        $clean = [];
        foreach ($schema as $field) {
            $key = $field['key'];
            $value = trim((string) ($input[$key] ?? ''));
            if (($field['required'] ?? false) && $value === '') {
                throw new \InvalidArgumentException($field['label'] . '不能为空');
            }
            if ($value === '') {
                continue;
            }
            $value = in_array($field['type'], ['plate','plate_prefix','plate_or_vin','vin'], true) ? strtoupper(str_replace(' ', '', $value)) : $value;
            $valid = match ($field['type']) {
                'vin' => (bool) preg_match('/^[A-HJ-NPR-Z0-9]{17}$/', $value),
                'plate' => (bool) preg_match('/^[\x{4e00}-\x{9fa5}][A-Z][A-Z0-9]{5,6}$/u', $value),
                'plate_prefix' => (bool) preg_match('/^[\x{4e00}-\x{9fa5}][A-Z](?:[A-Z0-9]{5,6})?$/u', $value),
                'plate_or_vin' => (bool) preg_match('/^(?:[A-HJ-NPR-Z0-9]{17}|[\x{4e00}-\x{9fa5}][A-Z][A-Z0-9]{5,6})$/u', $value),
                'idcard' => (bool) preg_match('/^\d{17}[0-9X]$/i', $value),
                'identity' => (bool) preg_match('/^(?:\d{17}[0-9X]|[0-9A-Z]{18})$/i', $value),
                'name' => mb_strlen($value) >= 2 && mb_strlen($value) <= 30,
                default => mb_strlen($value) <= 100,
            };
            if (!$valid) {
                throw new \InvalidArgumentException($field['label'] . '格式不正确');
            }
            $clean[$key] = $value;
        }
        return $clean;
    }
}
