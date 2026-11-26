package today.starain.utils.render.skia;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;

/**
 * Skija 的 {@code OperatingSystem} 枚举只有 WINDOWS/LINUX/MACOS，没有 ANDROID，
 * 在安卓 JVM 上它会按 "linux" + "arm64" 去找 {@code io/github/humbleui/skija/linux/arm64/libskija.so}，
 * 而安卓的原生库在 {@code io/github/humbleui/skija/android/<arch>/libskija.so}，永远命中不了。
 * <p>
 * 这里在 Skija 任何类被初始化之前把原生库释放到可写目录，并写入 {@code skija.library.path}，
 * 让 {@code io.github.humbleui.skija.impl.Library#load()} 走 {@code _loadFromDir} 分支
 * （该分支实际执行 {@code System.load(dir + "/" + System.mapLibraryName("skija"))}）。
 * 桌面端直接跳过，行为与之前完全一致。
 * <p>
 * 调用时机非常关键：{@code Library} 在 {@code DirectContext} 的静态初始化块里就会被加载，
 * 而 {@code MixinMinecraft} 在 {@code Minecraft.<init>} 的 {@code resizeDisplay} 阶段就会创建 Skia 表面，
 * 那时 {@code Bootstrap} 还没跑。所以这里的 {@link #prepare()} 会在四个时机被调用（幂等）：
 * <ol>
 *   <li>{@code fabric.mod.json} 里的 {@code preLaunch} 入口点——最早的时机；</li>
 *   <li>{@code SkiaContext} / {@code Skia} 的静态初始化块——兜底；</li>
 *   <li>{@code Bootstrap#launch()}——历史遗留，保留无害。</li>
 * </ol>
 */
public final class SkijaNatives {

    /** 释放原生库时可选的根目录属性，按顺序尝试第一个写得进去的。 */
    private static final String[] BASE_DIR_PROPERTIES = {
            "starain.native.dir", "java.io.tmpdir", "user.home", "user.dir"
    };

    /** 安卓系统的特征文件（只要能 stat 到就说明是安卓）。 */
    private static final String[] ANDROID_MARKER_FILES = {
            "/system/build.prop", "/system/bin/app_process", "/system/bin/app_process64"
    };

    private static boolean prepared;

    private SkijaNatives() {
    }

    /**
     * 幂等；必须在任何 Skija 类被加载之前调用。不能使用 lambda/方法引用：
     * 这个类在 mixin 直接触达的初始化链路上。
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;

        try {
            String existing = System.getProperty("skija.library.path");
            if (existing != null && !existing.isEmpty()) {
                log("skija.library.path 已由外部指定：" + existing);
                return;
            }

            log("系统 = " + System.getProperty("os.name") + " / " + System.getProperty("os.version")
                    + " / " + System.getProperty("os.arch")
                    + "，JVM = " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vendor"));

            if (!isAndroid()) {
                return;
            }

            String arch = currentArch();
            if (arch == null) {
                log("无法识别的 CPU 架构 " + System.getProperty("os.arch") + "，跳过 Skija 原生库释放");
                return;
            }

            String resource = "/io/github/humbleui/skija/android/" + arch + "/libskija.so";
            byte[] data = readResource(resource);
            if (data == null || data.length == 0) {
                // Fabric 把 include 进来的依赖塞在外层 jar 的 META-INF/jars/ 里，
                // 部分启动器的类加载器不把嵌套 jar 的资源暴露给 getResourceAsStream，这里直接读外层 jar 兜底。
                data = readFromNestedJar(arch);
            }
            if (data == null || data.length == 0) {
                log("jar 内没有 " + resource + "（需要 android 或 all 变体的构建产物）");
                return;
            }
            log("已从 " + resource + " 读到 " + data.length + " 字节");

            // 先试应用内部目录，外置存储（/storage、/sdcard）通常是 noexec 挂载，
            // 写进去 System.load 也会失败，所以放到第二轮兜底。
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < BASE_DIR_PROPERTIES.length; i++) {
                    String base = System.getProperty(BASE_DIR_PROPERTIES[i]);
                    if (base == null || base.isEmpty()) {
                        continue;
                    }
                    boolean external = isExternalStorage(base);
                    if (pass == 0 && external) {
                        continue;
                    }
                    if (pass == 1 && !external) {
                        continue;
                    }
                    File dir = new File(base, "starain-skija-" + arch);
                    if (writeNative(data, dir)) {
                        System.setProperty("skija.library.path", dir.getAbsolutePath());
                        log("已释放 Skija Android 原生库到 " + dir.getAbsolutePath());
                        return;
                    }
                    log("目录不可用，换下一个：" + dir.getAbsolutePath());
                }
            }
            log("没有任何可写目录能释放 Skija 原生库，可用 -Dstarain.native.dir=<目录> 手动指定");
        } catch (Throwable t) {
            log("释放 Skija 原生库失败：" + t);
        }
    }

    private static boolean isAndroid() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("linux")) {
            return false;
        }
        // 部分安卓 JVM（如 FCL/Pojav 的定制运行时）会直接把 os.version 写成 "Android-16"
        String osVersion = System.getProperty("os.version", "").toLowerCase(Locale.ROOT);
        if (osVersion.contains("android")) {
            return true;
        }
        for (int i = 0; i < ANDROID_MARKER_FILES.length; i++) {
            try {
                if (new File(ANDROID_MARKER_FILES[i]).exists()) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 某些系统上 stat 会被 SELinux 拒绝，忽略继续判断
            }
        }
        String vendor = System.getProperty("java.vendor", "") + " " + System.getProperty("java.vm.name", "")
                + " " + System.getProperty("java.vm.vendor", "");
        return vendor.toLowerCase(Locale.ROOT).contains("android");
    }

    /** 外置存储一般是 noexec 挂载，dlopen 会失败，只能作为最后手段。 */
    private static boolean isExternalStorage(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith("/storage") || lower.startsWith("/sdcard")
                || lower.startsWith("/mnt/sdcard") || lower.startsWith("/mnt/media_rw");
    }

    private static String currentArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.contains("aarch64") || arch.contains("arm64")) {
            return "arm64";
        }
        if (arch.contains("x86_64") || arch.contains("amd64")) {
            return "x64";
        }
        return null;
    }

    /**
     * 安卓原生库在 Fabric 的嵌套 jar 里，不同加载器对资源路径的解析方式不一样，
     * 这里依次试「绝对路径 / 类加载器相对路径 / 线程上下文类加载器」。
     */
    private static byte[] readResource(String path) {
        InputStream in = null;
        try {
            in = SkijaNatives.class.getResourceAsStream(path);
            if (in == null) {
                String relative = path.startsWith("/") ? path.substring(1) : path;
                ClassLoader own = SkijaNatives.class.getClassLoader();
                if (own != null) {
                    in = own.getResourceAsStream(relative);
                }
                if (in == null) {
                    ClassLoader context = Thread.currentThread().getContextClassLoader();
                    if (context != null && context != own) {
                        in = context.getResourceAsStream(relative);
                    }
                }
            }
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (Throwable t) {
            log("读取 " + path + " 失败：" + t);
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * 兜底：直接打开外层模组 jar，翻出 {@code META-INF/jars/skija-android-<arch>-*.jar}，
     * 再从里面读出 {@code io/github/humbleui/skija/android/<arch>/libskija.so}。
     * 这样不依赖类加载器对嵌套 jar 资源的支持。
     */
    /**
     * 把 code source 的 URL 还原成磁盘上的 jar 文件。
     * Fabric 对嵌套 jar 给出的可能是 {@code jar:file:/.../starain.jar!/META-INF/jars/x.jar!/}，
     * 这种情况下真正能打开的是最外层那个 jar。
     */
    private static java.io.File toFile(java.net.URL location) {
        if (location == null) {
            return null;
        }
        try {
            String text = location.toString();
            if (text.startsWith("jar:")) {
                text = text.substring(4);
            }
            int bang = text.indexOf("!/");
            if (bang >= 0) {
                text = text.substring(0, bang);
            }
            if (!text.startsWith("file:")) {
                return null;
            }
            return new java.io.File(new java.net.URI(text));
        } catch (Throwable t) {
            log("解析模组 jar 路径失败（" + location + "）：" + t);
            return null;
        }
    }

    private static byte[] readFromNestedJar(String arch) {
        java.io.File outer = null;
        try {
            java.security.CodeSource source = SkijaNatives.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                log("拿不到模组 jar 的位置，无法从嵌套 jar 读原生库");
                return null;
            }
            outer = toFile(source.getLocation());
            if (outer == null || !outer.isFile()) {
                log("模组 jar 不是本地文件（" + source.getLocation() + "），无法从嵌套 jar 读原生库");
                return null;
            }
            String wanted = "io/github/humbleui/skija/android/" + arch + "/libskija.so";
            java.util.jar.JarFile jar = new java.util.jar.JarFile(outer);
            try {
                java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    java.util.jar.JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.startsWith("META-INF/jars/") || !name.endsWith(".jar")) {
                        continue;
                    }
                    if (!name.contains("skija-android") || !name.contains(arch)) {
                        continue;
                    }
                    InputStream nested = jar.getInputStream(entry);
                    try {
                        java.util.jar.JarInputStream nestedJar = new java.util.jar.JarInputStream(nested);
                        try {
                            java.util.jar.JarEntry inner;
                            while ((inner = nestedJar.getNextJarEntry()) != null) {
                                if (wanted.equals(inner.getName())) {
                                    log("从 " + name + " 内读取原生库");
                                    return nestedJar.readAllBytes();
                                }
                            }
                        } finally {
                            nestedJar.close();
                        }
                    } finally {
                        nested.close();
                    }
                }
            } finally {
                jar.close();
            }
        } catch (Throwable t) {
            log("从嵌套 jar 读取原生库失败：" + t);
        }
        return null;
    }

    private static boolean writeNative(byte[] data, File dir) {
        FileOutputStream out = null;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            File target = new File(dir, "libskija.so");
            // 上次已经释放过且大小一致，直接复用，省掉每次启动 34MB 的读写
            if (target.isFile() && target.canRead() && target.length() == data.length) {
                return true;
            }
            out = new FileOutputStream(target);
            out.write(data);
            out.flush();
            out.close();
            out = null;
            try {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(target.toPath(), permissions);
            } catch (Throwable ignored) {
                // 非 POSIX 文件系统（或安卓不允许 chmod）时忽略，System.load 仍可能成功
            }
            // System.load 要求文件可读且非空，大小对得上才算写成功
            return target.isFile() && target.canRead() && target.length() == data.length;
        } catch (Throwable t) {
            return false;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void log(String message) {
        System.out.println("[Starain] " + message);
    }
}
