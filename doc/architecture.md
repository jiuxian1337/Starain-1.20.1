# 架构总览

## 启动流程

```
Minecraft.<init> (TAIL)
  └─ MixinMinecraft → new Bootstrap("...")
       └─ Bootstrap.launch() → Starain.newInstance()
            ├─ EventManager.netInstance()         事件总线
            ├─ eventManager.register(this)         Starain 自身
            ├─ eventManager.register(RotationManager.newInstance())
            ├─ ModuleManager.newInstance()         注册全部模块
            ├─ CommandManager.newInstance()        注册命令 + Brigadier 补全
            └─ ConfigManager.newInstance()         载入 latest.json + 安装关闭钩子
```

`Bootstrap` 存在的唯一原因：mixin 类不能直接引用主类中带 `invokedynamic` 的代码（发布版会经 ZKM 混淆），所以用一个普通跳板类中转。

`Starain` 是单例（`Starain.getInstance()`），持有四个 manager。所有 manager 都以 `newInstance()` 静态工厂创建。

## 目录结构（客户端 `src/main/java/today/starain`）

| 包 | 职责 |
| --- | --- |
| `Starain` / `Bootstrap` | 单例与初始化跳板 |
| `event/api` | 事件总线、注解、`Cancellable`、`EventType`、`Priority` |
| `event/impl` | 具体事件类 |
| `module` | `ClientModule` 基类、`ModuleManager`、`Category` |
| `module/impl/{client,combat,movement,player,render,misc}` | 各模块实现 |
| `value` | `Value` 基类与 `BooleanValue` / `ModeValue` / `NumberValue` / `ColorValue` / `TextValue` / `DragValue` |
| `config` | JSON 配置读写 |
| `command` | 聊天命令与 Tab 补全（`ValueEditor`） |
| `ui` | `Theme`、`Design`、ClickGUI（`ui/clickgui/opal`）、加载动画 |
| `utils/render/skia` | Skia 画布、字体、图片、绘制辅助 |
| `utils/render/shader` | GLSL 后处理（`Framebuffer`、`Shader`、`KawaseBlur`、`OutlineGlow`） |
| `utils/rotation` | 旋转管理、射线检测 |
| `utils/player`、`utils/client`、`utils/math`、`utils/vector`、`utils/timer`、`utils/animation` | 辅助工具 |
| `utils/wrapper/Wrapper` | `mc` 快捷字段与配置目录解析 |
| `utils/injection` | 原生 DLL 注入 |
| `mixin` | 所有 mixin |

## 事件总线

`EventManager`（`event/api`）是反射式事件总线：

- 处理器是带 `@EventTarget`、**恰好一个参数**的方法。
- 可选 `@EventPriority(int)`；**数值越小越先执行**：`HIGHEST=0`、`HIGH=5`、`MEDIUM=10`（默认）、`LOW=15`、`LOWEST=20`。
- `register(obj)` 时一次性反射解析方法、`setAccessible(true)` 并按优先级排序，缓存为「事件类型 → 监听器数组」。`call(event)` 只是一次 map 查找 + 数组遍历。
- `unregister(obj)` 按对象移除；`unregisterAll()` 清空。

事件类位于 `event/impl`，大多是可变 POJO；需要阻断的 implements `Cancellable`（提供 `isCancelled()/setCancelled()`）。`EventType.PRE/POST/INCOMING/OUTGOING` 用于区分阶段（`GameTickEvent`、`MotionEvent`、`PacketEvent` 等）。

## 模块系统

`ClientModule`（`module`）：

```java
public class Example extends ClientModule {
    private final BooleanValue enabledThing = new BooleanValue("Thing", true, this);

    public Example() {
        setName("Example");            // 同时是命令名、配置键
        setCategory(Category.COMBAT);  // 决定 ClickGUI 分栏
        setKey(GLFW.GLFW_KEY_R);       // 可选默认绑定
    }

    @EventTarget
    public void onTick(GameTickEvent e) { /* 仅在启用时被调用 */ }

    @Override protected void onEnable()  { }
    @Override protected void onDisable() { }
}
```

关键语义：

- `setEnabled(true)` → 注册到事件总线 + `onEnable()`；`setEnabled(false)` → 反注册 + `onDisable()`。所以 `@EventTarget` 只在模块启用期间被调用。
- 构造函数里通过 `new XxxValue(name, ..., this)` 添加参数：`Value` 构造器会把自己加入 `parent.getValues()`。
- 模块必须在 `ModuleManager` 构造函数里手动 `modules.add(new ...)`，没有扫描。
- `getSuffix()` 影响 ArrayList 显示名。
- `Config.load()` 在启动时恢复每个模块的 `enabled` 与按键。

## 参数（Value）

| 类型 | 说明 |
| --- | --- |
| `BooleanValue` | 布尔开关 |
| `ModeValue` | 枚举模式字符串，`isMode(...)`、`next()` |
| `NumberValue` | 单值或**范围**（两组构造器）；范围模式下 `get()` 返回区间内随机值，范围随机用于 CPS/延迟等 |
| `ColorValue` | `java.awt.Color`，`getArgb()/setArgb()` |
| `TextValue` | 字符串 |
| `DragValue` | x/y/width/height，用于 HUD 拖拽位置 |

构造器最后一个（可选）参数是 `Supplier<Boolean> visible`，`Value.isVisible()` 控制 ClickGUI 是否显示该行——用于「模式 B 才显示参数 B」。

## 配置

- 路径：`<gameDir>/Starain/<name>.json`，默认 `latest`。
- 格式：`{ "time": ..., "modules": { "<模块名>": { "enabled": bool, "key": int, "values": {...} } } }`，漂亮打印。
- `Config.save()` 按 `Value` 运行时类型分别序列化；范围 `NumberValue` 存 `{min,max}`。
- `ConfigManager` 在构造时载入 `latest`，并注册 JVM 关闭钩子保存（覆盖 Alt+F4 与 IDE Stop）。
- 聊天命令修改参数后立即 `saveConfig()`。

## 命令

`CommandManager`：

- 前缀 `.`（`CommandManager.PREFIX`）。
- 消费在 `onChat(ChatEvent)` 中完成：命中命令或模块名后取消聊天消息（不会发到服务器）。
- 内置命令：`bind`/`b`、`toggle`/`t`、`config`/`cfg`。
- **每个模块自动注册为命令**：`.<模块> [参数] [值]`。
- `CommandDispatcher`（Brigadier）**只用于补全**，`executes` 返回 1，真正执行在 `onChat` 里。
- 参数名匹配走 `ValueEditor.normalize()`（去掉非字母数字、转小写），所以 `Aim range` 可用 `AimRange`/`aimrange`。

## 按键绑定

- 绑定码：键盘直接用 GLFW key code；鼠标用 `BindUtils.MOUSE_OFFSET(1000) + button`。
- `ModuleManager` 监听 `KeyboardEvent` / `MouseButtonEvent`，仅在 `press` 且无界面打开时匹配 `module.getKey()`。
- `if (module.getKey() != 0 && module.getKey() == bindCode)` —— 0 表示未绑定。

## ClickGUI

`ui/clickgui/opal`：

- `OpalClickGUI extends Screen`，由 `ClickGuiModule.onEnable()` 打开（默认右 Shift）。
- 每个 `Category` 一列：`CategoryPanel`；`CONFIG` 用 `ConfigCategoryPanel`。
- 模块行 `ModulePanel` 展开后由 `PropertyProvider` 为每个 `Value` 创建对应的 `XxxPanel`。
- 主题常量在 `OpalTheme`（布局/字号/颜色基色），可调项实时读 `ClickGuiModule`，强调色读 `Theme`。
- `Theme` 从 `HUD` 模块读主题模式（Solid / Gradient / Rainbow）、主/辅色、速度、扩散。

## 全局状态

| 字段 | 用途 |
| --- | --- |
| `Starain.skipTicks` | 跳过本地玩家 tick 的计数（`MixinClientLevel` 消费），NoFall / Scaffold 使用 |
| `RotationManager.rotations/active` | 当前接管的目标旋转 |
| `KillAura.targets` | 当前目标列表，TargetHUD / ESP 读取 |
| `Skia.blurPath` / `Skia.shadowPath` | 每帧累积的模糊/阴影区域 |
| `AnimationUtils.delta` | 每帧毫秒差，动画用 |
