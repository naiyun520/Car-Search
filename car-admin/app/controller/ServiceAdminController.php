<?php

declare(strict_types=1);

namespace app\controller;

use app\BaseController;
use app\service\CryptoService;
use app\service\InputValidator;
use app\service\ProviderQueryException;
use app\service\ProviderService;
use think\facade\Db;

class ServiceAdminController extends BaseController
{
    private const STATUSES = [0, 1, 2];
    private const METHODS = ['GET', 'POST'];
    private const INPUT_TYPES = ['text', 'name', 'plate', 'plate_prefix', 'plate_or_vin', 'vin', 'idcard', 'identity'];

    public function index()
    {
        $rows = Db::name('service')->field('id,code,payment_product_id,name,short_name,description,icon,input_schema,result_schema,request_url_cipher,request_method,response_code_path,response_success_value,response_data_path,sale_price,cost_price,sort,status,updated_at')->order('sort')->order('id')->select()->toArray();
        foreach ($rows as &$row) $row = $this->safeService($row);
        return $this->ok($rows);
    }

    public function create()
    {
        $serviceId = (int) $this->request->post('service_id', 0);
        if ($serviceId > 0) return $this->update($serviceId);
        try { $data = $this->validatedData(null); } catch (\InvalidArgumentException $error) { return $this->fail($this->safeError($error)); }
        if (Db::name('service')->where('code', $data['code'])->count() > 0) return $this->fail('服务编码已存在');
        $now = date('Y-m-d H:i:s');
        $data += [
            'provider_api_id'=>0,
            'provider_key_cipher'=>CryptoService::encrypt(['key'=>'']),
            'created_at'=>$now,
            'updated_at'=>$now,
        ];
        try {
            $id = Db::name('service')->insertGetId($data);
        } catch (\Throwable $error) {
            if (str_contains($error->getMessage(), 'Duplicate')) return $this->fail('服务编码已存在');
            throw $error;
        }
        $this->audit('service.create', (string) $id, ['code'=>$data['code']]);
        return $this->ok(['id'=>$id], '接口服务已创建');
    }

    /**
     * Test the saved provider configuration through the exact same validation,
     * transport and response-normalization path used by paid orders.
     */
    public function test(int $id)
    {
        $service = Db::name('service')->where('id', $id)->find();
        if (!$service) return $this->fail('接口服务不存在', 404, 404);

        try {
            $schema = json_decode((string) $service['input_schema'], true);
            if (!is_array($schema) || !$schema) throw new \RuntimeException('接口请求字段配置无效');
            $input = InputValidator::validate($schema, (array) $this->request->post('input', []));
        } catch (\InvalidArgumentException $error) {
            return $this->fail($error->getMessage(), 422, 422);
        } catch (\Throwable $error) {
            return $this->fail($this->safeError($error), 422, 422);
        }

        $startedAt = hrtime(true);
        try {
            $response = ProviderService::query($service, $input);
            $duration = (int) round((hrtime(true) - $startedAt) / 1000000);
            $result = [
                'success'=>true,
                'provider_code'=>(string) ($response['code'] ?? ''),
                'provider_request_id'=>(string) ($response['request_id'] ?? ''),
                'duration_ms'=>$duration,
                'data'=>$response['data'] ?? [],
            ];
            $this->audit('service.test', (string) $id, ['success'=>true,'provider_code'=>$result['provider_code'],'duration_ms'=>$duration]);
            return $this->ok($result, '接口测试成功');
        } catch (\Throwable $error) {
            $duration = (int) round((hrtime(true) - $startedAt) / 1000000);
            $providerCode = $error instanceof ProviderQueryException ? $error->providerCode : 'transport_error';
            $requestId = $error instanceof ProviderQueryException ? $error->providerRequestId : '';
            $message = $this->safeError($error);
            $this->audit('service.test', (string) $id, ['success'=>false,'provider_code'=>$providerCode,'duration_ms'=>$duration,'error'=>mb_substr($message,0,300)]);
            return $this->ok([
                'success'=>false,
                'provider_code'=>$providerCode,
                'provider_request_id'=>$requestId,
                'duration_ms'=>$duration,
                'error'=>$message,
            ], '接口测试失败');
        }
    }

    public function update(int $id)
    {
        $bodyId = (int) $this->request->post('service_id', 0);
        if ($bodyId > 0 && $bodyId !== $id) return $this->fail('接口服务ID不一致，请刷新页面后重试');
        $service = Db::name('service')->where('id', $id)->find();
        if (!$service) return $this->fail('接口服务不存在', 404, 404);
        try { $data = $this->validatedData($service); } catch (\InvalidArgumentException $error) { return $this->fail($this->safeError($error)); }
        $data['updated_at'] = date('Y-m-d H:i:s');
        Db::name('service')->where('id', $id)->update($data);
        $this->audit('service.update', (string) $id, ['fields'=>array_keys($data)]);
        return $this->ok(['id'=>$id], '接口服务已保存');
    }

    public function uploadIcon(int $id)
    {
        $service = Db::name('service')->where('id', $id)->find();
        if (!$service) return $this->fail('接口服务不存在', 404, 404);
        $file = $this->request->file('icon');
        if (!$file) return $this->fail('请选择图标文件');

        $source = $file->getPathname();
        $size = is_file($source) ? filesize($source) : false;
        if ($size === false || $size < 1 || $size > 48 * 1024) return $this->fail('图标大小需在 48KB 以内');
        $image = @getimagesize($source);
        if (!$image || $image[0] < 32 || $image[1] < 32 || $image[0] > 1024 || $image[1] > 1024) {
            return $this->fail('图标尺寸需在 32×32 至 1024×1024 像素之间');
        }
        $mime = class_exists(\finfo::class) ? (new \finfo(FILEINFO_MIME_TYPE))->file($source) : (string) ($image['mime'] ?? '');
        $extensions = ['image/png'=>'png','image/jpeg'=>'jpg','image/webp'=>'webp'];
        if (!isset($extensions[$mime])) return $this->fail('图标仅支持 PNG、JPG 或 WebP 格式');

        $directory = root_path() . 'runtime' . DIRECTORY_SEPARATOR . 'uploads' . DIRECTORY_SEPARATOR . 'service-icons';
        if (!is_dir($directory) && !mkdir($directory, 0750, true) && !is_dir($directory)) return $this->fail('服务器图标目录不可写', 500, 500);
        $filename = bin2hex(random_bytes(16)) . '.' . $extensions[$mime];
        $target = $directory . DIRECTORY_SEPARATOR . $filename;
        if (!move_uploaded_file($source, $target) || !is_file($target)) return $this->fail('图标保存失败，请检查 runtime 目录权限', 500, 500);
        @chmod($target, 0640);

        try { Db::name('service')->where('id', $id)->update(['icon'=>$filename,'updated_at'=>date('Y-m-d H:i:s')]); }
        catch (\Throwable $error) { @unlink($target); throw $error; }
        $this->deleteIconFile((string) ($service['icon'] ?? ''), $filename);
        $this->audit('service.icon.upload', (string) $id, ['filename'=>$filename,'mime'=>$mime,'size'=>$size]);
        return $this->ok(['id'=>$id,'icon'=>$filename,'icon_url'=>'/api/v1/service-icon/' . $filename], '服务图标已更新');
    }

    public function sort()
    {
        $ids = array_values(array_filter(array_map('intval', (array) $this->request->post('ids', []))));
        $current = Db::name('service')->order('sort')->order('id')->column('id');
        $sorted = $ids;
        sort($sorted, SORT_NUMERIC);
        sort($current, SORT_NUMERIC);
        if (count($ids) < 2 || $sorted !== $current) return $this->fail('排序数据不完整，请刷新页面后重试');
        $now = date('Y-m-d H:i:s');
        Db::transaction(function () use ($ids, $now) {
            foreach ($ids as $index => $id) {
                Db::name('service')->where('id', $id)->update(['sort'=>$index + 1, 'updated_at'=>$now]);
            }
        });
        $this->audit('service.sort', '', ['ids'=>$ids]);
        return $this->ok(null, '排序已保存');
    }

    public function delete(int $id)
    {
        $service = Db::name('service')->where('id', $id)->find();
        if (!$service) return $this->fail('接口服务不存在', 404, 404);
        if (Db::name('order')->where('service_id', $id)->count() > 0) {
            return $this->fail('该接口已有历史订单，不能删除，请将状态设为隐藏');
        }
        Db::name('service')->where('id', $id)->delete();
        $this->deleteIconFile((string) ($service['icon'] ?? ''));
        $this->audit('service.delete', (string) $id, ['code'=>$service['code']]);
        return $this->ok(null, '接口服务已删除');
    }

    private function validatedData(?array $existing): array
    {
        $code = $existing ? (string) $existing['code'] : trim((string) $this->request->post('code', ''));
        $name = trim((string) $this->request->post('name', ''));
        $shortName = trim((string) $this->request->post('short_name', ''));
        $description = trim((string) $this->request->post('description', ''));
        $paymentProductId = trim((string) $this->request->post('payment_product_id', ''));
        if (!preg_match('/^[a-z][a-z0-9_]{2,39}$/', $code)) throw new \InvalidArgumentException('服务编码需为3-40位小写字母、数字或下划线，且以字母开头');
        if ($name === '' || mb_strlen($name) > 80) throw new \InvalidArgumentException('服务名称不能为空且不能超过80字');
        if ($shortName === '' || mb_strlen($shortName) > 40) throw new \InvalidArgumentException('前端简称不能为空且不能超过40字');
        if (mb_strlen($description) > 255) throw new \InvalidArgumentException('服务说明不能超过255字');
        if (mb_strlen($paymentProductId) > 128 || preg_match('/[\x00-\x20\x7f]/u', $paymentProductId)) throw new \InvalidArgumentException('微信支付道具ID格式不正确');

        $status = (int) $this->request->post('status', 0);
        $method = strtoupper(trim((string) $this->request->post('request_method', 'GET')));
        if (!in_array($status, self::STATUSES, true)) throw new \InvalidArgumentException('接口状态无效');
        if (!in_array($method, self::METHODS, true)) throw new \InvalidArgumentException('仅支持 GET 或 POST 请求');
        if ($status === 1 && $paymentProductId === '') throw new \InvalidArgumentException('运行状态必须填写微信支付道具ID');

        $inputSchema = $this->validateInputSchema((array) $this->request->post('input_schema', []));
        $resultSchema = $this->validateResultSchema((array) $this->request->post('result_schema', []));
        $url = trim((string) $this->request->post('api_url', ''));
        if ($url !== '') $this->validateUrl($url);
        if ($existing === null && $url === '') throw new \InvalidArgumentException('请填写完整接口 URL');
        if ($status === 1 && $url === '' && empty($existing['request_url_cipher'])) throw new \InvalidArgumentException('运行状态必须配置完整接口 URL');
        if ($status === 1 && $url === '' && $existing) {
            try {
                if (empty(CryptoService::decrypt((string) $existing['request_url_cipher'])['url'])) throw new \InvalidArgumentException('运行状态必须配置完整接口 URL');
            } catch (\InvalidArgumentException $error) {
                throw $error;
            } catch (\Throwable) {
                throw new \InvalidArgumentException('原接口 URL 无法读取，请重新填写');
            }
        }

        $salePrice = (float) $this->request->post('sale_price', 0);
        $costPrice = (float) $this->request->post('cost_price', 0);
        if ($salePrice < 0.01 || $salePrice > 99999) throw new \InvalidArgumentException('销售价格需在0.01-99999元之间');
        if ($status === 1 && $salePrice < 1) throw new \InvalidArgumentException('运行中的服务售价不能低于1元（Apple 支付最低金额）');
        if ($costPrice < 0 || $costPrice > 99999) throw new \InvalidArgumentException('成本价格范围无效');

        $data = [
            'code'=>$code,
            'payment_product_id'=>$paymentProductId,
            'name'=>$name,
            'short_name'=>$shortName,
            'description'=>$description,
            'icon'=>$existing ? (string) ($existing['icon'] ?? '') : '',
            'input_schema'=>json_encode($inputSchema, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            'result_schema'=>json_encode($resultSchema, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES),
            'request_method'=>$method,
            'response_code_path'=>mb_substr(trim((string) $this->request->post('response_code_path', 'code')), 0, 100),
            'response_success_value'=>mb_substr(trim((string) $this->request->post('response_success_value', '200')), 0, 50),
            'response_data_path'=>mb_substr(trim((string) $this->request->post('response_data_path', 'data')), 0, 100),
            'sale_price'=>number_format($salePrice, 2, '.', ''),
            'cost_price'=>number_format($costPrice, 4, '.', ''),
            'sort'=>$existing ? (int) $existing['sort'] : ((int) Db::name('service')->max('sort')) + 1,
            'status'=>$status,
        ];
        if ($url !== '') $data['request_url_cipher'] = CryptoService::encrypt(['url'=>$url]);
        return $data;
    }

    private function validateInputSchema(array $rows): array
    {
        $schema = [];
        $keys = [];
        foreach ($rows as $row) {
            if (!is_array($row)) continue;
            $key = trim((string) ($row['key'] ?? ''));
            $label = trim((string) ($row['label'] ?? ''));
            $type = trim((string) ($row['type'] ?? 'text'));
            if (!preg_match('/^[a-zA-Z][a-zA-Z0-9_]{0,49}$/', $key)) throw new \InvalidArgumentException('请求参数名格式不正确');
            if (isset($keys[$key])) throw new \InvalidArgumentException('请求参数名不能重复');
            if ($label === '' || mb_strlen($label) > 50) throw new \InvalidArgumentException('请求字段名称不能为空且不能超过50字');
            if (!in_array($type, self::INPUT_TYPES, true)) throw new \InvalidArgumentException('请求字段类型无效');
            $keys[$key] = true;
            $schema[] = ['key'=>$key, 'label'=>$label, 'type'=>$type, 'required'=>(bool) ($row['required'] ?? false)];
        }
        if (!$schema) throw new \InvalidArgumentException('至少配置一个用户输入字段');
        return $schema;
    }

    private function validateResultSchema(array $rows): array
    {
        $schema = [];
        foreach ($rows as $row) {
            if (!is_array($row)) continue;
            $key = trim((string) ($row['key'] ?? ''));
            $label = trim((string) ($row['label'] ?? ''));
            if (!preg_match('/^[a-zA-Z0-9_.-]{1,100}$/', $key)) throw new \InvalidArgumentException('结果字段路径格式不正确');
            if ($label === '' || mb_strlen($label) > 50) throw new \InvalidArgumentException('结果字段名称不能为空且不能超过50字');
            if (array_key_exists($key, $schema)) throw new \InvalidArgumentException('结果字段路径不能重复');
            $schema[$key] = $label;
        }
        if (!$schema) throw new \InvalidArgumentException('至少配置一个结果展示字段');
        return $schema;
    }

    private function validateUrl(string $url): void
    {
        if (strlen($url) > 3000 || preg_match('/[\x00-\x20\x7f]/u', $url)) throw new \InvalidArgumentException('接口 URL 格式不正确，不能包含空格或控制字符');
        $parts = parse_url($url);
        if (!is_array($parts) || strtolower((string) ($parts['scheme'] ?? '')) !== 'https' || empty($parts['host'])) throw new \InvalidArgumentException('接口 URL 必须使用 HTTPS');
        $host = (string) $parts['host'];
        if (!filter_var($host, FILTER_VALIDATE_IP) && !preg_match('/^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i', $host)) throw new \InvalidArgumentException('接口域名格式不正确');
        if (isset($parts['user']) || isset($parts['pass'])) throw new \InvalidArgumentException('接口 URL 不允许包含账号认证信息');
    }

    private function safeService(array $row): array
    {
        $url = '';
        $urlError = false;
        try { $url = (string) (CryptoService::decrypt((string) ($row['request_url_cipher'] ?? ''))['url'] ?? ''); }
        catch (\Throwable) { $urlError = !empty($row['request_url_cipher']); }
        unset($row['request_url_cipher']);
        $row['id'] = (int) $row['id'];
        $row['status'] = (int) $row['status'];
        $row['sort'] = (int) $row['sort'];
        $row['api_url_configured'] = $url !== '';
        $row['api_url'] = $url;
        $row['api_url_error'] = $urlError;
        $row['input_schema'] = json_decode((string) $row['input_schema'], true) ?: [];
        $results = json_decode((string) $row['result_schema'], true) ?: [];
        $row['result_schema'] = array_map(fn($key, $label) => ['key'=>$key, 'label'=>$label], array_keys($results), array_values($results));
        return $row;
    }

    private function deleteIconFile(string $filename, string $except = ''): void
    {
        if ($filename === $except || !preg_match('/^[a-f0-9]{32}\.(?:png|jpg|webp)$/', $filename)) return;
        $path = root_path() . 'runtime' . DIRECTORY_SEPARATOR . 'uploads' . DIRECTORY_SEPARATOR . 'service-icons' . DIRECTORY_SEPARATOR . $filename;
        if (is_file($path)) @unlink($path);
    }

    private function audit(string $action, string $targetId, array $detail): void
    {
        Db::name('audit_log')->insert(['admin_id'=>$this->request->admin['id'],'action'=>$action,'target_type'=>'service','target_id'=>$targetId,'detail'=>json_encode($detail,JSON_UNESCAPED_UNICODE),'ip'=>$this->request->ip(),'created_at'=>date('Y-m-d H:i:s')]);
    }
}
