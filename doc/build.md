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
gradle build                  # 编译 + remap，产物 build/libs/starain-<平台>-<yyMMdd>.jar（默认平台 all）
gradle runClient              # 开发客户端（loom 已设 devauth.enabled=true）
gradle runServer              # 开发服务端
gradle extractRuntimeClasspath  # 把运行时依赖 jar 复制到 build/runtimeClasspath
gradle updateVersionInJava    # 把 Starain.VERSION 改成当前 project.version
```

没有测试源、没有测试框架、没有 lint/format 配置。验证方式是编译通过 + 启动客户端。

## 版本号

- `build.gradle`：`version = new Date().format('yyMMdd')`，即构建日期，例如 `260927`。
- 产物名：`starain-<平台>-<version>.jar`（平台见下一节，默认 `all`）。
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
- **Skia**：`io.github.humbleui:skija-shared` + 按平台选择的原生库，全部通过 `include(...)` 打进 jar。
  全项目统一 **0.143.18**（这是第一个同时提供桌面与 Android 原生库的版本）。
- **DevAuth**：`modRuntimeOnly`，仅开发环境登录正版账号，不会进入发布 jar。想关掉自动登录就注释 `build.gradle` 里 `runs.client` 的 `devauth.enabled`。

## 资源打包

- mixin 列表在 `src/main/resources/starain.mixins.json`，**新增 mixin 必须加进去**（`mixins` 与 `client` 两个数组按是否仅客户端区分）。
- 需要在 mixin 中访问的私有成员优先走 `src/main/resources/starain.accesswidener`，而不是 accessor mixin。
- 字体、着色器、声音、原生 DLL 都在 `src/main/resources/assets/starain/`。

## 发布

```bash
gradle clean build
# 产物：build/libs/starain-<平台>-<yyMMdd>.jar（以及 -sources.jar）
```

发布前建议：

1. `gradle updateVersionInJava` 让 `Starain.VERSION` 与实际版本一致。
2. 检查 `fabric.mod.json` 的 `depends` 是否仍匹配 loader/MC 版本。
3. 在 Windows 与至少一个非 Windows 环境各启动一次（Skia 原生库跨平台）。

## 多平台产物（-PstarainPlatform）

Skia 的原生库是分平台发布的，一份 jar 同时塞进所有平台会白白变大。构建时用一个属性选择要打包的
原生库，默认 `all`（桌面全平台，行为与以前的单 jar 一致）：

```bash
gradle build -PstarainPlatform=windows   # 只带 skija-windows-x64
gradle build -PstarainPlatform=macos     # 带 skija-macos-x64 + skija-macos-arm64
gradle build -PstarainPlatform=linux     # 只带 skija-linux-x64
gradle build -PstarainPlatform=android   # 只带 skija-android-arm64 + x64
gradle build -PstarainPlatform=all       # 桌面四个 + 安卓两个原生库全带（默认）
```

产物：`build/libs/starain-<平台>-<yyMMdd>.jar` 与对应的 `-sources.jar`（Gradle 的命名是
`<archivesName>-<version>`，所以平台名在前、日期在后）。

可选属性：

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `-PstarainPlatform` | `all` | `windows` / `macos` / `linux` / `android` / `all`，非法值直接构建失败 |
| `-PskijaVersion` | `0.143.18` | skija-shared 与桌面原生库版本 |
| `-PskijaAndroidVersion` | 跟随 `-PskijaVersion` | Android 原生库版本，一般不用单独指定 |

`gradle printPlatformInfo -PstarainPlatform=<平台>` 会打印本次实际打包的内容。

> **Android 产物说明（务必先读）**
>
> 1. Skija 的 `OperatingSystem` 枚举只有 `WINDOWS` / `LINUX` / `MACOS`，**没有 ANDROID**——
>    在安卓 JVM（`os.name=Linux`、`os.arch=aarch64`）上它只会去资源目录
>    `io/github/humbleui/skija/linux/arm64/` 找 `libskija.so`，永远找不到我们的 android 库。
> 2. 因此由模组自己处理：`today.starain.utils.render.skia.SkijaNatives#prepare()` 在 `Bootstrap#launch()`
>    最前面执行，检测到 Android 后从 jar 内的 `io/github/humbleui/skija/android/<arch>/libskija.so`
>    释放到 `-Dstarain.native.dir` / `java.io.tmpdir` / `user.dir` 之一（目录名 `starain-skija-<arch>`），
>    尽量补上可执行位，然后 `System.setProperty("skija.library.path", ...)`，让 Skija 走 `_loadFromDir`。
>    桌面端检测失败直接返回，行为不变。
> 3. 架构只支持 `arm64` 与 `x64`（Skija 只发布了这两个），32 位 ARM 设备无法使用。
> 4. 尚未在真机上验证；Android 上 Skia 的 GPU 上下文（EGL/GLES）也没有实测。

## CI（GitHub Actions）

`.github/workflows/build.yml` 在 push 到 `mh-dev`、PR 到 `mh-dev`、打 `v*` tag、手动触发时运行，
用 matrix 并行产出 5 个 artifact：`starain-windows`、`starain-macos`、`starain-linux`、
`starain-android`、`starain-all`。

- 5 个产物统一在 `ubuntu-latest` 上构建：打包只是把原生库塞进 jar，与宿主操作系统无关。
- 仓库没有 `gradlew`，所以用 `gradle/actions/setup-gradle@v4` 安装固定版本 **9.5.1**（与
  `gradle/wrapper/gradle-wrapper.properties` 一致），并复用依赖缓存。
- 打 tag 时额外的 `release` job 会把 5 个 jar 附到同一个 Release 上。

## 仓库状态

- 当前目录是 git 仓库（默认分支 `mh-dev`，远端 `github.com/jiuxian1337/Starain-1.20.1`）。
- `.gitignore` 明确忽略所有 AI 辅助配置，包括 `CLAUDE.md`、`.claude/`、`AGENTS.md`、`GEMINI.md` 等。
- `run/`（开发运行目录）、`build/`、`.gradle/`、`.idea/` 均被忽略。
