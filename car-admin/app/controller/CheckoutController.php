<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\ApiRequestService;
use app\service\CheckoutService;
use think\facade\Log;

final class CheckoutController extends BaseController
{
    public function create()
    {
        try {
            ApiRequestService::requireProtocol($this->request);
            $payload = ApiRequestService::json($this->request);
            $serviceCode = ApiRequestService::requiredTransportValue(
                $this->request,
                'x-car-service-code',
                $payload['service_code'] ?? $this->request->get('service_code', ''),
                '/^[a-z][a-z0-9_]{2,39}$/',
                '客户端未提交服务编码，请彻底删除旧版小程序后重新打开'
            );
            $requestKey = ApiRequestService::requiredTransportValue(
                $this->request,
                'x-idempotency-key',
                $payload['request_key'] ?? $this->request->get('request_key', ''),
                '/^[A-Za-z0-9_-]{16,64}$/',
                '客户端未提交有效请求标识，请重新打开最新版小程序'
            );
            return $this->ok(CheckoutService::create($this->request->user, $serviceCode, $requestKey, $payload));
        } catch (\InvalidArgumentException $error) {
            return $this->fail($error->getMessage(), 422, 422);
        } catch (\Throwable $error) {
            return $this->checkoutFailure($error);
        }
    }

    public function status()
    {
        return $this->executeOrder(static fn($user, $orderNo) => CheckoutService::status($user, $orderNo));
    }

    public function confirm()
    {
        return $this->executeOrder(static fn($user, $orderNo) => CheckoutService::confirm($user, $orderNo));
    }

    public function payment()
    {
        return $this->executeOrder(static fn($user, $orderNo) => CheckoutService::payment($user, $orderNo));
    }

    public function query()
    {
        return $this->executeOrder(static fn($user, $orderNo) => CheckoutService::query($user, $orderNo));
    }

    public function recoverable()
    {
        try {
            ApiRequestService::requireProtocol($this->request);
            return $this->ok(CheckoutService::recoverable($this->request->user));
        }
        catch (\InvalidArgumentException $error) { return $this->fail($error->getMessage(), 422, 422); }
        catch (\Throwable $error) { return $this->checkoutFailure($error); }
    }

    private function executeOrder(callable $operation)
    {
        try {
            ApiRequestService::requireProtocol($this->request);
            $orderNo = ApiRequestService::requiredTransportValue(
                $this->request,
                'x-car-order-no',
                $this->request->param('order_no', ''),
                '/^[A-Za-z0-9_|*@-]{8,32}$/',
                '客户端未提交有效订单号，请从订单记录重新进入'
            );
            return $this->ok($operation($this->request->user, $orderNo));
        } catch (\InvalidArgumentException $error) {
            return $this->fail($error->getMessage(), 422, 422);
        } catch (\Throwable $error) {
            return $this->checkoutFailure($error);
        }
    }

    private function checkoutFailure(\Throwable $error)
    {
        $errorId = date('YmdHis') . '-' . strtoupper(bin2hex(random_bytes(3)));
        Log::error('[' . $errorId . '] checkout failed | ' . $error->getMessage() . ' | ' . $error->getFile() . ':' . $error->getLine());
        return json(['code'=>503,'message'=>'服务暂时不可用，请稍后重试（编号 ' . $errorId . '）','data'=>null,'error_id'=>$errorId],503);
    }
}
