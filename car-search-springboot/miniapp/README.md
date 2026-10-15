# UniApp 微信小程序

## 本地联调

先在项目根目录启动 Spring Boot，然后在本目录执行：

```powershell
npm run dev:mp-weixin
```

微信开发者工具必须导入 `miniapp/dist/dev/mp-weixin`。该开发包使用：

```text
http://127.0.0.1:8080/api/v1
```

开发者工具中还需在“详情 → 本地设置”勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”，否则微信开发者工具会拦截本地 HTTP 请求。

如果本地数据库中的微信 AppSecret 因主密钥不同而无法解密，根目录 `.env.local` 的 `CAR_DEV_LOGIN_ENABLED=true` 会启用仅限本机回环地址的开发登录，让“我的”、订单和反馈页面可调试。此模式不代表真实微信登录和支付已通过，生产环境必须关闭。

`127.0.0.1` 只适用于运行在同一台电脑上的微信开发者工具。手机真机中的 `127.0.0.1` 指手机自身，真机联调需要使用电脑的局域网 IP 或 HTTPS 测试域名，并确认防火墙和微信合法域名配置。

## 正式构建

```powershell
npm run build:mp-weixin
```

正式包位于 `miniapp/dist/build/mp-weixin`，使用 `https://car.naiblog.cn/api/v1`。不要用正式构建目录进行本地接口联调，也不要把开发包上传为正式版本。
