# Car Search Spring Boot + UniApp 重构版

本目录是现有 PHP + UniApp 项目的并行重构，不覆盖旧系统。

## 目录

- `backend/`：Java 21 + Spring Boot 4.1.1 后端。
- `miniapp/`：UniApp 微信小程序（迁移阶段保持原接口协议）。
- `MIGRATION.md`：业务兼容范围和迁移进度。

新后端直接兼容现有 MySQL `ci_*` 表和 AES-256-GCM 密文格式。迁移完成前不要让新旧后端同时执行支付对账、供应商查询或清理定时任务。

## 启动后端

```powershell
cd car-search-springboot/backend
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/car_inquiry?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='car_inquiry'
$env:DB_PASSWORD='数据库密码'
$env:DATA_ENCRYPT_KEY='与PHP环境完全相同的密钥'
mvn spring-boot:run
```

默认端口为 `8080`。生产环境必须通过 HTTPS 反向代理访问。

## 本地开发环境（Windows）

本地环境全部位于本工程和 E 盘，不使用 C 盘存放数据库、Maven 仓库或 npm 缓存：

- MySQL 数据：`.local/mysql-data`
- MySQL 端口：`127.0.0.1:3307`
- 开发库：`car_search_dev`
- Maven 仓库：`.local/maven-repo`
- npm 缓存：`.local/npm-cache`
- 运行日志：`.local/logs`

数据库已从 `car_test_mysql_data_bJDYo.sql` 导入。启动顺序：

```powershell
cd E:\XY_MZF\Car-Search\car-search-springboot
powershell -ExecutionPolicy Bypass -File .\start-local.ps1
powershell -ExecutionPolicy Bypass -File .\start-backend.ps1
```

访问地址：

- 后端健康检查：`http://127.0.0.1:8080/actuator/health`
- 管理后台：`http://127.0.0.1:8080/admin/index.html`
- 公开接口：`http://127.0.0.1:8080/api/v1/bootstrap`

启动小程序开发编译：

```powershell
cd E:\XY_MZF\Car-Search\car-search-springboot\miniapp
npm run dev:mp-weixin
```

开发模式自动使用 `http://127.0.0.1:8080/api/v1`，生产构建仍使用 `https://car.naiblog.cn/api/v1`。

`.env.local` 里的 `DATA_ENCRYPT_KEY` 目前是本地占位密钥，只能保证应用启动。若要解密备份中的订单输入、结果、供应商地址及微信安全配置，必须替换为线上导出数据实际使用的 `DATA_ENCRYPT_KEY`；该值无法从数据库备份中还原，也不应提交 Git。

停止环境：

```powershell
powershell -ExecutionPolicy Bypass -File .\stop-backend.ps1
powershell -ExecutionPolicy Bypass -File .\stop-local.ps1
```

数据库导出文件包含真实业务数据，已加入 `.gitignore`，不要提交到 Git 或发送到无关环境。隔离开发库的本地账号只允许访问 `car_search_dev`，不能访问原来的 MySQL 数据目录。
