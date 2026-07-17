package today.starain.module.impl.client;

import io.github.humbleui.skija.Font;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.utils.animation.SmoothAnimationTimer;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArrayListModule extends ClientModule {
    private static final float FONT_SIZE = 18.0F;
    private static final float SLIDE_DISTANCE = 20.0F;
    private static final float SUFFIX_GAP = 5.0F;

    private final NumberValue scale = new NumberValue("Scale", 0.5, 2.0, 1.0, 0.1, this);
    private final NumberValue offsetX = new NumberValue("Offset X", 0, 50, 4, 1, this);
    private final NumberValue offsetY = new NumberValue("Offset Y", 0, 50, 4, 1, this);
    private final ModeValue fontWeight = new ModeValue("Font Weight", "Medium", new String[]{"Regular", "Medium"}, this);
    private final ColorValue textColor = new ColorValue("Text Color", Color.WHITE, this);
    private final ColorValue suffixColor = new ColorValue("Suffix Color", new Color(170, 170, 170), this);
    private final BooleanValue glow = new BooleanValue("Glow", true, this);
    private final NumberValue glowRadius = new NumberValue("Glow Radius", 0.5, 10, 3, 0.1, this, glow::get);
    private final NumberValue glowAlpha = new NumberValue("Glow Alpha", 0, 255, 180, 1, this, glow::get);
    private final BooleanValue background = new BooleanValue("Background", false, this);
    private final ColorValue bgColor = new ColorValue("Background Color", new Color(0, 0, 0, 100), this, background::get);

    private final Map<ClientModule, SmoothAnimationTimer> animations = new LinkedHashMap<>();

    public ArrayListModule() {
        setName("ArrayList");
        setCategory(Category.CLIENT);
    }

    // 如果直接去new一个对象这个zkm的indy没法混 直接是明文调用 所以要搞一个newInstance方法
    public static ArrayListModule newInstance() {
        return new ArrayListModule();
    }

    // 1.20.1 的 ClientModule.getSuffix() 基类返回 null，1.21.11 返回空串。
    private static String suffixOf(ClientModule module) {
        String suffix = module.getSuffix();
        return suffix == null ? "" : suffix;
    }

    private static Color fade(Color color, float progress) {
        return withAlpha(color, color.getAlpha() * progress);
    }

    private static Color withAlpha(Color color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private static float rowWidth(ClientModule module, Font font) {
        float width = Skia.getTextWidth(module.getName(), font);
        String suffix = suffixOf(module);
        return suffix.isEmpty() ? width : width + SUFFIX_GAP + Skia.getTextWidth(suffix, font);
    }

    @EventTarget
    public void onRenderSkia(RenderSkiaEvent event) {
        Font font = fontWeight.isMode("Medium") ? Fonts.getMedium(FONT_SIZE) : Fonts.getRegular(FONT_SIZE);

        Set<ClientModule> enabled = new HashSet<>();
        for (ClientModule module : Starain.getInstance().getModuleManager().getModules()) {
            if (module.isEnabled()) {
                enabled.add(module);
                animations.computeIfAbsent(module, key -> new SmoothAnimationTimer(0.0F, 0.0F, 0.45F)).target = 1.0F;
            }
        }

        // 关掉的模块留在表里播完淡出动画再移除。
        for (Map.Entry<ClientModule, SmoothAnimationTimer> entry : animations.entrySet()) {
            if (!enabled.contains(entry.getKey())) {
                entry.getValue().target = 0.0F;
            }
            entry.getValue().update(true);
        }
        animations.entrySet().removeIf(entry -> entry.getValue().target == 0.0F && entry.getValue().value <= 0.001F);

        if (animations.isEmpty()) {
            return;
        }

        List<ClientModule> ordered = new ArrayList<>(animations.keySet());
        ordered.sort(Comparator.comparingDouble(module -> -rowWidth(module, font)));

        float s = scale.get().floatValue() * 0.5F;
        float lineHeight = font.getMetrics().getHeight();

        Skia.save();
        Skia.translate(mc.getWindow().getGuiScaledWidth() - offsetX.get().floatValue(), offsetY.get().floatValue());
        Skia.scale(s);

        float y = 0.0F;
        for (ClientModule module : ordered) {
            float progress = animations.get(module).value;
            String name = module.getName();
            String suffix = suffixOf(module);
            float nameWidth = Skia.getTextWidth(name, font);
            float width = rowWidth(module, font);

            // 原点在屏幕右边缘，所以整行往左画即右对齐；没播完的动画再往右滑出屏幕。
            float x = -width + (1.0F - progress) * SLIDE_DISTANCE;
            // drawHeightCenteredText 的 y 是行的垂直中心，基线只由 FontMetrics 决定，
            // 这样名字和后缀（如 "ms" 这种没有大写和升部的）才会共用同一条基线。
            float centerY = y + lineHeight / 2.0F;

            if (background.get()) {
                Skia.drawRoundedRect(x - 2.0F, y, width + 4.0F, lineHeight, 0.0F, fade(bgColor.get(), progress));
            }

            float suffixX = x + nameWidth + SUFFIX_GAP;

            // 发光取各自的文字颜色，只换透明度，这样改 Text Color 光晕会跟着变。
            if (glow.get()) {
                float radius = glowRadius.get().floatValue();
                float alpha = glowAlpha.get().floatValue() * progress;
                Skia.drawHeightCenteredTextGlow(name, x, centerY, withAlpha(textColor.get(), alpha), font, radius);
                if (!suffix.isEmpty()) {
                    Skia.drawHeightCenteredTextGlow(suffix, suffixX, centerY, withAlpha(suffixColor.get(), alpha), font, radius);
                }
            }

            // 正文只能用 Plain 版本：drawHeightCenteredText 自带一层同色模糊，会和上面的发光叠成一团。
            Skia.drawHeightCenteredTextPlain(name, x, centerY, fade(textColor.get(), progress), font);
            if (!suffix.isEmpty()) {
                Skia.drawHeightCenteredTextPlain(suffix, suffixX, centerY, fade(suffixColor.get(), progress), font);
            }

            y += lineHeight;
        }

        Skia.restore();
    }
}
