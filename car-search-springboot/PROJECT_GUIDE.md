# Car Search Spring Boot + UniApp 项目说明

> 面向首次接手本项目的开发者。本文依据当前源码整理，描述“现在有什么、各部分做什么、从哪里继续开发”。本项目仍处于重构迁移期，不能将已存在的代码等同于已经完成生产验证。

## 1. 项目定位

1. 本目录是原 PHP + UniApp 车辆信息查询小程序的并行重构版本。
2. `backend/` 是 Java 21 + Spring Boot 后端，同时提供小程序 API、管理 API、微信消息回调和静态管理后台。
3. `miniapp/` 是 Vue 3 + UniApp 微信小程序端。
4. 新后端直接兼容原系统的 MySQL `ci_*` 表、接口协议和 AES-256-GCM 密文，不会自动建表或改表。
5. 小程序公开 API 前缀是 `/api/v1`，管理 API 前缀是 `/admin-api`，微信消息入口是 `/wechat/message`。
6. 客户端协议版本当前为 `20260815.2`，统一响应结构为 `{ code, message, data }`。
7. 当前版本尚未完全完成，迁移范围和进度以 `MIGRATION.md` 及本文“当前完成度”章节为准。

## 2. 顶层目录与文件

```text
car-search-springboot/
├─ backend/                    Spring Boot 后端
├─ miniapp/                    UniApp 微信小程序
├─ .local/                     本地 MySQL、日志、依赖缓存等运行数据（不提交）
├─ .env.local                 本机后端环境变量（含敏感配置，不提交）
├─ .env.local.example         环境变量示例
├─ .gitignore                 本目录忽略规则
├─ car_test_mysql_data_*.sql  原业务数据库快照（敏感数据，不应提交或外传）
├─ README.md                  简要启动说明
├─ MIGRATION.md               兼容约束、迁移进度和切换原则
├─ PROJECT_GUIDE.md           本说明文档
├─ run-backend.ps1            前台运行后端
├─ start-local.ps1            启动本地 MySQL
├─ start-backend.ps1          构建并在后台启动后端
├─ stop-backend.ps1           停止本地后端
├─ stop-local.ps1             停止本地 MySQL
└─ test-local.ps1             本地接口集成检查
```

1. `.local/mysql-data` 保存本地开发数据库数据。
2. `.local/maven-repo` 保存项目专用 Maven 仓库缓存。
3. `.local/npm-cache` 保存项目专用 npm 缓存。
4. `.local/logs` 保存 MySQL 和后端运行日志。
5. `.env.local` 只用于本机，不得把数据库密码、微信密钥或数据加密主密钥提交到 Git。
6. `car_test_mysql_data_*.sql` 包含真实形态的业务数据，只能用于受控的本地迁移验证。

## 3. 后端结构与职责

后端源码根包为 `backend/src/main/java/cn/naiblog/carsearch/`。

### 3.1 根入口

1. `CarSearchApplication.java`：Spring Boot 启动类，启用定时任务和配置属性。

### 3.2 `api/`：统一接口基础设施

1. `ApiResponse.java`：定义统一的 `{code,message,data}` 响应对象。
2. `ApiSupport.java`：读取当前用户、校验客户端协议等控制器公共逻辑。
3. `BusinessException.java`：可安全返回给客户端的业务异常。
4. `GlobalExceptionHandler.java`：集中转换参数错误、业务异常和系统异常为 HTTP/API 响应。
5. `Pagination.java`：统一分页结果结构。

### 3.3 `config/`：配置与兼容性

1. `CarProperties.java`：映射 `car.*` 配置，包括协议版本、加密密钥、文件目录和微信 AppID/AppSecret。
2. `DatabaseCompatibilityVerifier.java`：启动时检查现有数据库表/字段是否满足兼容要求，避免在结构不匹配时带病运行。
3. `SettingService.java`：读取与更新 `ci_setting`、`ci_secure_setting`，敏感配置通过 `CryptoService` 加解密。
4. `WebConfig.java`：注册鉴权拦截器；除登录、服务目录和服务图标外，`/api/v1/**` 要求用户登录；除管理登录外，`/admin-api/**` 要求管理员登录。
5. `application.yml`：数据源、端口、上传限制、Actuator、业务配置和日志级别。

### 3.4 `security/`：身份、限流和加密

1. `UserAuthInterceptor.java`：校验小程序 Bearer Token，并把当前用户写入请求上下文。
2. `AdminAuthInterceptor.java`：校验管理端 Bearer Token，并把当前管理员写入请求上下文。
3. `CurrentUser.java`：当前小程序用户对象。
4. `CurrentAdmin.java`：当前后台管理员对象。
5. `Hashing.java`：哈希工具；数据库只保存访问 Token 的 SHA-256 值。
6. `CryptoService.java`：兼容原 PHP 系统的 AES-256-GCM 密文，用于订单输入、查询结果、供应商配置、微信会话和安全设置。
7. `RateLimitFilter.java`：对登录、业务接口等入口实施请求频率限制。

### 3.5 `controller/`：HTTP 入口

1. `RootController.java`：将 `/`、`/admin` 导向静态管理后台。
2. `PublicController.java`：提供 `/api/v1/bootstrap` 服务目录和 `/api/v1/service-icon/{file}` 服务图标。
3. `AuthController.java`：微信 code 登录、当前用户信息、资料更新和退出登录。
4. `CheckoutController.java`：创建订单、查询订单状态、获取支付参数、确认/恢复支付以及触发查询。
5. `OrderController.java`：当前用户的历史订单列表与订单详情。
6. `FeedbackController.java`：用户提交反馈、查看反馈列表和详情。
7. `AdminController.java`：管理员登录/退出、仪表盘、财务统计、用户、公告、通用设置、支付设置、邮件设置和审计记录。
8. `AdminOrderController.java`：管理端订单列表/详情、支付同步、查询重试、发货、在线退款、iOS 手动退款、受限批量删除和微信消息归档查询。
9. `ServiceAdminController.java`：查询服务配置、创建/修改/排序/删除服务、上传图标和测试上游供应商接口。
10. `FeedbackAdminController.java`：管理端反馈列表/详情、回复和删除。
11. `WechatMessageController.java`：验证微信消息回调 URL，并接收安全模式 AES JSON 消息。

### 3.6 `service/`：核心业务

1. `CheckoutService.java`：下单主流程，负责用户归属、服务快照、后端定价、幂等键和订单状态。
2. `InputValidator.java`：按服务的 `input_schema` 校验用户查询字段。
3. `ProviderService.java`：按服务配置调用上游供应商 API，并按 JSON 路径提取响应状态和数据。
4. `ProviderQueryException.java`：保存供应商错误码与请求标识，供查询失败处理。
5. `QueryRunnerService.java`：以原子状态转换抢占待查询订单，调用供应商并保存标准化结果。
6. `VirtualPaymentService.java`：生成 `wx.requestVirtualPayment` 所需的签名和支付参数。
7. `PaymentReconciliationService.java`：通过微信服务端订单查询核实支付结果；不能依赖客户端 success 作为最终付款依据。
8. `PaymentDeliveryService.java`：查询完成后向微信确认虚拟商品已发货。
9. `PaymentLifecycleService.java`：串联支付确认、发货、退款申请和退款状态核对。
10. `AuditService.java`：记录管理员敏感操作审计日志。
11. `MailService.java`：发送反馈回复等业务邮件。
12. `MaintenanceScheduler.java`：执行支付对账、查询恢复、过期数据清理等周期任务。

### 3.7 `wechat/`：微信平台适配

1. `WechatLoginService.java`：服务端调用 `code2Session`，用临时 code 换取 `openid` 和 `session_key`。
2. `WechatPaymentService.java`：封装微信虚拟支付服务端 API、access token 和签名处理。
3. `WechatMessageCryptoService.java`：验证消息签名并加解密安全模式消息。
4. `WechatRefundEventService.java`：归档并处理发货、iOS 退款等微信事件；退款状态机仍需继续补齐和真实回归。

### 3.8 后端资源与测试

1. `backend/src/main/resources/application.yml`：默认运行配置，生产敏感值必须从环境变量或加密配置表注入。
2. `backend/src/main/resources/static/admin/index.html`：无前端框架的管理后台入口。
3. `static/admin/app.js`：管理端鉴权、路由和公共请求逻辑。
4. `static/admin/dashboard.js`：仪表盘和财务图表。
5. `static/admin/services.js`：查询服务配置管理。
6. `static/admin/settings.js`：通用、支付和邮件配置页面逻辑。
7. `static/admin/feedback.js`：反馈管理页面逻辑。
8. `static/admin/errors.js`：错误展示与转换。
9. `static/admin/style.css`、`enhancements.css`：管理后台样式。
10. `static/admin/echarts.min.js`：仪表盘图表依赖。
11. `backend/src/test/.../CryptoServiceTest.java`：验证 PHP 兼容加密格式。
12. `WechatMessageCryptoServiceTest.java`：验证微信消息安全模式加解密。
13. `WechatRefundEventServiceTest.java`：验证微信退款事件处理的部分逻辑。

## 4. 小程序结构与职责

小程序源码位于 `miniapp/src/`。

### 4.1 根配置

1. `main.js`：创建并挂载 Vue 3 应用。
2. `App.vue`：应用级生命周期和全局样式。
3. `manifest.json`：UniApp 应用及微信小程序平台配置。
4. `pages.json`：页面注册、导航栏和底部 TabBar 配置。
5. `config.js`：API 根地址；默认生产地址为 `https://car.naiblog.cn/api/v1`，开发命令由 Vite 注入本地地址。
6. `project.config.json`：微信开发者工具项目配置。
7. `vite.config.js`：UniApp/Vite 构建配置和开发环境变量。
8. `package.json`：依赖与 `dev:mp-weixin`、`build:mp-weixin` 命令。

### 4.2 页面 `pages/`

1. `index/index.vue`：首页，加载公告和可用查询服务，提供入口导航。
2. `query/query.vue`：按服务输入定义收集车牌号等查询参数，创建订单并发起支付/查询流程。
3. `result/result.vue`：轮询或读取订单状态，展示标准化查询结果和失败信息。
4. `orders/orders.vue`：展示当前用户历史订单。
5. `feedback/feedback.vue`：提交投诉建议并查看回复记录。
6. `agreement/agreement.vue`：展示服务协议、隐私规则和免责声明。
7. `my/my.vue`：个人中心、用户资料、订单/反馈入口和退出登录。

### 4.3 组件 `components/`

1. `plate-input/plate-input.vue`：车牌输入展示和交互组件。
2. `plate-keyboard/plate-keyboard.vue`：适配普通、新能源等车牌输入的自定义键盘。

### 4.4 工具 `utils/`

1. `auth.js`：调用 `uni.login` 获取微信 code，再请求后端 `/auth/login`；缓存和清理 Bearer Token。
2. `request.js`：封装请求地址、超时、协议头、鉴权头、登录失效和统一错误处理。
3. `protocol.js`：保存客户端协议版本及协议相关常量。
4. `payment.js`：校验服务端支付参数、调用 `wx.requestVirtualPayment`、转换错误码并区分确定失败与待服务端对账状态。
5. `loading.js`：统一加载状态管理，避免页面重复显示/隐藏 loading。

## 5. 主要业务链路

### 5.1 登录

1. 小程序 `auth.js` 调用 `uni.login({ provider: 'weixin' })` 获取短期 code。
2. 小程序把 code 提交到 `POST /api/v1/auth/login`。
3. 后端 `WechatLoginService` 调用微信 `code2Session`。
4. 后端保存用户信息和加密后的 `session_key`。
5. 后端生成随机 Token，返回原文给客户端，数据库仅保存 SHA-256；Token 默认最长有效 2 小时。
6. 后续受保护请求携带 `Authorization: Bearer <token>`。

### 5.2 下单、支付与查询

1. 小程序从 `GET /api/v1/bootstrap` 获取服务目录、输入定义和价格。
2. 小程序提交服务编码、输入和 16～64 位幂等键到 `POST /api/v1/checkout/create`。
3. 后端先按 `user_id + request_key` 恢复已有订单，再校验输入、服务和后端价格。
4. 小程序调用 `/checkout/payment` 获取后端签名的虚拟支付参数。
5. 小程序调用 `wx.requestVirtualPayment` 拉起支付。
6. 客户端回调只能用于继续交互，订单付款状态必须由微信服务端查询或验签消息确认。
7. 支付确认后，后端以原子状态转换将订单从 `paid` 推进为 `querying`，再调用供应商。
8. 查询成功后，加密保存原始/标准化结果，将订单置为 `success`，并向微信确认发货。
9. 超时、客户端退出或回调丢失时，通过 `/checkout/status`、`/checkout/recoverable` 和后台对账任务恢复。

### 5.3 反馈

1. 用户通过 `/api/v1/feedback` 提交和查看反馈。
2. 管理员通过 `/admin-api/feedback*` 查看、回复或删除。
3. 配置 SMTP 后，后台回复可由 `MailService` 发送邮件。

### 5.4 微信消息、退款和发货

1. 微信平台使用 `GET /wechat/message` 验证回调地址。
2. 生产消息使用 `POST /wechat/message?encrypt_type=aes...` 推送。
3. 后端校验签名并解密 AES JSON，不接受明文业务消息。
4. 事件请求和处理结果加密归档到 `ci_wechat_event`，重复事件按事件键幂等处理。
5. 管理后台可查看消息、重试发货、发起/核对退款；iOS 退款事件流程仍需补齐真实状态机测试。

## 6. API 分组速查

1. 公开接口：`GET /api/v1/bootstrap`、`GET /api/v1/service-icon/{file}`、`POST /api/v1/auth/login`。
2. 用户接口：`/api/v1/me`、`/api/v1/orders*`、`/api/v1/feedback*`、`/api/v1/checkout/*`、`POST /api/v1/auth/logout`。
3. 管理接口：`/admin-api/login`、仪表盘、用户、订单、服务、公告、反馈、审计、通用/支付/邮件设置。
4. 微信入口：`GET|POST /wechat/message`。
5. 运维入口：`GET /actuator/health`、`GET /actuator/info`。
6. 完整请求字段和响应字段应直接查看相应 Controller、Service 以及项目根目录的 `api接口详情.md`；开发时不要只依赖本节摘要。

## 7. 数据表与作用

1. `ci_user`：小程序用户、openid/unionid、资料和账号状态。
2. `ci_user_token`：小程序登录 Token 哈希和过期时间。
3. `ci_admin`：后台管理员及密码哈希。
4. `ci_admin_token`：管理员 Token 哈希和过期时间。
5. `ci_admin_login_attempt`：后台登录失败与风控记录。
6. `ci_service`：查询服务、输入/输出 Schema、价格、支付商品 ID、供应商请求及解析配置。
7. `ci_order`：业务订单、用户归属、服务快照、金额、状态、加密输入和结果。
8. `ci_payment`：微信支付单、支付/退款/发货状态及平台响应。
9. `ci_feedback`：用户反馈、联系方式、处理状态和回复。
10. `ci_announcement`：小程序公告、展示时间和抑制周期。
11. `ci_setting`：普通业务配置。
12. `ci_secure_setting`：微信 AppSecret/AppKey、消息密钥、SMTP 密码等加密配置。
13. `ci_audit_log`：管理员敏感操作审计记录。
14. `ci_wechat_event`：微信回调事件、幂等键、加密报文和处理结果。

注意：JDBC 查询大量使用现有字段名，数据库结构变更前必须同步检查 SQL、兼容验证器、旧 PHP 程序和线上存量数据。

## 8. 配置说明

### 8.1 必要环境变量

1. `DB_URL`：MySQL JDBC 地址。
2. `DB_USERNAME`：数据库账号。
3. `DB_PASSWORD`：数据库密码。
4. `DATA_ENCRYPT_KEY`：与原 PHP 线上环境完全一致的数据加密主密钥；错误密钥会导致存量密文无法解密。
5. `WECHAT_APP_ID`：微信小程序 AppID；数据库配置可覆盖/补充运行配置。
6. `WECHAT_APP_SECRET`：微信小程序 AppSecret，仅可保存在后端。
7. `CAR_STORAGE_PATH`：服务图标等上传文件目录，默认 `./runtime/uploads`。
8. `PORT`：后端端口，默认 `8080`。
9. `DB_POOL_SIZE`：数据库连接池上限，默认 `10`。

### 8.2 数据库/后台安全配置

1. 微信 OfferID、支付环境和支付开关保存在设置表。
2. 微信沙箱/正式 AppKey、消息 Token、EncodingAESKey、SMTP 密码等保存在安全设置表并加密。
3. `DATA_ENCRYPT_KEY` 无法从数据库备份反推，丢失后存量加密字段不可恢复。
4. 生产环境必须由 HTTPS 反向代理访问，且需正确传递转发头。
5. 不要把 `session_key`、AppSecret、AppKey、Token、供应商密钥输出到前端、URL 或日志。

## 9. 本地启动

### 9.1 前置条件

1. JDK 21。
2. Maven。
3. Node.js 与 npm。
4. MySQL 8.x。
5. 微信开发者工具。
6. 当前 PowerShell 脚本硬编码使用 `E:\mySQL\bin`；伙伴电脑路径不同需先调整脚本或手工启动 MySQL。

### 9.2 启动步骤（Windows）

```powershell
cd E:\XY_MZF\Car-Search\car-search-springboot
Copy-Item .env.local.example .env.local
# 按本机环境填写 .env.local，尤其是数据库与 DATA_ENCRYPT_KEY
powershell -ExecutionPolicy Bypass -File .\start-local.ps1
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1

cd .\miniapp
npm install
npm run dev:mp-weixin
```

1. 后端健康检查：`http://127.0.0.1:8080/actuator/health`。
2. 管理后台：`http://127.0.0.1:8080/admin/`。
3. 公开服务接口：`http://127.0.0.1:8080/api/v1/bootstrap`。
4. 小程序开发产物通常位于 `miniapp/dist/dev/mp-weixin`，用微信开发者工具导入。
5. Vite 开发模式使用 `http://127.0.0.1:8080/api/v1`；真机调试不能直接访问电脑的 `127.0.0.1`，需改成局域网/测试域名并配置微信合法域名。

### 9.3 停止与测试

```powershell
cd E:\XY_MZF\Car-Search\car-search-springboot
powershell -ExecutionPolicy Bypass -File .\test-local.ps1
powershell -ExecutionPolicy Bypass -File .\stop-backend.ps1
powershell -ExecutionPolicy Bypass -File .\stop-local.ps1

cd .\backend
mvn test

cd ..\miniapp
npm run build:mp-weixin
```

1. `test-local.ps1` 检查健康状态、静态管理台、公开接口、鉴权、订单/反馈响应和敏感字段泄露。
2. 传入 `-AdminUsername`、`-AdminPassword` 后会增加管理 API 登录态检查。
3. 微信登录、支付、退款、消息和发货仍需在具有真实权限与配置的开发版/体验版环境回归。

## 10. 当前完成度与优先事项

### 10.1 已有实现

1. Spring Boot 工程、生产配置基线和数据库结构兼容检查。
2. API 统一响应、异常处理、用户/管理员鉴权、协议校验和限流。
3. PHP AES-256-GCM 存量密文兼容。
4. 微信 code2Session 登录与短期 Bearer Token。
5. 服务目录、动态输入/结果 Schema 和供应商查询。
6. 幂等下单、虚拟支付参数、服务端支付核验、订单恢复和发货。
7. 用户订单、用户反馈和静态管理后台的主要功能。
8. 微信安全模式消息解密、事件归档和部分退款事件处理。

### 10.2 尚需完善/验证

1. 补齐 iOS 退款事件的完整状态机、异常分支和线上回归。
2. 完成所有管理 API、审计、邮件和定时任务的功能验收。
3. 建立 PHP 与 Spring Boot 双实现契约测试，证明相同输入产生兼容响应和状态变化。
4. 覆盖支付失败、超时、重复请求、回调丢失、重复通知、退款失败和发货重试。
5. 使用真实微信开发版/体验版核对 AppID、OfferID、道具 ID、AppKey、回调 URL、消息 Token 和 EncodingAESKey。
6. 确认各端差异，尤其是 iOS 15+/微信 8.0.68+、iOS 不支持沙箱、iOS 最低 1 元等限制。
7. 修复或确认当前部分 Markdown/JSON 中文内容可能存在的历史编码显示问题，统一使用 UTF-8。
8. 完成生产 Nginx、HTTPS、合法域名、文件持久化、备份、日志和监控配置。

## 11. 开发约束与注意事项

1. 不要同时让 PHP 和 Spring Boot 消费微信回调或运行支付对账、供应商查询、清理定时任务，否则可能重复扣费、查询或推进状态。
2. 订单金额、服务、支付商品、用户归属和订单状态一律以后端数据库为准。
3. 客户端支付 success 不代表最终到账；必须通过微信服务端查询或验签消息确认。
4. 下单必须保留 `user_id + request_key` 幂等恢复，不能仅靠前端禁用按钮防重复。
5. `paid -> querying` 等关键状态必须采用条件更新/事务抢占，防止并发重复调用供应商。
6. 修改微信功能前先检索工程根目录 `微信小程序官方文档/`，涉及支付、退款、安全等高风险内容时还要核对当前官方在线文档。
7. 修改接口协议时同步检查后端 Controller/Service、小程序 `request.js`/`protocol.js`、旧 PHP 兼容和 `api接口详情.md`。
8. 修改服务配置结构时同步验证后台编辑页、小程序动态表单、输入校验、供应商解析和存量服务数据。
9. 已付款或已进入支付处理的订单是财务凭证，不应物理删除；管理端现有批量删除已对此做限制。
10. 生产切换应一次性切换 `/api/v1`、`/admin-api`、`/wechat/message` 三个入口，观察期保留 PHP upstream 以便只回滚流量。

## 12. 微信官方文档依据

本说明整理时阅读了以下本地官方文档镜像（页面抓取时间均为 2026-09-01）：

1. `微信小程序官方文档/miniprogram/dev/api/open-api/login/wx.login.md`：客户端获取五分钟有效登录 code。
2. `微信小程序官方文档/miniprogram/dev/server/API/user-login/api_code2session.md`：`code2Session` 必须在服务端调用。
3. `微信小程序官方文档/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment.md`：虚拟支付、服务端订单核验、发货、退款以及 iOS 限制。

线上最终行为仍取决于微信后台权限、正式配置、基础库/客户端版本和真实接口响应。支付、退款、发货或回调代码变更时，须按工程根目录 `AGENTS.md` 的规则重新核对最新官方在线文档。
