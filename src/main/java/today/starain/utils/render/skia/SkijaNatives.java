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
 * 让 {@code io.github.humbleui.skija.impl.Library} 走 {@code _loadFromDir} 分支。
 * 桌面端直接跳过，行为与之前完全一致。
 */
public final class SkijaNatives {

    /** 释放原生库时可选的根目录，按顺序尝试第一个可写的。 */
    private static final String[] BASE_DIR_PROPERTIES = {"starain.native.dir", "java.io.tmpdir", "user.dir"};

    private static boolean prepared;

    private SkijaNatives() {
    }

    /**
     * 幂等；必须在任何 Skija 类被加载之前调用（目前由 {@code Bootstrap#launch} 打头调用）。
     * 不能使用 lambda/方法引用：这个类在 mixin 直接触达的初始化链路上。
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;

        try {
            if (System.getProperty("skija.library.path") != null) {
                return;
            }
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
                log("jar 内没有 " + resource + "，请用 android/all 变体打包");
                return;
            }

            for (int i = 0; i < BASE_DIR_PROPERTIES.length; i++) {
                String base = System.getProperty(BASE_DIR_PROPERTIES[i]);
                if (base == null || base.isEmpty()) {
                    continue;
                }
                File dir = new File(base, "starain-skija-" + arch);
                if (writeNative(data, dir)) {
                    System.setProperty("skija.library.path", dir.getAbsolutePath());
                    log("已释放 Skija Android 原生库到 " + dir.getAbsolutePath());
                    return;
                }
            }
            log("没有任何可写目录能释放 Skija 原生库，可用 -Dstarain.native.dir=<目录> 手动指定");
        } catch (Throwable t) {
            log("释放 Skija 原生库失败: " + t);
        }
    }

    private static boolean isAndroid() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("linux")) {
            return false;
        }
        // 安卓系统一定有 /system/build.prop；部分定制 JVM 会直接标明 vendor
        if (new File("/system/build.prop").exists()) {
            return true;
        }
        String vendor = System.getProperty("java.vendor", "") + " " + System.getProperty("java.vm.name", "");
        return vendor.toLowerCase(Locale.ROOT).contains("android");
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

    private static byte[] readResource(String path) {
        InputStream in = null;
        try {
            in = SkijaNatives.class.getResourceAsStream(path);
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        } catch (IOException e) {
            log("读取 " + path + " 失败: " + e);
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

    private static boolean writeNative(byte[] data, File dir) {
        FileOutputStream out = null;
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return false;
            }
            File target = new File(dir, "libskija.so");
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
            return target.length() == data.length;
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
