<?php

declare(strict_types=1);

namespace app\middleware;

use Closure;
use think\facade\Db;
use think\Request;

class UserAuth
{
    public function handle(Request $request, Closure $next)
    {
        $token = preg_replace('/^Bearer\s+/i', '', $request->header('authorization', ''));
        $session = $token ? Db::name('user_token')->where('token_hash', hash('sha256', $token))->where('expires_at', '>', date('Y-m-d H:i:s'))->find() : null;
        if (!$session) {
            return json(['code' => 401, 'message' => '请先登录', 'data' => null], 401);
        }
        $user = Db::name('user')->where('id', $session['user_id'])->where('status', 1)->find();
        if (!$user) {
            return json(['code' => 403, 'message' => '账号不可用', 'data' => null], 403);
        }
        if (empty($user['session_key_cipher'])) {
            Db::name('user_token')->where('id', $session['id'])->delete();
            return json(['code' => 401, 'message' => '微信登录态需要刷新', 'data' => null], 401);
        }
        $request->user = $user;
        return $next($request);
    }
}
