package today.starain.utils.render.skia.font;

import io.github.humbleui.skija.Data;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.Typeface;
import today.starain.utils.render.skia.utils.SkiaUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class FontHelper {

    private static final Map<String, Typeface> typefaceCache = new HashMap<>();
    private static final Map<String, Font> fontCache = new HashMap<>();

    private static Typeface getTypeface(String font, FontType type) {
        return typefaceCache.computeIfAbsent(font, k -> loadTypeface(k, type));
    }

    private static Typeface loadTypeface(String font, FontType type) {
        Optional<Data> fontDataOptional = SkiaUtils.convertToData("/assets/starain/fonts/" + font);
        // Skija 0.143 起 Typeface 的静态工厂移到 FontMgr 上。
        return fontDataOptional.map(data -> FontMgr.getDefault().makeFromData(data))
                .orElseThrow(() -> new IllegalArgumentException("Font not found: " + font));
    }

    public static Font load(String font, float size, FontType fontType) {
        // 同一 (字体, 字号) 的 Font 是只读的，可安全跨帧复用。Font 是 skija 的 native 句柄，
        // 每帧每实体重复 new 会带来 native 构造开销与 GC 压力（见 NameTags 热路径）。
        String key = font + '\u0000' + size;
        Font cached = fontCache.get(key);
        if (cached != null) {
            return cached;
        }
        Typeface typeface = getTypeface(font, fontType);
        Font load = new Font(typeface, size);
        load.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
        load.setSubpixel(true);
        fontCache.put(key, load);
        return load;
    }

    public static Font load(String font, float size) {
        return load(font, size, getFontType(font));
    }

    private static FontType getFontType(String font) {
        String fileExtension = font.substring(font.lastIndexOf('.') + 1).toLowerCase();
        return FontType.fromString(fileExtension);
    }

    public static void clearCache() {
        typefaceCache.clear();
        fontCache.clear();
    }

    public static void preloadFonts(String... fonts) {
        for (String font : fonts) {
            getTypeface(font, getFontType(font));
        }
    }
}