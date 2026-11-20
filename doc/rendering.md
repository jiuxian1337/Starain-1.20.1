# 渲染架构

Starain 有两条互不干扰的渲染路径：**Skia（2D/GUI）** 与 **GLSL 后处理（3D 世界）**。两者最终都画到主 framebuffer 上。

## 一、Skia 路径

依赖 `io.github.humbleui:skija` 0.116.4，原生库随 jar 打包。

### 画布生命周期

```
MixinWindow.onFramebufferResize  ─┐
MixinMinecraft.resizeDisplay     ─┼─> SkiaContext.createSurface(w, h)
                                  │      ├─ DirectContext.makeGL()
                                  │      └─ Surface.wrapBackendRenderTarget(..., frameBufferId)
```

`SkiaContext.draw(consumer)` 是唯一的绘制入口，它负责在 Skia 与 MC 的 GL 状态之间来回切换：

1. 重置 `pixelStore`，`context.resetGLAll()`；
2. 执行你的绘制 lambda；
3. `context.flush()`，`BufferUploader.reset()`；
4. 恢复 blend / colorMask / depthMask / scissor / depthTest / activeTexture / cull / shaderColor 等状态。

> 第 4 步很关键：Skia 不改 `RenderSystem` 的着色器颜色，而 ClickGUI 全走 Skia，若不显式恢复白色，下一帧世界会偏色。

### 坐标约定

所有绘制代码都是：

```java
SkiaContext.draw(canvas -> {
    Skia.save();
    Skia.scale((float) Minecraft.getInstance().getWindow().getGuiScale());  // 逻辑坐标
    // 用 GUI 逻辑坐标绘制
    Skia.restore();
});
```

因此 `Skia` 的 API 一律接收 **GUI 缩放后的逻辑坐标**（与 `getGuiScaledWidth()` 同一坐标系）。

### 每帧渲染顺序（`MixinGui`，插在 `renderCrosshair` 之后）

1. `KawaseBlur.INSTANCE.draw(...)` —— 把整屏做主 framebuffer 的降采样模糊，得到一张全屏纹理；
2. `Glow.flushHud()` —— 批量绘制 HUD 发光矩形；
3. `SkiaContext.draw`：
   - 若 HUD 开了 Blur：用 `Skia.blurPath` 做裁剪，把模糊纹理贴回来；
   - 若 HUD 开了 Shadow：用 `Skia.shadowPath` 的 `DIFFERENCE` 裁剪画模糊阴影；
   - `ChatHudRenderer.renderPanel()`；
   - `RenderSkiaEvent` —— 所有 HUD 模块在这里画。

### 批量模糊 / 阴影

`Skia.drawBlur(x,y,w,h,radius)` 和 `Skia.drawShadow(...)` **不立即绘制**，只是把圆角矩形追加到静态 `Skia.blurPath` / `Skia.shadowPath`。`MixinGui` 每帧统一消费一次并重置为 `new Path()`。这就是「几十个 HUD 元素只付一次模糊代价」的原因。

同理 `Glow.queueHud(...)` 把发光矩形排队，`Glow.flushHud()` 一次性绘制。

> 代价：模糊/阴影使用的是上一帧累积的区域，帧间位移过大时会出现「字跑在模糊前面」的拖影。

### 文字

- `Skia.drawText` / `Skia.drawBaselineText` 支持 Minecraft 颜色码（`§x`）分段。
- 字体来自 `utils/render/skia/font`：`FontHelper` 按文件名缓存 `Typeface`；`Fonts` 提供 Noto Sans SC（中英）、Inter、Borel、Urbanist 与 Material Symbols 图标字体。
- `Icon` 是 Material Symbols 的码点常量表（约 3000 个）。

### 图片 / 纹理

`ImageHelper` 用 `Image.adoptGLTextureFrom(SkiaContext.getContext(), texture, GL_TEXTURE_2D, ...)` 把 MC 的 GL 纹理包成 Skia `Image`（例如画玩家头像）。

## 二、GLSL 路径（世界渲染）

`utils/render/shader` 是一套自写的后处理框架：

| 类 | 作用 |
| --- | --- |
| `Framebuffer` | 创建 FBO + 颜色纹理，支持 `sizeMulti`（0.5、0.25…）与 RGBA/RGB |
| `Shader` | 编译/链接 `assets/starain/shaders/*`，统一设置 uniform |
| `ShaderHelper` | 绑定纹理等辅助 |
| `PostProcessRenderer` | 全屏 quad 的 VAO/VBO/IBO 与 `beginRender/endRender` |
| `KawaseBlur` | 多级降采样 + 升采样模糊（6 级 FBO） |
| `OutlineGlow` | 发光/描边 |

着色器文件：`blur.vert/frag`、`passthrough.*`、`glow.*`、`glowmask.frag`、`worldcolor.*`。

### 世界空间绘制点

`MixinGameRenderer` 在 `renderLevel` 里读取 `renderHand` 字段（`@At(value="FIELD", opcode=180, ordinal=0)`）之前发出 `RenderWorldEvent`。此时 `PoseStack` 已带相机朝向，处于世界渲染坐标系，适合画 3D ESP、轨迹、目标光环。`RenderWorldEvent` 的处理者（`Glow`、`TargetESP`、`Trajectories`、`ESP2D` 等）直接使用传入的 `PoseStack`。

### WorldColor

`WorldColor` 通过 `MixinShaderInstance` 在每次 `ShaderInstance.apply()` 时改写 `COLOR_MODULATOR` uniform，实现全屏/手持物品色调。`RenderSystem.getShaderColor()` 是输入，`WorldColor.modulate(...)` 是实际映射。

## 三、ClickGUI 渲染

`OpalClickGUI.render` 独立于 HUD 路径，自己调用 `SkiaContext.draw`：

- 逻辑尺寸来自 `window.getGuiScaledWidth/Height`；
- `OpalTheme` 提供布局常量（`PANEL_SPACING=10`、`PANEL_TOP=25`、`ROW_HEIGHT=20`）与可调项（半径、面板宽、透明度实时读 `ClickGuiModule`）；
- 强调色走 `Theme.accent(index)`，支持 Solid / Gradient / Rainbow；
- `closing` 时最后一栏收起的动画结束后 `setScreen(null)`。

## 四、加载动画

`MixinLoadingOverlay` 在 `LoadingOverlay.render` 的 HEAD 直接 `ci.cancel()` 并自绘：

- 用 `HelloAnimation` 的路径数据 + `PathMeasure` 做描边生长动画；
- 进度条与淡入淡出自己算；
- 若 `SkiaContext` 尚不可用则退化为 `GuiGraphics.fill` 的简单进度条；
- 满足 `HOLD_MILLIS` 且资源加载完成后调用 `onFinish`，并在动画结束后移除 overlay。

## 五、性能注意事项

- 新 HUD 元素优先用 `Skia.drawBlur/drawShadow` 的批处理，而不是逐元素贴图。
- 模糊分辨率由 `HUD.blurRadius`、`Blur Iterations` 控制；`Limit Blur FPS` 可把模糊刷新限制在 25–165 fps。
- `SkiaContext.draw` 每帧至少两次（HUD + 可能的 ClickGUI），不要在 lambda 里做重计算。
- 大量实体渲染（NameTags/Glow）应做距离与视锥裁剪；`RayCastUtil.inView` 提供现成判断。
