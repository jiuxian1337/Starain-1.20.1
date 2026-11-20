# Mixin 与事件注入点索引

所有 mixin 在 `today.starain.mixin`，必须在 `src/main/resources/starain.mixins.json` 中登记。

- `mixins`：通用（客户端与会逻辑都能加载）
- `client`：仅客户端
- `injectors.defaultRequire = 1`：除非显式 `require = 0`，注入点找不到会直接崩溃，改注入点时务必核对目标方法签名（Mojang 映射）。

## 通用 mixins

| Mixin | 目标 | 作用 |
| --- | --- | --- |
| `MixinConnection` | `Connection.channelRead0` / `send` | 发出 `PacketEvent(INCOMING)` / `PacketEvent(OUTGOING)`；`NetworkUtils.sendPacketNoEvent` 通过一个待透传集合绕过 OUTGOING |
| `MixinEntity` | `Entity.getViewVector`（`@Overwrite`）、`makeStuckInBlock`、`moveRelative` | `RayTraceEvent`（只对本地玩家，改写视线 yaw/pitch）、`StuckInBlockEvent`、`StrafeEvent` |
| `MixinItem` | `Item.getPlayerPOVHitResult` | `UseItemRayTraceEvent`（改写使用物品时的 yaw/pitch） |
| `MixinLivingEntity` | `jumpFromGround`、`travel`、`tickHeadTurn` | `JumpEvent`、`FallFlyingEvent`、`RotationAnimationEvent`（头部朝向动画） |
| `MixinPlayer` | `Player.attack` 多处 | `AttackYawEvent`、`AttackSlowdownEvent`（可取消击退/停止冲刺） |

## 客户端 mixins

| Mixin | 目标 / 注入点 | 作用 |
| --- | --- | --- |
| `MixinMinecraft` | `<init>` TAIL | 启动 `Bootstrap` |
| | `tick` HEAD/TAIL | `GameTickEvent(PRE)` / `GameTickEvent(POST)` |
| | `handleKeybinds` 中 `isUsingItem` 调用前 | `ClickEvent`（可取消点击处理） |
| | `runTick` HEAD | 更新 `AnimationUtils.delta` |
| | `resizeDisplay` HEAD | `SkiaContext.createSurface` + `KawaseBlur.resize` |
| `MixinLocalPlayer` | `tick`（`AbstractClientPlayer.tick` 前） | `PlayerTickEvent`（可取消整个 tick） |
| | `sendPosition` HEAD + cancel | **完整重写** vanilla 发送位置逻辑，使 `MotionEvent(PRE)` 能改坐标/角度（POST 在取消分支补发）；首 tick 发 `RespawnEvent` |
| | `LocalPlayer` 中减速点 | `SlowdownEvent` |
| `MixinClientLevel` | `tickNonPassenger` 的 `Entity.tick` | 消费 `Starain.skipTicks`，跳过本地玩家 tick |
| `MixinClientPacketListener` | `sendChat` HEAD | `ChatEvent`（可取消，可改消息内容） |
| `MixinConnection` | 见上 | 包事件 |
| `MixinGameRenderer` | `renderLevel` HEAD/RETURN、`renderHand` 字段读取前 | WorldColor 的进入/退出；`RenderWorldEvent`（3D 绘制点） |
| `MixinGui` | `render` 中 `renderCrosshair` 之后 | 每帧模糊/阴影批处理、`Glow.flushHud`、`ChatHudRenderer.renderPanel`、`RenderSkiaEvent` |
| | `renderEffects` HEAD | EffectHUD 启用时取消原版效果渲染 |
| | `displayScoreboardSidebar` | 交给 `ScoreboardModule`（可整段取消），并 redirect 逐行 `drawString` 以实现渐变 IP |
| `MixinEntityRenderer` | `renderNameTag` HEAD | NameTags 启用时隐藏其他玩家的原版头顶名 |
| `MixinLivingEntityRenderer` | `render` 中的 `Mth.rotLerp/lerp` | `RotationAnimationEvent`（身体/头部平滑） |
| `MixinItemInHandRenderer` | `renderArmWithItem` HEAD | Animations 模块接管持剑格挡姿态，否则完全交还原版 |
| `MixinItemEntityRenderer` | `render` | ItemPhysics：去掉旋转、浮动、放平 |
| `MixinKeyboardHandler` | `keyPress` HEAD | `KeyboardEvent`（只认主窗口） |
| `MixinMouseHandler` | `onPress` HEAD | `MouseButtonEvent`（只认主窗口） |
| `MixinKeyboardInput` | `Input.tick` TAIL | `MoveInputEvent`（改写前进/侧移/跳跃/潜行） |
| `MixinMultiPlayerGameMode` | `useItem` 的 `send` | `PositionItemEvent`（可替换/取消使用物品包） |
| `MixinLightTexture` | `updateLightTexture` 的 `OptionInstance.get`（ordinal 1） | FullBright 的 gamma 模式（MixinExtras `ModifyExpressionValue`） |
| `MixinShaderInstance` | `apply()` HEAD（`require = 0`） | WorldColor 修改 `COLOR_MODULATOR` |
| `MixinWindow` | `onFramebufferResize` HEAD | 重建 Skia surface + blur 尺寸 |
| `MixinLoadingOverlay` | `render` HEAD + cancel | 自定义加载动画 |
| `MixinChatComponent` | `render` 的 `fill` | 自定义聊天栏（`ChatComponentDuck` 暴露滚动/行数据） |
| `MixinChatScreen` | `render` 的 `fill`、`EditBox.render`、`mouseScrolled` | 自定义输入框与滚动 |
| `MixinCommandSuggestions` / `MixinSuggestionsList` | `render` | 聊天命令补全的定位与绘制 |
| `MixinJoinMultiplayerScreen` | `render` TAIL | 多人游戏界面右上角绘制「内置进服」按钮（见 [原生注入](native.md)） |
| `MixinScreenKeyForJoin` | `Screen.keyPressed` HEAD | 进服面板打开时吞掉 Esc 并关闭面板 |
| `MixinScreenJoiningOverlay` | `JoinMultiplayerScreen` / `ConnectScreen` / `ReceivingLevelScreen` 的 `render` TAIL | 连接过程中绘制全屏进服动画（`InjectedJoinScreen.renderJoiningOverlay`） |

## 旋转系统（多 mixin 协作）

`RotationManager` 本身是一个全局事件监听器，注册后接管以下事件：

```
MotionEvent(PRE)     ── 在 sendPosition 重写里被写入最终 yaw/pitch
MoveInputEvent       ── MoveUtils.fixMovement 保持移动方向与视线一致
RayTraceEvent        ── 所有射线检测（攻击/放置/使用）使用目标旋转
UseItemRayTraceEvent ── getPlayerPOVHitResult
PositionItemEvent    ── 重写 ServerboundMovePlayerPacket.PosRot
StrafeEvent / JumpEvent / AttackYawEvent / FallFlyingEvent / RotationAnimationEvent
RespawnEvent         ── 清空旋转状态
PlayerTickEvent      ── 每 tick 收敛
```

`RotationManager.setRotations(rotation, speed, raycast)` 设定目标；`raycast` 回调用于在视线被遮挡时加偏移，或回退到正对目标。

## 注意事项

1. **不要用 `@Overwrite`**（除非别无选择）。`MixinLocalPlayer.sendPosition` 由 `@Overwrite` 改成 `@Inject(HEAD)+cancel` 就是为了避免其他模组（如 controlify）因优先级相同而崩游戏。
2. 在 `Minecraft` 构造早期触发的 mixin（`<init>`、`resizeDisplay`）访问 `Starain.getInstance()` 必须先判空。
3. 改 `LocalPlayer`/`Connection` 相关注入点时，注意 `RespawnEvent` 依赖 `tickCount <= 1` 这个条件。
4. `MixinShaderInstance` 用了 `require = 0`，因为 `apply()` 在部分环境下可能被优化掉；这是刻意的。
5. 新增 `@Shadow` 字段前思考能否用 accesswidener；accesswidener 对运行时其他模组更友好。
