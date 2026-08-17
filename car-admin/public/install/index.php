<?php

declare(strict_types=1);

session_start();

const APP_ROOT = __DIR__ . '/../..';
const ENV_FILE = APP_ROOT . '/.env';
const INSTALL_LOCK = APP_ROOT . '/runtime/install.lock';
const DEFAULT_ADMIN_USER = 'admin';
const DEFAULT_ADMIN_PASSWORD = '123456';

function escape(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES, 'UTF-8');
}

function installed(): bool
{
    if (is_file(INSTALL_LOCK)) return true;
    if (!is_file(ENV_FILE)) return false;
    $environment = parse_ini_file(ENV_FILE, true, INI_SCANNER_TYPED) ?: [];
    return (bool) ($environment['APP']['INSTALLED'] ?? false);
}

function environmentChecks(): array
{
    $runtime = APP_ROOT . '/runtime';
    return [
        ['PHP 版本', version_compare(PHP_VERSION, '8.0.0', '>='), PHP_VERSION . '（要求 ≥ 8.0）'],
        ['PDO MySQL', extension_loaded('pdo_mysql'), extension_loaded('pdo_mysql') ? '已启用' : '未启用'],
        ['OpenSSL', extension_loaded('openssl'), extension_loaded('openssl') ? '已启用' : '未启用'],
        ['cURL', extension_loaded('curl'), extension_loaded('curl') ? '已启用' : '未启用'],
        ['mbstring', extension_loaded('mbstring'), extension_loaded('mbstring') ? '已启用' : '未启用'],
        ['Composer 依赖', is_file(APP_ROOT . '/vendor/autoload.php'), is_file(APP_ROOT . '/vendor/autoload.php') ? '完整' : '缺少 vendor/autoload.php'],
        ['项目配置权限', is_writable(APP_ROOT) || (is_file(ENV_FILE) && is_writable(ENV_FILE)), '项目根目录需要临时可写'],
        ['运行目录权限', is_dir($runtime) ? is_writable($runtime) : is_writable(APP_ROOT), 'runtime 需要可写'],
    ];
}

function quoteEnv(string $value): string
{
    return '"' . str_replace(["\\", '"', "\r", "\n"], ["\\\\", '\\"', '', '\\n'], $value) . '"';
}

function table(string $prefix, string $name): string
{
    return '`' . $prefix . $name . '`';
}

function encryptData(array $data, string $secret): string
{
    $iv = random_bytes(12);
    $tag = '';
    $cipher = openssl_encrypt(json_encode($data, JSON_UNESCAPED_UNICODE), 'aes-256-gcm', hash('sha256', $secret, true), OPENSSL_RAW_DATA, $iv, $tag);
    if ($cipher === false) throw new RuntimeException('初始化加密失败');
    return base64_encode($iv . $tag . $cipher);
}

function writeEnvironment(array $database, string $encryptKey): void
{
    $content = implode(PHP_EOL, [
        '[APP]',
        'INSTALLED = true',
        'DEBUG = false',
        'DEFAULT_TIMEZONE = Asia/Shanghai',
        '',
        '[DATABASE]',
        'TYPE = mysql',
        'HOSTNAME = ' . quoteEnv($database['host']),
        'DATABASE = ' . quoteEnv($database['name']),
        'USERNAME = ' . quoteEnv($database['user']),
        'PASSWORD = ' . quoteEnv($database['password']),
        'HOSTPORT = ' . $database['port'],
        'CHARSET = utf8mb4',
        'PREFIX = ' . quoteEnv($database['prefix']),
        '',
        '[SECURITY]',
        'DATA_ENCRYPT_KEY = ' . quoteEnv($encryptKey),
        '',
        '[WECHAT]',
        'APP_ID =',
        'APP_SECRET =',
        'OFFER_ID =',
        'SANDBOX_APP_KEY =',
        'PRODUCTION_APP_KEY =',
        'PAY_ENV = 0',
        '',
    ]);
    $temporary = APP_ROOT . '/.env.installing';
    if (file_put_contents($temporary, $content, LOCK_EX) === false) throw new RuntimeException('无法写入 .env，请检查项目目录权限');
    if (is_file(ENV_FILE)) @copy(ENV_FILE, APP_ROOT . '/.env.backup.' . date('YmdHis'));
    if (!@rename($temporary, ENV_FILE)) {
        if (!is_file(ENV_FILE) || !@unlink(ENV_FILE) || !@rename($temporary, ENV_FILE)) throw new RuntimeException('无法生成正式 .env');
    }
    @chmod(ENV_FILE, 0640);
}

$checks = environmentChecks();
$checksPassed = !in_array(false, array_column($checks, 1), true);
$alreadyInstalled = installed();
$success = false;
$errors = [];
if (empty($_SESSION['install_csrf'])) $_SESSION['install_csrf'] = bin2hex(random_bytes(24));

if ($_SERVER['REQUEST_METHOD'] === 'POST' && !$alreadyInstalled) {
    try {
        if (!$checksPassed) throw new RuntimeException('安装环境未通过，请先处理红色检测项');
        if (!hash_equals((string) $_SESSION['install_csrf'], (string) ($_POST['csrf'] ?? ''))) throw new RuntimeException('页面已过期，请刷新后重新提交');

        $database = [
            'host' => trim((string) ($_POST['db_host'] ?? '127.0.0.1')),
            'port' => (int) ($_POST['db_port'] ?? 3306),
            'name' => trim((string) ($_POST['db_name'] ?? '')),
            'user' => trim((string) ($_POST['db_user'] ?? '')),
            'password' => (string) ($_POST['db_password'] ?? ''),
            'prefix' => trim((string) ($_POST['db_prefix'] ?? 'ci_')),
        ];
        if (!preg_match('/^[a-zA-Z0-9_]{1,64}$/', $database['name'])) throw new InvalidArgumentException('数据库名称格式不正确');
        if ($database['user'] === '') throw new InvalidArgumentException('数据库账号不能为空');
        if (!preg_match('/^[a-zA-Z0-9_]{1,20}$/', $database['prefix'])) throw new InvalidArgumentException('数据表前缀只能包含字母、数字和下划线');
        if ($database['port'] < 1 || $database['port'] > 65535) throw new InvalidArgumentException('数据库端口不正确');

        $pdo = new PDO("mysql:host={$database['host']};port={$database['port']};dbname={$database['name']};charset=utf8mb4", $database['user'], $database['password'], [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::MYSQL_ATTR_MULTI_STATEMENTS => true,
        ]);
        $adminTableName = $database['prefix'] . 'admin';
        $tableCheck = $pdo->prepare('SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=? AND TABLE_NAME=?');
        $tableCheck->execute([$database['name'], $adminTableName]);
        if ((int) $tableCheck->fetchColumn() > 0) throw new RuntimeException('数据库中已存在同前缀程序表。请使用空数据库，防止覆盖已有数据。');

        $schema = file_get_contents(APP_ROOT . '/database/schema.sql');
        if ($schema === false) throw new RuntimeException('数据库结构文件缺失');
        $pdo->exec(str_replace('`ci_', '`' . $database['prefix'], $schema));

        $encryptKey = bin2hex(random_bytes(32));
        $pdo->beginTransaction();
        $pdo->prepare('INSERT INTO ' . table($database['prefix'], 'admin') . ' (`username`,`password_hash`,`must_change_password`,`status`,`created_at`,`updated_at`) VALUES (?,?,1,1,NOW(),NOW())')
            ->execute([DEFAULT_ADMIN_USER, password_hash(DEFAULT_ADMIN_PASSWORD, PASSWORD_DEFAULT)]);

        require_once APP_ROOT . '/app/service/ServiceCatalog.php';
        $serviceStatement = $pdo->prepare('INSERT INTO ' . table($database['prefix'], 'service') . ' (`code`,`provider_api_id`,`name`,`short_name`,`description`,`icon`,`input_schema`,`result_schema`,`provider_key_cipher`,`request_url_cipher`,`request_method`,`response_code_path`,`response_success_value`,`response_data_path`,`sale_price`,`cost_price`,`sort`,`status`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0,NOW(),NOW())');
        foreach (\app\service\ServiceCatalog::all() as $index => $service) {
            $serviceStatement->execute([$service['code'],$service['apiId'],$service['name'],$service['shortName'],$service['description'],'',json_encode($service['inputs'],JSON_UNESCAPED_UNICODE),json_encode($service['results'],JSON_UNESCAPED_UNICODE),encryptData(['key'=>''],$encryptKey),encryptData(['url'=>''],$encryptKey),'GET','code','200','data',$service['salePrice'],$service['costPrice'],$index + 1]);
        }
        $pdo->commit();

        writeEnvironment($database, $encryptKey);
        $runtime = APP_ROOT . '/runtime';
        if (!is_dir($runtime) && !mkdir($runtime, 0750, true) && !is_dir($runtime)) throw new RuntimeException('无法创建 runtime 目录');
        if (file_put_contents(INSTALL_LOCK, json_encode(['installed_at'=>date(DATE_ATOM),'version'=>'2.0.0'], JSON_UNESCAPED_UNICODE), LOCK_EX) === false) throw new RuntimeException('无法写入安装锁');
        $success = true;
        $alreadyInstalled = true;
        unset($_SESSION['install_csrf']);
    } catch (Throwable $exception) {
        if (isset($pdo) && $pdo instanceof PDO && $pdo->inTransaction()) $pdo->rollBack();
        @unlink(APP_ROOT . '/.env.installing');
        $errors[] = $exception->getMessage();
    }
}
?>
<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>安装车辆信息查询系统</title>
<style>:root{--blue:#175cd3;--bg:#f3f6fb;--text:#18243a;--muted:#718096;--line:#dfe6f0;--green:#09875c;--red:#d74840}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC",sans-serif}.hero{height:190px;padding-top:35px;text-align:center;color:#fff;background:linear-gradient(135deg,#0b3479,#2475e2)}.logo{width:54px;height:54px;margin:auto;display:grid;place-items:center;border-radius:15px;background:#ffffff18;border:1px solid #ffffff66;font-size:27px;font-weight:800}.hero h1{margin:12px 0 4px;font-size:24px}.hero p{margin:0;font-size:13px;opacity:.75}.container{width:min(900px,calc(100% - 30px));margin:-30px auto 50px}.card{padding:26px;margin-bottom:18px;background:#fff;border:1px solid #e6ebf2;border-radius:14px;box-shadow:0 12px 35px #234b7a12}h2{font-size:18px;margin:0 0 18px}.checks,.grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.check{display:flex;justify-content:space-between;padding:13px;border:1px solid var(--line);border-radius:8px;font-size:13px}.ok{color:var(--green)}.bad{color:var(--red)}label{font-size:13px;font-weight:600}input{width:100%;height:43px;margin-top:8px;padding:0 12px;border:1px solid #d8e0eb;border-radius:8px;outline:none}input:focus{border-color:var(--blue);box-shadow:0 0 0 3px #175cd315}.hint{font-size:12px;color:var(--muted);line-height:1.7}.error{padding:13px 16px;margin-bottom:16px;border-radius:8px;background:#fff0ef;color:var(--red);font-size:13px}.submit,.button{display:inline-grid;place-items:center;height:48px;border:0;border-radius:8px;background:var(--blue);color:#fff;font-weight:700;text-decoration:none}.submit{width:100%;margin-top:24px}.submit:disabled{background:#aeb9c8}.done{text-align:center;padding:48px}.done-icon{width:70px;height:70px;margin:auto;display:grid;place-items:center;border-radius:50%;background:#e5f8ef;color:var(--green);font-size:35px}.credentials{display:inline-block;margin:18px auto;padding:14px 25px;border-radius:9px;background:#f1f5fa;font-size:15px}.button{padding:0 28px}@media(max-width:650px){.checks,.grid{grid-template-columns:1fr}.card{padding:19px}}</style></head>
<body><header class="hero"><div class="logo">车</div><h1>车辆信息查询系统</h1><p>安装环境检测与数据库初始化</p></header><main class="container">
<?php if ($success): ?><section class="card done"><div class="done-icon">✓</div><h2>安装成功</h2><p class="hint">数据库和系统配置已经完成，现在可以直接进入管理后台。</p><div class="credentials">账号：<b>admin</b>　密码：<b>123456</b></div><br><a class="button" href="../admin/?installed=1">进入管理后台</a></section>
<?php elseif ($alreadyInstalled): ?><section class="card done"><div class="done-icon">✓</div><h2>系统已经安装</h2><p class="hint">安装入口已锁定，避免覆盖现有数据。</p><a class="button" href="../admin/">进入管理后台</a></section>
<?php else: ?><?php foreach ($errors as $error): ?><div class="error"><?= escape($error) ?></div><?php endforeach; ?>
<section class="card"><h2>第一步：环境检测</h2><div class="checks"><?php foreach ($checks as [$name,$passed,$detail]): ?><div class="check"><span><?= escape($name) ?></span><b class="<?= $passed?'ok':'bad' ?>"><?= $passed?'✓':'×' ?> <?= escape($detail) ?></b></div><?php endforeach; ?></div></section>
<form method="post"><input type="hidden" name="csrf" value="<?= escape($_SESSION['install_csrf']) ?>"><section class="card"><h2>第二步：数据库配置</h2><p class="hint">请提前在宝塔创建空数据库。其他微信、支付、接口和客服配置均在安装后的管理后台填写。</p><div class="grid"><label>数据库地址<input name="db_host" value="<?= escape($_POST['db_host'] ?? '127.0.0.1') ?>" required></label><label>数据库端口<input name="db_port" type="number" value="<?= escape($_POST['db_port'] ?? '3306') ?>" required></label><label>数据库名称<input name="db_name" value="<?= escape($_POST['db_name'] ?? '') ?>" required></label><label>数据表前缀<input name="db_prefix" value="<?= escape($_POST['db_prefix'] ?? 'ci_') ?>" required></label><label>数据库账号<input name="db_user" value="<?= escape($_POST['db_user'] ?? '') ?>" required></label><label>数据库密码<input name="db_password" type="password"></label></div><button class="submit" type="submit" <?= $checksPassed?'':'disabled' ?>>确认配置并安装</button></section></form>
<?php endif; ?></main></body></html>
