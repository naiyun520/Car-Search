<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\ConfigService;
use app\service\CryptoService;
use app\service\PaymentLifecycleService;
use app\service\PaymentService;
use app\service\QueryRunnerService;
use think\facade\Db;

class AdminController extends BaseController
{
    public function login()
    {
        $username = trim((string)$this->request->post('username',''));
        $identityHash = hash('sha256', mb_strtolower($username) . '|' . $this->request->ip());
        $attempt = null;
        $throttleAvailable = true;
        try {
            $attempt = Db::name('admin_login_attempt')->where('identity_hash', $identityHash)->find();
        } catch (\Throwable) {
            return $this->fail('登录安全组件不可用，请先执行数据库升级',503,503);
        }
        if ($attempt && $attempt['locked_until'] && strtotime($attempt['locked_until']) > time()) return $this->fail('登录尝试过多，请15分钟后再试',429,429);
        try {
            $admin = Db::name('admin')->where('username',$username)->where('status',1)->find();
        } catch (\Throwable) {
            return $this->fail('数据库尚未正确安装或升级，请执行 php think car:upgrade',503,503);
        }
        if (!$admin || !password_verify((string)$this->request->post('password',''),$admin['password_hash'])) {
            if ($throttleAvailable) {
                $failCount = (int) ($attempt['fail_count'] ?? 0) + 1;
                try {
                    Db::name('admin_login_attempt')->strict(false)->replace()->insert([
                        'identity_hash'=>$identityHash,
                        'fail_count'=>$failCount >= 5 ? 0 : $failCount,
                        'locked_until'=>$failCount >= 5 ? date('Y-m-d H:i:s', strtotime('+15 minutes')) : null,
                        'updated_at'=>date('Y-m-d H:i:s'),
                    ]);
                } catch (\Throwable) { return $this->fail('登录安全组件不可用，请稍后重试',503,503); }
            }
            return $this->fail('账号或密码错误',401,401);
        }
        if ($throttleAvailable) {
            try { Db::name('admin_login_attempt')->where('identity_hash', $identityHash)->delete(); } catch (\Throwable) {}
        }
        $token = bin2hex(random_bytes(32)); $now = date('Y-m-d H:i:s');
        Db::name('admin_token')->insert(['admin_id'=>$admin['id'],'token_hash'=>hash('sha256',$token),'expires_at'=>date('Y-m-d H:i:s',strtotime('+8 hours')),'created_at'=>$now]);
        Db::name('admin')->where('id',$admin['id'])->update(['last_login_at'=>$now,'updated_at'=>$now]);
        return $this->ok(['token'=>$token,'admin'=>['id'=>$admin['id'],'username'=>$admin['username'],'must_change_password'=>(bool)($admin['must_change_password'] ?? false)]]);
    }

    public function dashboard()
    {
        $today = date('Y-m-d 00:00:00');
        $grossRevenue = (float) Db::name('order')->where('paid_at','>=',$today)->sum('amount');
        $refunds = (float) Db::name('payment')->where('refunded_at','>=',$today)->sum('refund_amount');
        $revenue = $grossRevenue - $refunds;
        $cost = (float) Db::name('order')->where('queried_at','>=',$today)->sum('cost_amount');
        return $this->ok(['users'=>Db::name('user')->count(),'orders'=>Db::name('order')->count(),'today_orders'=>Db::name('order')->where('created_at','>=',$today)->count(),'today_gross_revenue'=>number_format($grossRevenue,2,'.',''),'today_refunds'=>number_format($refunds,2,'.',''),'today_revenue'=>number_format($revenue,2,'.',''),'today_cost'=>number_format($cost,4,'.',''),'today_profit'=>number_format($revenue-$cost,2,'.',''),'failed_orders'=>Db::name('order')->where('status','query_failed')->count()]);
    }

    public function users()
    {
        $keyword = trim((string) $this->request->get('keyword',''));
        $query = Db::name('user')->field('id,nickname,avatar_url,phone,status,last_login_at,created_at');
        if ($keyword !== '') {
            $query->where(function ($builder) use ($keyword) {
                if (ctype_digit($keyword)) $builder->whereOr('id',(int) $keyword);
                $builder->whereOr('nickname','like','%' . $keyword . '%')->whereOr('phone','like','%' . $keyword . '%');
            });
        }
        return $this->ok($query->order('id','desc')->paginate(['list_rows'=>20,'page'=>max(1,(int)$this->request->get('page',1))])->toArray());
    }

    public function setUserStatus(int $id)
    {
        $status = (int)$this->request->post('status',1);
        if (!in_array($status,[0,1],true)) return $this->fail('状态无效');
        Db::name('user')->where('id',$id)->update(['status'=>$status,'updated_at'=>date('Y-m-d H:i:s')]); $this->audit('user.status','user',(string)$id,['status'=>$status]);
        return $this->ok(null,'用户状态已更新');
    }

    public function orders()
    {
        $query = Db::name('order')->alias('o')->leftJoin('user u','u.id=o.user_id')->leftJoin('payment p','p.order_id=o.id')->field('o.id,o.order_no,o.user_id,o.service_name,o.amount,o.status,o.paid_at,o.queried_at,o.created_at,u.nickname,u.phone,p.callback_payload,p.delivery_status,p.delivery_attempts,p.delivery_last_error,p.delivered_at,p.refund_status,p.refund_order_no,p.refund_amount,p.refund_reason,p.refund_last_error,p.refund_requested_at,p.refunded_at');
        if ($this->request->get('status','') !== '') $query->where('o.status',(string)$this->request->get('status'));
        $keyword = trim((string) $this->request->get('keyword',''));
        if ($keyword !== '') {
            $query->where(function ($builder) use ($keyword) {
                $builder->whereOr('o.order_no','like','%' . $keyword . '%')->whereOr('u.nickname','like','%' . $keyword . '%')->whereOr('u.phone','like','%' . $keyword . '%');
                if (ctype_digit($keyword)) $builder->whereOr('o.user_id',(int) $keyword);
            });
        }
        $page = $query->order('o.id','desc')->paginate(['list_rows'=>20,'page'=>max(1,(int)$this->request->get('page',1))])->toArray();
        foreach ($page['data'] as &$row) {
            $wechatOrder = json_decode((string) ($row['callback_payload'] ?? ''), true);
            $orderType = is_array($wechatOrder) && array_key_exists('order_type', $wechatOrder)
                ? (int) $wechatOrder['order_type']
                : null;
            $row['payment_platform'] = in_array($orderType, [7,8], true)
                ? 'ios'
                : (in_array($orderType, [0,1], true) ? 'wechat' : 'unknown');
            unset($row['callback_payload']);
        }
        unset($row);
        return $this->ok($page);
    }

    public function deleteOrders()
    {
        $orderNos = array_values(array_unique(array_filter(array_map('trim', (array) $this->request->post('order_nos', [])))));
        if (!$orderNos) return $this->fail('请选择要删除的订单');
        if (count($orderNos) > 200) return $this->fail('单次最多删除200笔订单');
        $rows = Db::name('order')->alias('o')->leftJoin('payment p','p.order_id=o.id')->whereIn('o.order_no', $orderNos)
            ->field('o.id,o.order_no,o.status,o.paid_at,p.status payment_status')->select()->toArray();
        if (!$rows) return $this->fail('所选订单不存在或已被删除');
        foreach ($rows as $row) {
            if (!empty($row['paid_at']) || !in_array((string) ($row['payment_status'] ?? ''),['created','cancelled'],true)) {
                return $this->fail('已付款或进入支付处理的订单属于财务凭证，不能物理删除');
            }
        }
        $ids = array_column($rows, 'id');
        Db::transaction(function () use ($ids) {
            Db::name('payment')->whereIn('order_id', $ids)->delete();
            Db::name('order')->whereIn('id', $ids)->delete();
        });
        $deletedNos = array_column($rows, 'order_no');
        $this->audit('order.batch_delete', 'order', (string) ($deletedNos[0] ?? ''), ['order_nos'=>$deletedNos,'count'=>count($deletedNos)]);
        return $this->ok(null, '已删除 ' . count($deletedNos) . ' 笔订单');
    }

    public function orderDetail(string $orderNo)
    {
        $row = Db::name('order')->alias('o')->leftJoin('user u','u.id=o.user_id')->leftJoin('payment p','p.order_id=o.id')
            ->where('o.order_no',$orderNo)
            ->field('o.*,u.nickname,u.phone,p.transaction_id,p.status payment_status,p.pay_env,p.callback_payload,p.last_checked_at,p.delivery_status,p.delivery_attempts,p.delivery_attempted_at,p.delivery_last_error,p.delivered_at,p.refund_status,p.refund_order_no,p.refund_amount,p.refund_reason,p.refund_last_error,p.refund_requested_at,p.refunded_at')
            ->find();
        if (!$row) return $this->fail('订单不存在',404,404);
        try {
            $row['input'] = $this->decryptOrderValue((string) ($row['input_cipher'] ?? ''), '订单查询输入');
            $result = $this->decryptOrderValue((string) ($row['result_cipher'] ?? ''), '订单查询结果');
        } catch (\RuntimeException $error) { return $this->fail($error->getMessage(), 500, 500); }
        $row['result'] = $result['items'] ?? [];
        $wechatOrder = json_decode((string) ($row['callback_payload'] ?? ''), true);
        if (is_array($wechatOrder)) {
            unset($wechatOrder['token']);
            $row['wechat_order'] = $wechatOrder;
        }
        $row['wechat_order'] ??= [];
        unset($row['callback_payload']);
        unset($row['input_cipher'],$row['result_cipher'],$row['service_snapshot_cipher']);
        return $this->ok($row);
    }

    public function syncPayment(string $orderNo)
    {
        $row = Db::name('order')->alias('o')->join('user u','u.id=o.user_id')->where('o.order_no',$orderNo)->field('o.*,u.openid')->find();
        if (!$row) return $this->fail('订单不存在',404,404);
        if (!in_array((string) $row['status'], ['pending_payment','payment_review'], true)) return $this->ok(['status'=>$row['status']],'订单状态无需同步');
        try { $status = PaymentService::reconcile($row,$row); }
        catch (\Throwable $error) { return $this->fail($this->safeError($error),503,503); }
        if ($status === 'paid') {
            try { $result = QueryRunnerService::runForOrderNo($orderNo); $status = $result['status']; }
            catch (\Throwable $error) { return $this->fail('支付已确认但查询暂未启动：' . $this->safeError($error),503,503); }
        }
        $this->audit('order.payment.sync','order',$orderNo,['status'=>$status]);
        return $this->ok(['status'=>$status],$status === 'success' ? '支付已确认并完成查询' : ($status === 'paid' ? '微信支付状态已同步' : '微信暂未确认该订单支付成功'));
    }

    public function retryOrder(string $orderNo)
    {
        $order = Db::name('order')->where('order_no',$orderNo)->find();
        if (!$order) return $this->fail('订单不存在',404,404);
        if ($order['status'] !== 'query_failed') return $this->fail('仅查询失败订单可重试');
        $this->audit('order.retry','order',$orderNo,[]);
        try { $result = QueryRunnerService::retryFromAdmin($orderNo); }
        catch (\Throwable $error) { return $this->fail('查询启动失败：' . $this->safeError($error),503,503); }
        if ($result['status'] === 'success') return $this->ok(['status'=>'success'],'订单已恢复并完成查询');
        if ($result['status'] === 'query_failed') {
            $reason = (string) Db::name('order')->where('order_no',$orderNo)->value('query_last_error');
            return $this->fail('查询再次失败' . ($reason !== '' ? '：' . mb_substr($reason,0,300) : ''),504,504);
        }
        return $this->ok(['status'=>$result['status']],'订单已恢复为待查询');
    }

    public function retryDelivery(string $orderNo)
    {
        try { $status = PaymentLifecycleService::deliver($orderNo); }
        catch (\Throwable $error) { return $this->fail($this->safeError($error),503,503); }
        $this->audit('order.delivery.retry','order',$orderNo,['status'=>$status]);
        return $this->ok(['status'=>$status],'微信发货状态已确认');
    }

    public function refundOrder(string $orderNo)
    {
        $reason = (string) $this->request->post('reason','2');
        try { $status = PaymentLifecycleService::requestRefund($orderNo,$reason); }
        catch (\InvalidArgumentException $error) { return $this->fail($this->safeError($error)); }
        catch (\Throwable $error) { return $this->fail($this->safeError($error),503,503); }
        $this->audit('order.refund','order',$orderNo,['reason'=>$reason,'status'=>$status]);
        return $this->ok(['status'=>$status],$status === 'refunded' ? '订单已退款' : '退款申请已提交微信处理');
    }

    public function reconcileRefund(string $orderNo)
    {
        try { $status = PaymentLifecycleService::reconcileRefund($orderNo); }
        catch (\Throwable $error) { return $this->fail($this->safeError($error),503,503); }
        $this->audit('order.refund.reconcile','order',$orderNo,['status'=>$status]);
        return $this->ok(['status'=>$status],$status === 'refunded' ? '退款已完成' : ($status === 'failed' ? '退款失败，可重新申请' : '退款仍在处理中'));
    }

    public function announcements()
    {
        return $this->ok(Db::name('announcement')->order('id','desc')->select()->toArray());
    }

    public function saveAnnouncement()
    {
        $id=(int)$this->request->post('id',0); $title=trim((string)$this->request->post('title','')); $content=trim((string)$this->request->post('content',''));
        if ($title==='' || $content==='') return $this->fail('标题和内容不能为空');
        $now=date('Y-m-d H:i:s'); $data=['title'=>mb_substr($title,0,100),'content'=>$content,'suppress_hours'=>max(1,min(720,(int)$this->request->post('suppress_hours',24))),'status'=>(int)$this->request->post('status',1),'start_at'=>$this->request->post('start_at') ?: null,'end_at'=>$this->request->post('end_at') ?: null,'updated_at'=>$now];
        $id ? Db::name('announcement')->where('id',$id)->update($data) : $id=Db::name('announcement')->insertGetId(array_merge($data,['created_at'=>$now]));
        $this->audit('announcement.save','announcement',(string)$id,[]); return $this->ok(['id'=>$id],'公告已保存');
    }

    public function settings()
    {
        return $this->ok(Db::name('setting')->column('value','key'));
    }

    public function saveSettings()
    {
        $allowed=['operator_name','privacy_contact','privacy_effective_date','customer_service_phone','customer_service_hours','disclaimer','privacy_retention_days']; $values=(array)$this->request->post('settings',[]); $now=date('Y-m-d H:i:s');
        $operator = trim((string) ($values['operator_name'] ?? ''));
        $contact = trim((string) ($values['privacy_contact'] ?? ''));
        $effectiveDate = trim((string) ($values['privacy_effective_date'] ?? ''));
        $retentionDays = (int) ($values['privacy_retention_days'] ?? 30);
        if ($operator === '' || mb_strlen($operator) > 120) return $this->fail('请填写不超过120字的运营主体全称');
        if ($contact === '' || mb_strlen($contact) > 120) return $this->fail('请填写不超过120字的个人信息保护联系方式');
        if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $effectiveDate)) return $this->fail('隐私规则生效日期格式不正确');
        if ($retentionDays < 1 || $retentionDays > 365) return $this->fail('查询数据保留天数需在1至365天之间');
        $values['operator_name']=$operator; $values['privacy_contact']=$contact; $values['privacy_effective_date']=$effectiveDate; $values['privacy_retention_days']=(string)$retentionDays;
        foreach (['customer_service_phone'=>50,'customer_service_hours'=>80,'disclaimer'=>2000] as $key=>$limit) {
            if (mb_strlen(trim((string) ($values[$key] ?? ''))) > $limit) return $this->fail('运营设置内容过长，请精简后保存');
        }
        foreach ($allowed as $key) if (array_key_exists($key,$values)) Db::name('setting')->strict(false)->replace()->insert(['key'=>$key,'value'=>trim((string)$values[$key]),'updated_at'=>$now]);
        $this->audit('settings.save','setting','',array_keys($values)); return $this->ok(null,'设置已保存');
    }

    public function paymentSettings()
    {
        $wechat = ConfigService::wechat();
        return $this->ok([
            'payment_enabled' => (string) ConfigService::value('payment_enabled', '0'),
            'wechat_app_id' => $wechat['app_id'],
            'wechat_offer_id' => $wechat['offer_id'],
            'wechat_pay_env' => $wechat['pay_env'],
            'app_secret_configured' => $wechat['app_secret'] !== '',
            'sandbox_app_key_configured' => $wechat['sandbox_app_key'] !== '',
            'production_app_key_configured' => $wechat['production_app_key'] !== '',
        ]);
    }

    public function savePaymentSettings()
    {
        $appId = trim((string) $this->request->post('wechat_app_id', ''));
        $offerId = trim((string) $this->request->post('wechat_offer_id', ''));
        $payEnv = (int) $this->request->post('wechat_pay_env', 0);
        $enabled = (string) $this->request->post('payment_enabled', '0') === '1';
        if ($appId !== '' && !preg_match('/^wx[a-zA-Z0-9]{16}$/', $appId)) return $this->fail('微信 AppID 格式不正确');
        if ($offerId !== '' && !preg_match('/^[a-zA-Z0-9_-]{1,64}$/', $offerId)) return $this->fail('OfferID 格式不正确，请从微信公众平台虚拟支付基础配置中完整复制');
        if (!in_array($payEnv, [0, 1], true)) return $this->fail('支付环境无效');

        $now = date('Y-m-d H:i:s');
        foreach (['wechat_app_id'=>$appId,'wechat_offer_id'=>$offerId] as $key => $value) {
            if ($value !== '') Db::name('setting')->strict(false)->replace()->insert(['key'=>$key,'value'=>$value,'updated_at'=>$now]);
        }
        Db::name('setting')->strict(false)->replace()->insert(['key'=>'wechat_pay_env','value'=>(string)$payEnv,'updated_at'=>$now]);
        $secureFields = [
            'wechat_app_secret' => trim((string) $this->request->post('wechat_app_secret', '')),
            'wechat_sandbox_app_key' => trim((string) $this->request->post('wechat_sandbox_app_key', '')),
            'wechat_production_app_key' => trim((string) $this->request->post('wechat_production_app_key', '')),
        ];
        foreach ($secureFields as $key => $value) if ($value !== '') ConfigService::saveSecure($key, $value);
        ConfigService::saveSecure('wechat_access_token', '');
        Db::name('setting')->strict(false)->replace()->insert(['key'=>'wechat_access_token_expires_at','value'=>'0','updated_at'=>$now]);

        $wechat = ConfigService::wechat();
        $selectedAppKey = $payEnv === 1 ? $wechat['sandbox_app_key'] : $wechat['production_app_key'];
        if ($enabled && in_array('', [$wechat['app_id'],$wechat['app_secret'],$wechat['offer_id'],$selectedAppKey], true)) {
            Db::name('setting')->strict(false)->replace()->insert(['key'=>'payment_enabled','value'=>'0','updated_at'=>$now]);
            return $this->fail('配置尚不完整：请填写 AppID、AppSecret、OfferID 和当前支付环境对应的 AppKey');
        }
        Db::name('setting')->strict(false)->replace()->insert(['key'=>'payment_enabled','value'=>$enabled?'1':'0','updated_at'=>$now]);
        $this->audit('payment.settings.save','setting','payment',[
            'payment_enabled'=>$enabled, 'wechat_app_id'=>$appId, 'wechat_offer_id'=>$offerId, 'wechat_pay_env'=>$payEnv,
            'updated_secrets'=>array_keys(array_filter($secureFields, fn($value) => $value !== '')),
        ]);
        return $this->ok(null, '支付配置已安全保存');
    }

    public function changePassword()
    {
        $admin=Db::name('admin')->where('id',$this->request->admin['id'])->find(); $new=(string)$this->request->post('new_password','');
        if (!password_verify((string)$this->request->post('old_password',''),$admin['password_hash'])) return $this->fail('原密码错误');
        $length = strlen($new);
        $groups = (int) preg_match('/[a-z]/',$new) + (int) preg_match('/[A-Z]/',$new) + (int) preg_match('/\d/',$new) + (int) preg_match('/[^a-zA-Z0-9]/',$new);
        if ($length < 12 || $length > 128 || $groups < 3) return $this->fail('新密码需为12至128位，并至少包含大写字母、小写字母、数字、特殊字符中的三类');
        Db::name('admin')->where('id',$admin['id'])->update(['password_hash'=>password_hash($new,PASSWORD_DEFAULT),'must_change_password'=>0,'updated_at'=>date('Y-m-d H:i:s')]); Db::name('admin_token')->where('admin_id',$admin['id'])->delete();
        return $this->ok(null,'密码已修改，请重新登录');
    }

    private function audit(string $action,string $targetType,string $targetId,array $detail): void
    {
        try {
            Db::name('audit_log')->insert(['admin_id'=>$this->request->admin['id'],'action'=>$action,'target_type'=>$targetType,'target_id'=>mb_substr($targetId,0,64),'detail'=>json_encode($detail,JSON_UNESCAPED_UNICODE),'ip'=>$this->request->ip(),'created_at'=>date('Y-m-d H:i:s')]);
        } catch (\Throwable $error) {
            \think\facade\Log::error('audit log failed | action=' . $action . ' | message=' . mb_substr($error->getMessage(),0,500));
        }
    }

    private function decryptOrderValue(string $cipher, string $label): array
    {
        if ($cipher === '') return [];
        try { return CryptoService::decrypt($cipher); }
        catch (\Throwable $error) { throw new \RuntimeException($label . '无法解密，请检查服务器主加密密钥是否与原环境一致'); }
    }

}
