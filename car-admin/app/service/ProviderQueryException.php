<?php

declare(strict_types=1);

namespace app\service;

final class ProviderQueryException extends \RuntimeException
{
    public function __construct(
        string $message,
        public readonly string $providerCode,
        public readonly string $providerRequestId = ''
    ) {
        parent::__construct($message);
    }
}
