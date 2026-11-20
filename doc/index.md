# 开发者文档

Starain 是一个 Fabric 客户端模组：

```
Starain-1.20.1/
├── src/            Fabric 客户端模组（package today.starain）
└── doc/            本文档
```

## 文档目录

| 文档 | 内容 |
| --- | --- |
| [架构总览](architecture.md) | 启动流程、事件总线、模块系统、配置文件、命令 |
| [构建与开发环境](build.md) | Gradle 命令、IDE 运行配置、发布产物、常见构建问题 |
| [扩展指南](extending.md) | 新增模块 / 参数 / 命令 / 事件的完整步骤 |
| [渲染架构](rendering.md) | Skia 画布、模糊/阴影批处理、GLSL 后处理、字体 |
| [Mixin 索引](mixins.md) | 每个 mixin 的注入点、发出的事件、注意事项 |

## 快速上手

```bash
# 编译客户端模组
gradle build

# 启动开发客户端（DevAuth 自动登录）
gradle runClient
```

更多内容见 [构建与开发环境](build.md)。

## 重要约定

- **静态工厂 `newInstance()`**：所有 manager 类使用私有构造 + 静态工厂，而不是 `new`。原因是发布版会经过 ZKM 混淆，mixin 无法混入 `invokedynamic`，只能走明文静态调用。新增 manager 请沿用此模式。
- **模块必须手动注册**：`ModuleManager` 没有类扫描，新模块需要加入其构造函数里的列表。
- **模块名即命令名与配置键**：重命名模块会让旧配置失效。
- **`@Overwrite` 风险**：`MixinLocalPlayer.sendPosition` 曾用 `@Overwrite`，会与其他模组的注入冲突，现已改为 `@Inject(HEAD) + cancel()` 并重写方法体。不要再改回去。
- **注释与界面文案使用中文**，与现有代码保持一致。
