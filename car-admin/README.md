# 车辆信息查询后台 3.0

这是可全新安装的 ThinkPHP 8 后端。安装过程只需要数据库信息；管理员、数据表、服务目录、主加密密钥及正式 `.env` 均由安装程序自动创建。

## 上传前准备

服务器要求：PHP 8.0+（推荐 8.2）、MySQL 5.7+、Nginx、PDO MySQL、OpenSSL、cURL、mbstring、fileinfo。

源码仓库不提交 `vendor`。如果上传的发布包没有 `vendor/autoload.php`，在项目目录执行一次：

```bash
composer install --no-dev --optimize-autoloader
```

安装页面会检测 Composer 依赖；缺少 `vendor/autoload.php` 时不会允许继续安装，不会出现“安装成功但后台无法运行”。

## 宝塔部署

1. 在宝塔创建一个空数据库和数据库账号，字符集选择 `utf8mb4`。
2. 上传完整 `car-admin` 项目。
3. 创建 PHP 网站，PHP 选择 8.2，运行目录设置为：

   ```text
   /www/wwwroot/你的站点目录/public
   ```

4. 伪静态填写：

   ```nginx
   location / {
       try_files $uri $uri/ /index.php?s=$uri&$query_string;
   }

   location ~* ^/(?:\.env|composer\.(?:json|lock)|database(?:/|$)|app(?:/|$)|config(?:/|$)|route(?:/|$)|runtime(?:/|$)|2026[0-9]+\.log$) {
       deny all;
   }

   client_max_body_size 64k;
   fastcgi_read_timeout 40s;
   ```

5. 确保安装期间项目根目录和 `runtime` 可写。
6. 直接访问站点域名。未安装时根域名自动跳转 `/install/`，安装完成后自动跳转 `/admin/`。
7. 安装页面只填写数据库地址、端口、数据库名、账号、密码和表前缀。
8. 安装成功后的默认后台账号：

   ```text
   账号：admin
   密码：123456
   ```

9. 首次登录会被强制修改默认密码；新密码需为12至128位，并至少包含大写字母、小写字母、数字、特殊字符中的三类。改密后依次配置供应商接口、微信小程序、虚拟支付、运营主体、隐私联系人、客服和公告。
10. 安装完成后执行：

   ```bash
   chown -R www:www runtime .env
   chmod -R 750 runtime
   chmod 640 .env
   ```

## `.env.example` 是否需要重命名

不需要。

`.env.example` 只是配置结构说明，不包含服务器数据库密码和主加密密钥，因此可以安全保存在源码中。正式 `.env` 由安装程序根据填写的数据库信息自动生成，并写入随机的 `DATA_ENCRYPT_KEY`。

如果项目直接附带固定 `.env`，不同用户会共享数据库占位配置和加密密钥，这是不安全的，也会造成覆盖安装。因此正式项目应保留 `.env.example`，由安装器生成每台服务器独有的 `.env`。

## 后台配置顺序

1. “接口与价格”：新增或编辑服务，填写完整 HTTPS 接口 URL、请求方式、用户输入字段、响应解析路径、展示字段、价格和微信支付道具ID；服务图标支持 PNG/JPG/WebP，大小不超过48KB，文件保存在 `runtime/uploads/service-icons`。
2. 接口 URL 中可直接保留供应商示例参数。系统会用用户提交内容覆盖同名参数，例如字段 key 为 `name`、`chepai` 时，会覆盖 URL 中的 `name`、`chepai`。
3. 状态设为“运行”时前端可支付查询；“维护”时前端可见但不能支付；“隐藏”时前端不展示。新装及升级后的原有接口默认维护。
4. 每个运行服务必须先在微信小程序后台“虚拟支付 → 道具管理”创建并发布对应道具，将道具ID填写到接口服务中，并确保微信道具价格与后台销售价完全一致。
5. “系统与支付”：填写微信 AppID、AppSecret、OfferID、沙箱 AppKey 和正式 AppKey。AppSecret 来自“小程序后台 - 开发管理 - 开发设置”，其余支付参数来自“虚拟支付 - 基础配置”。
6. 配置微信消息推送：服务器地址使用后台显示的 `/wechat/message` 完整 HTTPS 地址，数据格式选择“JSON”，消息加解密方式选择“安全模式”；公众平台与本后台填写完全相同的 Token、EncodingAESKey。该配置负责 Apple 退款问询、最终退款通知及支付交易编号补全。
7. 完成微信沙箱验证后再启用支付。支付参数或消息推送安全参数不完整时系统禁止开启。
7. 配置客服、免责声明和公告。

安装后所有查询服务默认维护、支付默认关闭，避免配置未完成时用户下单或付款。

## 数据链路

```text
微信登录 → 选择运行中的服务 → 服务端校验输入 → 创建待支付订单
→ 服务端调用微信官方 `/xpay/query_order` 核验支付状态和金额
→ 订单幂等变更为已支付 → 使用下单时的加密服务快照原子抢占查询
→ 调用供应商接口 → 仅业务码 200 保存并展示结果
→ 查询成功后调用微信 `/xpay/notify_provide_goods` 确认发货
→ 发货失败由定时任务自动补偿；查询失败由后台重试或发起官方退款
→ 退款通过 `/xpay/refund_order` 发起并用 `/xpay/query_order` 核验最终状态
```

其中 `/xpay/refund_order` 仅适用于 Android、鸿蒙、Windows 等普通虚拟支付。iOS Apple 支付由用户向 Apple 官方申请退款：微信向 `/wechat/message` 推送退款问询，系统在 3 秒内根据本地履约记录答复；Apple 作出最终决定后，系统处理 `xpay_refund_notify`，幂等更新订单与财务状态。管理员不得以私人转账后再点“手动退款”的方式处理 iOS 订单，以免 Apple 后续退款造成重复赔付。

包含供应商密钥的完整接口 URL、微信 AppSecret、两套支付 AppKey、用户 `session_key`、查询参数和查询结果均加密保存。系统不使用自定义支付回调密钥；支付结果通过微信官方订单查询接口确认。供应商响应只按结果字段白名单提取，`user_info` 不会下发小程序。

## 已安装站点升级（本次必须执行）

先备份网站和数据库。保留服务器中的 `.env`、`runtime`、`vendor`，其余后端代码用本版本覆盖；不要把本地缺失的 `vendor` 当作空目录上传。覆盖后在项目根目录执行：

```bash
php think car:upgrade
```

这条命令不能省略。3.0 会幂等补齐订单请求幂等键、查询尝试记录、支付查单节流字段、微信订单/交易/商户编号、退款事件表及索引，并从历史微信查单 JSON 回填可识别编号；不会删除用户、订单或历史反馈。执行后必须看到“数据库升级完成”。随后清理 ThinkPHP 缓存并验证：

```bash
php think clear
php think list | grep car:
curl -s https://你的域名/api/v1/bootstrap
```

`bootstrap` 返回的 `protocol_version` 必须为 `20260815.2`。如果不是，说明 Nginx/PHP-FPM 仍指向旧目录、OPcache 未刷新或 CDN 缓存未清理。重载 PHP 8.2 服务后再查，不要继续发布小程序。

新小程序版本必须上传 `car-miniapp/dist/build/mp-weixin`，版本号 `1.1.6`。不要上传 `src` 目录代替构建产物。

## 运维

在宝塔“计划任务”中新增 Shell 脚本，每分钟执行一次支付补偿任务：

```bash
cd /www/wwwroot/你的站点目录 && php think car:payment-reconcile
```

该任务会补查客户端回调丢失的待付款订单，对核验为已支付的订单自动发起供应商查询，把卡死 5 分钟以上的查询中订单复位后重跑，重试未确认的微信发货，并核验处理中的退款。即使客户端完全离线、后台关闭新支付通道，已有订单的查询、发货和退款仍会继续执行。

支付确认和供应商查询分别走固定 `/checkout/confirm` 与 `/checkout/query` 接口，订单号由请求头传递。单次供应商查询最长约 25 秒；PHP `max_execution_time` 与 Nginx/宝塔 `fastcgi_read_timeout` 均应不小于 35 秒，否则定时任务要在 5 分钟后才能恢复查询中订单。

## 上线验收

1. 通过网络调试确认接口 `/bootstrap` 的 `protocol_version=20260815.2`；正式界面不再展示内部协议版本。
2. 先用沙箱支付一笔最低价格服务，验证订单依次经过 `pending_payment → paid → querying → success`。
3. 查询成功后后台支付记录的 `delivery_status` 应变为 `delivered`；微信已返回付款结果后断网重进应恢复原订单，未付款订单不得在用户端恢复或提示。
4. 微信支付金额、订单号或环境任何一个不匹配时，订单必须进入 `payment_review`，不得调用供应商。
5. 切换正式环境前，微信道具必须已发布，`productId` 与销售价（分）必须逐项一致；正式 AppKey 与沙箱 AppKey 不可混用。
6. 供应商文档中曾出现过明文密钥，上线前应全部轮换，并只在后台加密配置完整 URL。
7. 在微信公众平台保存消息推送配置时必须验证成功；后台 iOS 订单不应出现主动退款按钮。使用 Apple 测试退款后，订单应依次显示“Apple审核中”和“已退款”，重复通知不得重复变更财务记录。

后台“微信消息”会统一保存消息推送入口收到的发货、代币支付、退款问询、退款结果、用户投诉、支付风控以及未知事件。iOS 退款问询中的 `refund_request_reason` 会保留原始枚举并在消息摘要中显示中文解释；该字段不是用户自由填写的完整说明。自动答复的结论和证据模板可在“系统与支付”修改，新模板只对尚未答复的问询生效。订单列表的“状态”只表示业务履约，退款审核及退款结果统一显示在“微信履约”列。
8. 在后台订单搜索框分别输入 `order_id`、`wx_order_id`、微信支付交易单号和渠道/商户单号，均应定位到同一订单。

每天执行一次：

```bash
cd /www/wwwroot/你的站点目录 && php think car:cleanup
```

忘记管理员密码时执行：

```bash
php think car:admin-reset admin
```

命令生成一次性密码，登录后在后台修改。

