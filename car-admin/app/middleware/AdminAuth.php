<?php

declare(strict_types=1);

namespace app\middleware;

use Closure;
use think\facade\Db;
use think\Request;

class AdminAuth
{
    public function handle(Request $request, Closure $next)
    {
        $token = preg_replace('/^Bearer\s+/i', '', $request->header('authorization', ''));
        $session = $token ? Db::name('admin_token')->where('token_hash', hash('sha256', $token))->where('expires_at', '>', date('Y-m-d H:i:s'))->find() : null;
        if (!$session) {
            return json(['code' => 401, 'message' => '管理端登录已失效', 'data' => null], 401);
        }
        $admin = Db::name('admin')->where('id', $session['admin_id'])->where('status', 1)->find();
        if (!$admin) return json(['code'=>403,'message'=>'管理员账号不可用','data'=>null],403);
        $request->admin = $admin;
        if (!empty($admin['must_change_password']) && trim($request->pathinfo(), '/') !== 'admin-api/password') {
            return json(['code'=>428,'message'=>'首次登录必须先修改默认密码','data'=>null],428);
        }
        $response = $next($request);
        return $response->header([
            'Cache-Control'=>'no-store, no-cache, must-revalidate, private',
            'Pragma'=>'no-cache',
            'X-Content-Type-Options'=>'nosniff',
        ]);
    }
}
