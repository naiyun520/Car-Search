<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\WechatService;
use app\service\CryptoService;
use app\service\ApiRequestService;
use think\facade\Db;

class AuthController extends BaseController
{
    public function login()
    {
        try {
            ApiRequestService::requireProtocol($this->request);
            $payload = ApiRequestService::json($this->request);
        } catch (\InvalidArgumentException $error) {
            return $this->fail($error->getMessage(),422,422);
        }
        $code = trim((string) ($payload['code'] ?? ''));
        if ($code === '') return $this->fail('登录凭证不能为空');
        try { $session = WechatService::codeToSession($code); } catch (\Throwable $e) { return $this->fail('登录失败，请稍后重试', 503, 503); }
        $now = date('Y-m-d H:i:s');
        $user = Db::name('user')->where('openid', $session['openid'])->find();
        $sessionKeyCipher = CryptoService::encrypt(['value'=>(string) ($session['session_key'] ?? '')]);
        if (!$user) {
            $id = Db::name('user')->insertGetId(['openid'=>$session['openid'],'unionid'=>$session['unionid'] ?? null,'nickname'=>'微信用户','session_key_cipher'=>$sessionKeyCipher,'status'=>1,'last_login_at'=>$now,'created_at'=>$now,'updated_at'=>$now]);
            $user = Db::name('user')->where('id', $id)->find();
        } else {
            Db::name('user')->where('id', $user['id'])->update(['session_key_cipher'=>$sessionKeyCipher,'last_login_at'=>$now,'updated_at'=>$now]);
        }
        $token = bin2hex(random_bytes(32));
        Db::name('user_token')->insert(['user_id'=>$user['id'],'token_hash'=>hash('sha256',$token),'expires_at'=>date('Y-m-d H:i:s', strtotime('+2 hours')),'created_at'=>$now]);
        return $this->ok(['token'=>$token,'user'=>self::safeUser($user)]);
    }

    public function me() { return $this->ok(self::safeUser($this->request->user)); }

    public function updateProfile()
    {
        $data = ['nickname'=>mb_substr(trim((string) $this->request->post('nickname', '微信用户')),0,64),'avatar_url'=>mb_substr(trim((string) $this->request->post('avatar_url','')),0,500),'updated_at'=>date('Y-m-d H:i:s')];
        Db::name('user')->where('id',$this->request->user['id'])->update($data);
        return $this->ok(array_merge(self::safeUser($this->request->user),$data),'资料已更新');
    }

    private static function safeUser(array $user): array
    {
        return ['id'=>$user['id'],'nickname'=>$user['nickname'],'avatar_url'=>$user['avatar_url'],'phone_masked'=>$user['phone'] ? substr($user['phone'],0,3).'****'.substr($user['phone'],-4) : ''];
    }
}
