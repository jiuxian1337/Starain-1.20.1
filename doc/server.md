# 验证后端（server）

`:server` 是一个独立的 Java 应用（`dev.jiuxian.server.ServerMain`），提供卡密激活、登录验证、在线会话管理与一个 Web 管理面板。它依赖 `:common` 中的包定义与加解密工具，**不依赖客户端模组**。

> 当前客户端代码里**没有**调用这个后端的登录/聊天客户端。两边只在 `common` 的包格式上对应；要接入需要在客户端补一套 socket 客户端。

## 启动

```bash
gradle :server:run           # 开发运行
gradle :server:jar           # fat jar：server/build/libs/server.jar
gradle :server:installDist   # build/install/server/bin/server(.bat)
```

启动顺序（`ServerMain`）：

1. `Config.load()` —— 读取 `./config.yml`（工作目录，不是 classpath）
2. `DatabaseManager.init()` —— SQLite `./database.db`，建表 + 迁移
3. `new SocketServer().start()` —— Netty，默认端口见配置
4. `new HttpServer().start()` —— Javalin，管理面板

## 配置 `config.yml`

`server/src/main/resources/config.yml` 只是**模板**。`Config.load()` 在 `./config.yml` 不存在时会用随机端口/账号/密钥生成一份，所以不要期望改资源里的模板会生效——运行目录下的那份文件才是实际配置。

| 键 | 说明 |
| --- | --- |
| `http-port` | Javalin 监听端口 |
| `socket-port` | Netty 验证端口 |
| `aes-secret` | 包体 AES 密钥（两侧必须一致） |
| `admin-username` / `admin-password` | 管理面板账号 |
| `upload-key` | 上传接口用的 key（`AdminController.uploadCloudNative` 目前为空实现） |
| `get-native-aes-secret`、`get-native-rsa-public-secret`、`get-native-rsa-private-secret` | 原生组件下发用的密钥对 |

> 模板里是 `httpPort`（驼峰），而 `Config.load()` 读取的是 `http-port`（连字符）。以自动生成的文件为准；若手写配置，请用连字符键名。

## 通信协议

Netty pipeline（`SocketServer`）：

```
LengthFieldBasedFrameDecoder(MAX, 0, 4, 0, 4)   # 4 字节长度前缀
LengthFieldPrepender(4)
ReadTimeoutHandler(40s)                          # 心跳需 ≤ 30s
PacketCodec                                     # AES + JSON
ServerHandler                                   # 业务
```

`PacketCodec`：

- 载荷 = Base64( AES/ECB/PKCS5Padding( JSON ) )，密钥为 `SHA-256(aesSecret)` 取前 16 字节（`EncryptionUtil.generateKey`）。
- JSON 顶层含 `type`（类名判别）、`time`、以及包字段。
- 时间戳来自 `EncryptionUtil.getOnlineTime("https://www.baidu.com")`（HEAD 请求取 `Date` 头）；服务端校验 30 秒新鲜度，超时直接关闭连接。**这个 HTTP 请求是每次编码都发生的**，离线环境会导致校验失败。
- 解码失败/类型未知都会关连接。

### 包类型

| 方向 | 类 | 字段 |
| --- | --- | --- |
| C2S | `LoginC2SPacket` | username, password, hwid, qq |
| C2S | `RegisterC2SPacket` | username, password, cardKey, hwid, qq |
| C2S | `UpdateIgnC2SPacket` | ign |
| C2S | `ChatC2SPacket` | message |
| S2C | `UserInfoS2CPacket` | success, message, banned, expiryDate, banExpiryDate, token |
| S2C | `ChatS2CPacket` | message, sender |
| S2C | `SyncUsersS2CPacket` | users (Map<用户名, IGN>) |
| S2C | `SystemMsgS2CPacket` | message |

新增包类型需要同时改：`common` 的 DTO、`PacketCodec.decode` 的 switch，以及 `ServerHandler` 的分支。

### 登录 / 注册流程（`ServerHandler`）

连接建立后 `authenticated=false`，只接受登录或注册：

- **登录**：用户名存在 → 密码匹配 → HWID 匹配 → 未封禁 → 记录 QQ、加入 `SessionManager`、回发 token，并广播上线条目。
- **注册**：卡密存在 → 用户名未被占用则新建用户（过期时间 = 现在 + 卡密时长）；已存在时必须密码与 HWID 均匹配，并叠加时长；卡密标记 `used` 并绑定用户。
- 认证后：`UpdateIgnC2SPacket` 更新 IGN 并广播用户同步；`ChatC2SPacket` 广播聊天。
- 断线：移除会话并广播离线。

`SessionManager` 用 Netty `AttributeKey` 存用户名/IGN/token，`activeSessions` 为 `用户名 → Channel`；同一用户名重复登录会踢掉旧连接。token 生成方式：`Base64(hwid XOR 最大QQ号)` 再用 `aesSecret` 加密。

## 数据库

SQLite + ORMLite，文件 `./database.db`，`DatabaseManager.init()` 自动建表并对旧表做 `ALTER TABLE` 补列（失败静默忽略）。

| 表 | 实体 | 关键字段 |
| --- | --- | --- |
| `users` | `User` | username(unique), password(明文), hwid, banned, expiryDate, banExpiryDate, qq, registerIp |
| `keys` | `Key` | content(unique), duration(ms), used, usedBy(外键 User), remark |
| `settings` | `Settings` | key/value；启动时补 `motd`、`latest_version`、`min_version` |
| `blacklist` | `Blacklist` | hwid 等 |
| `audit_logs` | `AuditLog` | operator, action, target, details, ip, timestamp |
| `chat_messages` | `ChatMessage` | 聊天记录（当前未写入） |

> 注意：`User.password` 是**明文比较**（`getPassword().equals(password)`），`jbcrypt` 只出现在已被注释掉的 `AuthController` 草稿里。若要做安全加固，这里是最该改的地方之一。

## HTTP 管理面板

`HttpServer`（Javalin 6，自定义 Gson `JsonMapper`）：

| 路径 | 说明 |
| --- | --- |
| `GET /admin/login` | 登录页（已登录跳转 `/admin`） |
| `POST /admin/login` | 表单登录，会话属性 `admin` |
| `GET /admin/logout` | 注销 |
| `GET /admin` | 控制台页面 |
| `GET /admin/css/*`、`/admin/js/*` | 静态资源（`resources/css|js`） |
| `/admin/api/*` | 以下 REST 接口，未登录返回 401 |

由 `AdminController` 处理：

```
GET    /admin/api/stats                     用户/卡密/在线数
GET    /admin/api/users                     用户列表
POST   /admin/api/users/{id}/ban            封禁/解封（可带到期时间）
POST   /admin/api/users/{id}/reset-password 重置密码
POST   /admin/api/users/{id}/kick            踢下线
POST   /admin/api/users/{id}/reset-hwid      重置设备码
GET    /admin/api/keys                      卡密列表
POST   /admin/api/keys/generate             批量生成卡密
POST   /admin/api/keys/delete               删除卡密
GET    /admin/api/online                    在线用户
POST   /admin/api/broadcast                 全服广播
GET/POST /admin/api/blacklist               黑名单查询/添加
DELETE /admin/api/blacklist/{id}            移除黑名单
GET/POST /admin/api/settings                设置项读写
GET    /admin/api/logs                      审计日志（分页）
```

页面文件：`server/src/main/resources/{admin,login}.html`、`css/{admin,login}.css`、`js/{admin,login}.js`。

## 部署建议

1. 在目标目录放置 `config.yml`（或首次启动让程序生成后立刻修改随机账号）。
2. 用 `gradle :server:build` 生成 `server.jar`，与 `database.db`、`config.yml` 同目录运行。
3. 反向代理只暴露所需端口；管理面板默认无 HTTPS、无防爆破。
4. 备份 `database.db`（单文件 SQLite）。
5. 若客户端要靠它做时间校验，确保机器能访问 `https://www.baidu.com`（见上文时间戳说明）。

## 已知未完成项

- `AuthController` 整份被注释（含 bcrypt、IP 注册限制、黑名单校验等逻辑），当前未接入路由。
- `AdminController.uploadCloudNative` 是空实现。
- `ChatMessage` 表无写入路径。
- 客户端侧无 socket 客户端实现。
