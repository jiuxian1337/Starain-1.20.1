# 原生注入

Starain 包含两个 Windows x64 原生 DLL，均打包在 `src/main/resources/assets/starain/native/`：

| DLL | 作用 |
| --- | --- |
| `starain_inject.dll` | JNI 桥接，暴露注入相关 native 方法 |
| `MinecraftProxy_msvc.dll` | 注入到目标客户端进程中的代理 |

## 加载流程

```
NativeInjector.ensureLoaded()
  ├─ extractResource("/assets/starain/native/starain_inject.dll")
  │     └─ 复制到临时文件（deleteOnExit），返回 Path
  └─ System.load(path)

ProxyInjector.tryInject()
  ├─ 若 127.0.0.1:25565 已在监听 → 视为已注入，直接返回 true
  ├─ NativeInjector.ensureLoaded()
  ├─ findProxyDllPath()：解压 MinecraftProxy_msvc.dll
  └─ NativeInjector.injectProxyDll(path, 0)
```

`injectProxyDll(String dllPath, int timeoutMs)` 返回 `null` 表示成功，否则返回错误码字符串（`NO_TARGET_WINDOW` 会被映射为「未找到布吉岛窗口」）。`getLastInjectedPid()` 返回最近注入的进程 PID。

## 进服界面（`InjectedJoinScreen`）

`ui/injection/InjectedJoinScreen` 是原生注入的用户入口，状态机 `INJECTING → INJECTED → JOINING`，失败转 `FAILED`：

1. 多人游戏界面右上角的「内置进服」按钮（`MixinJoinMultiplayerScreen` 在 `render` TAIL 自绘）调用 `open()`；
2. `open()` 先探测 `ProxyInjector.isProxyListening()`，未监听则在后台线程执行 `ProxyInjector.tryInject()`；
3. 注入成功后按钮变为「连接」，`enter(JOINING)` 启动 `runJoinAnimation()`：
   - tail `logs/latest.log` 做实时日志滚动（`isNoise` 过滤堆栈、WARN/ERROR、OpenGL 噪声）；
   - 反射拿到 `ConnectScreen` 内的 `Connection` / Netty `Channel` 并主动关闭；
   - 以 `ServerData("Starain Proxy", "127.0.0.1:25565")` 重新 `ConnectScreen.startConnecting`，经本地代理进服；
4. 进服过程中的全屏动画由 `MixinScreenJoiningOverlay` 绘制，Esc 由 `MixinScreenKeyForJoin` 拦截。

> 该界面依赖反射访问 `ConnectScreen` / `Connection` 的私有字段，MC 版本或映射变化时最容易失效。

## 注意事项

- **仅 Windows x64**：DLL 与 `System.load` 路径都假定 Windows。其他平台 `ensureLoaded()` 返回 false，错误信息在 `NativeInjector.loadError()`。
- **失败不抛异常**：注入失败通过 `ProxyInjector.failReason()` / `isInjected()` 暴露，调用方应检查返回值而不是 catch。
- **幂等**：`tryInject()` 是 `synchronized` 的，并且会先探测 `25565` 是否已被占用（代理启动后监听该端口），因此重复调用是安全的。
- 临时 DLL 在进程退出时删除（`deleteOnExit`）；如果失败，检查临时目录权限。
- 代理端口常量：`ProxyInjector.PROXY_PORT = 25565`。
- `getLanAddress()` 会跳过 `virtual`、`vEthernet`、`VMware`、`VirtualBox`、`WSL`、`Hyper-V` 网卡，用于给局域网连接选择地址。

## 扩展 native 方法

1. 在 `NativeInjector` 中声明 `public static native ...`。
2. 在 `starain_inject.dll` 中实现对应 JNI 符号（名称为 `Java_today_starain_utils_injection_NativeInjector_<方法名>`）。
3. 重新打包资源并验证 `System.load` 成功。

> 该 DLL 的源码不在本仓库中，只有编译产物。
