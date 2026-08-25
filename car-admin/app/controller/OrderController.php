<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\CheckoutService;
use think\facade\Db;

/**
 * 订单历史与旧客户端兼容层。
 * 新结算协议只使用固定 /checkout 端点，服务标识不再依赖动态路由参数。
 */
final class OrderController extends BaseController
{
    public function create()
    {
        return $this->upgradeRequired();
    }

    public function createForService(int $service_id = 0)
    {
        return $this->upgradeRequired();
    }

    public function index()
    {
        $rows = Db::name('order')->alias('o')->leftJoin('payment p','p.order_id=o.id')
            ->where('o.user_id',$this->request->user['id'])->whereNotNull('o.paid_at')
            ->field('o.*,p.wechat_order_type,p.refund_status')
            ->order('o.id','desc')->paginate([
                'list_rows'=>max(1,min(30,(int) $this->request->get('page_size',10))),
                'page'=>max(1,(int) $this->request->get('page',1)),
            ])->toArray();
        $rows['data'] = array_map(static function (array $row): array {
            $safe = CheckoutService::safeOrder($row);
            $safe['payment_platform'] = (int) ($row['wechat_order_type'] ?? -1) === 7 ? 'ios' : 'wechat';
            $safe['refund_status'] = (string) ($row['refund_status'] ?? 'none');
            return $safe;
        }, $rows['data']);
        return $this->ok($rows);
    }

    public function recoverable()
    {
        return $this->ok(CheckoutService::recoverable($this->request->user));
    }

    public function payment(string $orderNo)
    {
        return $this->ok(CheckoutService::payment($this->request->user,$orderNo));
    }

    public function detail(string $orderNo)
    {
        return $this->ok(CheckoutService::status($this->request->user,$orderNo));
    }

    public function query(string $orderNo)
    {
        return $this->ok(CheckoutService::query($this->request->user,$orderNo));
    }

    private function upgradeRequired()
    {
        return $this->fail('当前客户端结算协议已停用，请完全退出并重新打开最新版小程序',426,426);
    }
}

