# Starain

Starain 是一个面向 Minecraft **1.20.1 Fabric** 的客户端功能整合模组。它提供了一套完整的模块系统、事件总线、基于 Skia 的自绘 UI/ClickGUI，以及一套独立的验证/授权后端（`server` 模块）。

> 本项目采用「全部自绘 GPU 渲染」：HUD、ClickGUI、聊天栏、名字牌、记分板都走 Skia，世界内的发光/描边走 GLSL 后处理。

---

## 功能概览

| 分类 | 模块 |
| --- | --- |
| **Combat** | KillAura、Velocity、Criticals、KeepSprint、AutoHeal、AutoThrow |
| **Player** | Scaffold、NoFall、InvManager、ChestStealer、AutoTools |
| **Movement** | Sprint、NoSlow、NoJumpDelay、FastWeb、Stuck |
| **Visual** （Category 名为 Render） | Animations、Glow、2DESP、TargetESP、NameTags、Trajectories、FullBright、ItemPhysics、TimeChanger、WorldColor |
| **Misc** | Target、Teams、AntiBot、GhostHand、MiddleClickPearl、HealthBypass、TitleReplace |
| **Client** | ClickGui、HUD、ArrayList、TargetHUD、EffectHUD、ScoreboardModule、Notification |

每个模块都有可调参数（开关、模式、数值、颜色、按键绑定），全部保存到配置文件。

### 界面特性

- **Opal 风格 ClickGUI**：分栏、搜索（`Ctrl+F`）、滚轮滚动、拖拽移动、按住 `Tab` 显示按键绑定。
- **主题色**：支持纯色 / 渐变 / 彩虹三种模式，全局同步。
- **动态 HUD**：ArrayList 动画、TargetHUD、效果栏、记分板、自定义聊天栏（可滚动、平滑动画）。
- **模糊与阴影**：所有 HUD 元素的模糊/阴影会**合并成一帧只渲染一次**，即使是大量元素也不会明显掉帧。
- **加载动画**：替换原版加载条，启动时绘制自定义描边动画。

---

## 环境要求

| 项目 | 要求 |
| --- | --- |
| Minecraft | **1.20.1** |
| Fabric Loader | **≥ 0.19.3** |
| Java | **17**（客户端） |
| Fabric API | 不需要 |
| 操作系统 | Windows 10/11 x64（原生注入功能仅 Windows 支持） |

Skia 的原生库已随 jar 打包（Windows / Linux / macOS x64 / macOS ARM64），不需要额外安装。

---

## 安装

1. 安装对应 Minecraft 1.20.1 的 Fabric Loader。
2. 把 `starain-<版本>.jar` 放进 `.minecraft/mods/`。
3. 启动游戏。

模组是**纯客户端**的（`"environment": "client"`），不要装到服务端。

---

## 使用

### 默认按键

| 按键 | 功能 |
| --- | --- |
| **右 Shift** | 打开 / 关闭 ClickGUI |

其余模块默认没有绑定按键。可以：

- 在 ClickGUI 里点击模块行右侧的按键区域，按下要绑定的键；
- 或者在聊天栏用 `.bind <模块> <按键>`。

### 内置进服（Windows）

多人游戏界面（服务器列表）右上角有一个「内置进服」按钮：

- 点击后会在后台把代理 DLL 注入到客户端进程，注入成功后可一键经本地代理（`127.0.0.1:25565`）进服；
- 连接过程会显示自定义全屏动画与实时日志，按 `Esc` 可关闭面板。

该功能**仅 Windows x64**。非 Windows 平台请忽略此按钮。

### ClickGUI

- 顶部栏可拖动窗口位置；位置会自动保存。
- `Ctrl+F` 搜索模块名。
- 点击模块行左侧开关启用/禁用，点击行本身展开参数。
- 按住 `Tab` 查看所有已绑定按键。
- 最后一栏 **Config** 用于新建 / 保存 / 删除 / 载入配置，点文件夹图标可直接打开配置目录。

### 聊天栏命令

命令前缀是 **`.`**。在聊天框输入：

```
.bind <模块> <按键>          给模块绑定按键（.b）
.toggle <模块>               切换模块开关（.t）
.config <load|save> <名字>   载入 / 保存配置（.cfg）

.<模块>                      查看模块所有参数
.<模块> <参数>               查看某个参数的当前值
.<模块> <参数> <值>          修改参数并立即保存
```

示例：

```
.bind KillAura R
.toggle Sprint
.config save pvp
.KillAura Mode Switch
.KillAura CPS 16
```

参数名可以省略空格（`Aim range` 写成 `AimRange` 或 `aimrange`），大小写不敏感，支持 Tab 补全。

可用按键名：`A`–`Z`、`0`–`9`、`MouseLeft` / `MouseRight` / `MouseMiddle`、`Mouse4`…、`LShift` / `RShift` / `LCtrl` / `RCtrl` / `LAlt` / `RAlt`、`Space`、`Enter`、`Esc`、`Tab`、`Up` / `Down` / `Left` / `Right`、`Delete`、`Backspace`，以及 `Key<GLFW编号>`。

### 配置文件

配置保存在游戏目录下：

```
.minecraft/Starain/latest.json     # 默认配置
.minecraft/Starain/<名字>.json     # 自定义配置
```

正常退出、`Alt+F4`、甚至 IDE 停止运行都会自动保存（通过 JVM 关闭钩子）。

---

## 常见问题

**游戏启动崩溃 / 找不到类？**
确认是 Minecraft 1.20.1 + Fabric Loader ≥ 0.19.3 + Java 17，并确认没有同时装其他修改同一渲染流程的模组。

**打开 ClickGUI 卡顿？**
在 HUD 模块里关闭 Blur 或调低 Blur Iterations；也可以开启 `Limit Blur FPS` 限制模糊刷新率。

**改了参数没保存？**
聊天命令修改会立即保存。ClickGUI 里的修改在退出游戏或切换配置时保存。

**窗口缩放后 UI 错位？**
窗口尺寸变化会自动重建渲染表面；若仍异常，重启客户端。

**原生注入功能提示未找到窗口？**
该功能仅支持 Windows，且需要游戏以常规方式运行。失败原因会显示在客户端提示中。

---

## 开发者文档

面向开发者的架构、构建方式、扩展指南都在 [`doc/`](doc/index.md)：

- [架构总览](doc/architecture.md)
- [构建与开发环境](doc/build.md)
- [模块 / 参数 / 命令扩展指南](doc/extending.md)
- [渲染架构（Skia 与 GLSL）](doc/rendering.md)
- [Mixin 与事件注入点索引](doc/mixins.md)
- [验证后端 server](doc/server.md)
- [原生注入](doc/native.md)

---

## 许可与声明

Copyright © 2026. All rights reserved.

本项目仅用于技术研究与学习。使用前请确认目标服务器允许客户端模组；因使用本软件导致的封禁、数据丢失或其他后果由使用者自行承担。
