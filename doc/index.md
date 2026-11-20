# 开发者文档

Starain 由三个 Gradle 子项目组成，它们之间只有「共享数据类」一层关系：

```
Starain-1.20.1/
├── src/            Fabric 客户端模组（package today.starain）
├── common/         :common — 客户端/服务端共享的包定义与加解密（dev.jiuxian.common）
├── server/         :server — 独立验证后端（dev.jiuxian.server），依赖 :common
└── doc/            本文档
```

> 注意：客户端目前**没有**引用 `:common` 或 `:server`。两者只在包格式上对应，客户端侧的登录/聊天客户端代码不在本仓库。

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [架构总览](architecture.md) | 启动流程、事件总线、模块系统、配置文件、命令 |
| [构建与开发环境](build.md) | Gradle 命令、IDE 运行配置、发布产物、常见构建问题 |
| [扩展指南](extending.md) | 新增模块 / 参数 / 命令 / 事件的完整步骤 |
| [渲染架构](rendering.md) | Skia 画布、模糊/阴影批处理、GLSL 后处理、字体 |
| [Mixin 索引](mixins.md) | 每个 mixin 的注入点、发出的事件、注意事项 |
| [验证后端](server.md) | 协议、数据库、HTTP 管理面板、部署 |
| [原生注入](native.md) | JNI 加载、代理 DLL、限制条件 |

## 快速上手

```bash
# 编译客户端模组
gradle build

# 启动开发客户端（DevAuth 自动登录）
gradle runClient

# 验证后端
gradle :server:run
```

更多内容见 [构建与开发环境](build.md)。

## 重要约定

- **静态工厂 `newInstance()`**：所有 manager 类使用私有构造 + 静态工厂，而不是 `new`。原因是发布版会经过 ZKM 混淆，mixin 无法混入 `invokedynamic`，只能走明文静态调用。新增 manager 请沿用此模式。
- **模块必须手动注册**：`ModuleManager` 没有类扫描，新模块需要加入其构造函数里的列表。
- **模块名即命令名与配置键**：重命名模块会让旧配置失效。
- **`@Overwrite` 风险**：`MixinLocalPlayer.sendPosition` 曾用 `@Overwrite`，会与其他模组的注入冲突，现已改为 `@Inject(HEAD) + cancel()` 并重写方法体。不要再改回去。
- **注释与界面文案使用中文**，与现有代码保持一致。
