<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use think\facade\Db;

class PublicController extends BaseController
{
    public function bootstrap()
    {
        $query = Db::name('service')->field('id,code,name,short_name,description,icon,input_schema,result_schema,status,updated_at')->whereIn('status',[0,1]);
        $services = $query->order('sort','asc')->order('id','asc')->select()->toArray();
        $catalogParts = [];
        foreach ($services as &$service) {
            $catalogParts[] = $service['id'] . ':' . $service['code'] . ':' . $service['status'] . ':' . ($service['updated_at'] ?? '');
            $service['input_schema'] = json_decode($service['input_schema'],true) ?: [];
            $service['result_schema'] = json_decode($service['result_schema'],true) ?: [];
            $service['status'] = (int) $service['status'];
            unset($service['id'],$service['updated_at']);
        }
        unset($service);
        $now = date('Y-m-d H:i:s');
        $announcement = Db::name('announcement')->field('id,title,content,suppress_hours,updated_at')->where('status',1)->where(function($query) use ($now) { $query->whereNull('start_at')->whereOr('start_at','<=',$now); })->where(function($query) use ($now) { $query->whereNull('end_at')->whereOr('end_at','>=',$now); })->order('id','desc')->find();
        $settings = Db::name('setting')->whereIn('key',['operator_name','privacy_contact','privacy_effective_date','customer_service_phone','customer_service_hours','disclaimer','privacy_retention_days'])->column('value','key');
        $catalogVersion = hash('sha256', implode('|', $catalogParts));
        return $this->ok(['app_version'=>'20260815-query-v1','protocol_version'=>\app\service\CheckoutService::CLIENT_PROTOCOL,'catalog_version'=>$catalogVersion,'services'=>$services,'announcement'=>$announcement,'settings'=>$settings]);
    }

    public function serviceIcon(string $file)
    {
        if (!preg_match('/^[a-f0-9]{32}\.(?:png|jpg|webp)$/', $file)) return response('', 404);
        $path = root_path() . 'runtime' . DIRECTORY_SEPARATOR . 'uploads' . DIRECTORY_SEPARATOR . 'service-icons' . DIRECTORY_SEPARATOR . $file;
        if (!is_file($path)) return response('', 404);
        $extension = strtolower(pathinfo($file, PATHINFO_EXTENSION));
        $mime = ['png'=>'image/png','jpg'=>'image/jpeg','webp'=>'image/webp'][$extension] ?? 'application/octet-stream';
        $data = file_get_contents($path);
        if ($data === false || strlen($data) > 48 * 1024) return response('', 404);
        return response($data, 200, [
            'Content-Type'=>$mime,
            'Cache-Control'=>'public, max-age=31536000, immutable',
            'X-Content-Type-Options'=>'nosniff',
        ]);
    }
}
