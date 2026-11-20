# 扩展指南

## 新增一个模块

1. **创建类**（放到对应分类包，如 `module/impl/combat`）：

```java
package today.starain.module.impl.combat;

public class ExampleAura extends ClientModule {
    private final BooleanValue onlyPlayers = new BooleanValue("Only Players", true, this);
    private final NumberValue range = new NumberValue("Range", 1.0, 6.0, 3.0, 0.1, this);
    private final ModeValue mode = new ModeValue("Mode", "Single",
            new String[]{"Single", "Multi"}, this);
    private final NumberValue switchDelay = new NumberValue("Switch Delay", 0, 500, 100, 10,
            this, () -> mode.isMode("Multi"));   // 仅 Multi 模式可见

    public ExampleAura() {
        setName("ExampleAura");
        setCategory(Category.COMBAT);
    }

    @EventTarget
    public void onTick(GameTickEvent e) {
        if (e.getType() != EventType.PRE || mc.player == null) return;
        // ...
    }

    @Override protected void onEnable()  { }
    @Override protected void onDisable() { }
}
```

2. **注册到 `ModuleManager`**：在构造函数里 `modules.add(new ExampleAura());`。位置决定 ClickGUI 栏内顺序（同分类内按插入顺序）。

3. 完成。`ModuleManager` 会把它作为聊天命令 `.<模块名>` 暴露，`Config` 会自动保存/恢复，ClickGUI 会自动生成参数行。

### 检查清单

- [ ] `setName` 唯一且稳定（改名会让旧配置丢这一项）。
- [ ] `setCategory` 正确（决定分栏）。
- [ ] 需要默认按键时 `setKey(...)`；鼠标用 `BindUtils.mouseCode(...)`。
- [ ] 依赖其他模块时用 `getModule(Xxx.class)`，并做空判断（模块可能在构造早期被查询）。
- [ ] 在客户端线程上操作游戏状态；耗时任务用 `Multithreading.runAsync`。
- [ ] 模块被禁用后不再收到事件，所以不需要在处理器里再判断 `isEnabled()`。

## 新增参数

直接声明字段即可，`Value` 构造器会自动注册：

```java
private final NumberValue speed = new NumberValue("Speed", 0.0, 10.0, 5.0, 0.1, this);
private final ColorValue color = new ColorValue("Color", new Color(91, 207, 250), this, someBoolean::get);
```

- 范围数值用 7 参数构造器：`NumberValue(name, min, max, rangeMin, rangeMax, step, parent, visible)`，此时 `get()` 每次返回区间内随机值（适用于 CPS、延迟抖动）。
- 可见性用最后一个 `Supplier<Boolean>`；null 等价于恒真。
- ClickGUI 的 `PropertyProvider` 会按类型自动生成 `BooleanPanel` / `ModePanel` / `NumberPanel` / `RangeNumberPanel` / `ColorPanel` / `StringPanel`。新 `Value` 子类需要同步：
  1. `PropertyProvider.create(...)` 加分支；
  2. `Config.save()/load()` 加序列化分支（否则不会被持久化）；
  3. `ValueEditor.suggestionsFor/describe/apply` 加聊天命令支持。

## 新增事件

1. 在 `event/impl` 建类，`implements Event`（需要可取消时再加 `Cancellable`）。
2. 尽量提供阶段字段（`EventType`）与所有可修改量，避免处理器只能读。
3. 在合适的 mixin 里 `Starain.getInstance().getEventManager().call(new XxxEvent(...))`，并把可修改字段写回原逻辑。
4. 事件方法名与参数类型要精确：总线的缓存按 `event.getClass()` 查找，**子类事件不会触发父类监听器**。

## 新增命令

两种方式：

**A. 独立命令类**（复杂逻辑）

```java
public final class ExampleCommand extends Command {
    public ExampleCommand() { super("example", "ex"); }   // 主名 + 别名

    @Override
    public void onCommand(String[] args) { /* args[0] 是命令名 */ }
}
```

并在 `CommandManager.load()` 里 `addCommand(new ExampleCommand())`，同时按需在 `loadDispatcher()` 里加补全分支。

**B. 模块参数**（推荐，"参数名 + 值"模型已覆盖大多数需求）：无需写代码，模块自动获得 `.<模块> <参数> <值>`。

修改参数后记得 `Starain.getInstance().getConfigManager().saveConfig();`。

## 新增 Mixin

1. 写类，包名必须是 `today.starain.mixin`。
2. 在 `starain.mixins.json` 的 `mixins`（通用）或 `client`（仅客户端）数组里登记。
3. 优先用 `@Inject` + `cancellable` 组合，而不是 `@Overwrite`——后者会与其他模组冲突（见 `MixinLocalPlayer.sendPosition` 的注释）。
4. 需要访问 MC 私有成员时，先考虑加到 `starain.accesswidener`。
5. 在新 mixin 里调用总线前做空判断，因为 `Minecraft` 构造早期 `Starain.getInstance()` 仍可能为 null：

```java
if (Starain.getInstance() == null || Starain.getInstance().getEventManager() == null) return;
```

## 新增渲染内容

- **2D / HUD / GUI**：在 `RenderSkiaEvent` 或 ClickGUI 的 `render` 中用 `Skia.*`；先 `Skia.save()` → `Skia.scale(guiScale)` → 绘制 → `Skia.restore()`。
- **模糊/阴影**：不要直接贴图，调用 `Skia.drawBlur(x,y,w,h,radius)` / `Skia.drawShadow(...)` 把区域加入静态 `Path`，`MixinGui` 每帧统一渲染一次。大量元素必须走这个批量路径。
- **3D 世界**：在 `RenderWorldEvent` 里用传入的 `PoseStack`。
- 细节见 [渲染架构](rendering.md)。

## 新增资源

- 字体放 `assets/starain/fonts/`，通过 `FontHelper.load("名字.ttf", size)` 或 `Fonts.getXxx(size)`。
- 着色器放 `assets/starain/shaders/`，用 `new Shader("x.vert", "x.frag")`。
- 图标字形在 `utils/render/skia/font/Icon`（Material Symbols）。

## 调试

- `ChatUtils.addChatMessage(...)` 打印到聊天栏（带 `[Starain]` 前缀）。
- `SkiaContext.draw` 内部异常、`EventManager.call` 里处理器异常都会抛出；渲染路径外层有 try/catch 打栈，看日志即可。
- 事件只在模块启用时订阅——"处理器没跑"先确认模块是启用的。
