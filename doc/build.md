# 构建与开发环境

## 前置

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 客户端字节码目标为 17（`options.release = 17`） |
| Gradle | 9.x | 仓库**没有** `gradlew` / `gradlew.bat` / wrapper jar，只有 `gradle/wrapper/gradle-wrapper.properties`（固定 9.5.1） |
| IntelliJ IDEA | 2025.x | 已带 `.idea/runConfigurations`，推荐用 IDE 跑客户端 |

如果 `gradle` 不在 PATH，可以用本地 Gradle 发行版：

```
%USERPROFILE%\.gradle\wrapper\dists\gradle-9.5.1-bin\<hash>\gradle-9.5.1\bin\gradle.bat
```

或直接按 IDE 里的 run configuration 启动。

## 常用命令

```bash
gradle build                  # 编译 + remap，产物 build/libs/starain-<yyMMdd>.jar
gradle runClient              # 开发客户端（loom 已设 devauth.enabled=true）
gradle runServer              # 开发服务端
gradle extractRuntimeClasspath  # 把运行时依赖 jar 复制到 build/runtimeClasspath
gradle updateVersionInJava    # 把 Starain.VERSION 改成当前 project.version
```

没有测试源、没有测试框架、没有 lint/format 配置。验证方式是编译通过 + 启动客户端。

## 版本号

- `build.gradle`：`version = new Date().format('yyMMdd')`，即构建日期，例如 `260927`。
- 产物名：`starain-<version>.jar`。
- `Starain.VERSION` 是硬编码字符串（当前 `260717`）；除非显式执行 `updateVersionInJava`，否则不会自动同步。
- `processResources` 会把 `${version}`、`${minecraft_version}`、`${loader_version}` 展开进 `fabric.mod.json`。

## IDE 运行配置

`.idea/runConfigurations/` 下有两个 Application 配置，均通过 `net.fabricmc.devlaunchinjector.Main` 启动：

```
Minecraft Client:
  -Dfabric.dli.config=<proj>\.gradle\loom-cache\launch.cfg
  -Dfabric.dli.env=client
  -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient
  -XX:StackShadowPages=32
  -Ddevauth.enabled=true
  working dir: run/
```

首次跑之前需要让 Loom 生成 `launch.cfg`（IDEA 的 Gradle 同步或先执行一次 `gradle build`）。

## 依赖要点

- **映射**：Mojang 官方映射（`loom.officialMojangMappings()`），所以代码里是 `Minecraft`、`LocalPlayer`、`GuiGraphics` 这类官方名。
- **Fabric API 未使用**：没有 `fabric-api` 依赖，只依赖 loader。
- **Lombok**：1.18.46。改 `build.gradle` 时注意版本一致。
- **Skia**：`io.github.humbleui:skija-shared` 与 5 个平台原生库（win/linux/mac-x64/mac-arm64）全部通过 `include(...)` 打进 jar。
- **DevAuth**：`modRuntimeOnly`，仅开发环境登录正版账号，不会进入发布 jar。想关掉自动登录就注释 `build.gradle` 里 `runs.client` 的 `devauth.enabled`。

## 资源打包

- mixin 列表在 `src/main/resources/starain.mixins.json`，**新增 mixin 必须加进去**（`mixins` 与 `client` 两个数组按是否仅客户端区分）。
- 需要在 mixin 中访问的私有成员优先走 `src/main/resources/starain.accesswidener`，而不是 accessor mixin。
- 字体、着色器、声音、原生 DLL 都在 `src/main/resources/assets/starain/`。

## 发布

```bash
gradle clean build
# 产物：build/libs/starain-<yyMMdd>.jar（以及 -sources.jar）
```

发布前建议：

1. `gradle updateVersionInJava` 让 `Starain.VERSION` 与实际版本一致。
2. 检查 `fabric.mod.json` 的 `depends` 是否仍匹配 loader/MC 版本。
3. 在 Windows 与至少一个非 Windows 环境各启动一次（Skia 原生库跨平台）。

## 仓库状态

- 当前目录**不是** git 仓库（`git rev-parse` 会失败）。
- `.gitignore` 明确忽略所有 AI 辅助配置，包括 `CLAUDE.md`、`.claude/`、`AGENTS.md`、`GEMINI.md` 等。
- `run/`（开发运行目录）、`build/`、`.gradle/`、`.idea/` 均被忽略。
