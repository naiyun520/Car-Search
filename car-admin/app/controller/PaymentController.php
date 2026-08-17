<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\CheckoutService;

/** 旧动态确认地址的兼容入口；核心状态机由 CheckoutService 统一处理。 */
final class PaymentController extends BaseController
{
    public function confirm(string $orderNo)
    {
        return $this->ok(CheckoutService::confirm($this->request->user,$orderNo));
    }
}
