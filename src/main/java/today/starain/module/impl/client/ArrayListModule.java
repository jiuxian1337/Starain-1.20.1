package today.starain.module.impl.client;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
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
    private final NumberValue paddingX = new NumberValue("Padding X", 0, 20, 4, 0.5, this);
    private final NumberValue paddingY = new NumberValue("Padding Y", 0, 10, 1, 0.5, this);
    private final BooleanValue background = new BooleanValue("Background", false, this);
    private final ColorValue bgColor = new ColorValue("Background Color", new Color(0, 0, 0, 100), this, background::get);
    private final NumberValue bgRadius = new NumberValue("Background Radius", 0, 15, 6, 0.1, this, background::get);

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

    /**
     * 整个背景描成一条路径：右边贴屏幕边是直线，左边的阶梯每个拐点都用
     * tangentArcTo 过渡——凸角凹角都是弧，上下行之间就完全连贯了。
     * 每行单独画圆角矩形做不到这一点：凹角只会留豁口。
     */
    private void drawBackgroundOutline(float[] xs, float padX, float rowHeight, int count) {
        // 左侧轮廓的拐点序列（从上往下）。等宽相接（差几丝以内）不算台阶，直接一条直线穿过。
        List<float[]> corners = new ArrayList<>();
        corners.add(new float[]{xs[0] - padX, 0.0F});
        for (int i = 1; i < count; i++) {
            if (Math.abs(xs[i] - xs[i - 1]) > 0.01F) {
                float boundary = i * rowHeight;
                corners.add(new float[]{xs[i - 1] - padX, boundary});
                corners.add(new float[]{xs[i] - padX, boundary});
            }
        }
        float totalHeight = count * rowHeight;
        corners.add(new float[]{xs[count - 1] - padX, totalHeight});

        float r = bgRadius.get().floatValue();
        try (Path path = new Path(); Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            Color c = bgColor.get();
            paint.setARGB(c.getAlpha(), c.getRed(), c.getGreen(), c.getBlue());

            path.moveTo(padX, 0.0F);
            float prevX = padX;
            float prevY = 0.0F;
            for (int i = 0; i < corners.size(); i++) {
                float[] corner = corners.get(i);
                float[] next = i + 1 < corners.size() ? corners.get(i + 1) : new float[]{padX, totalHeight};
                // 半径不能超过两侧线段的一半，不然弧的切点越过线段端点，轮廓会扭。
                float in = dist(prevX, prevY, corner[0], corner[1]);
                float out = dist(corner[0], corner[1], next[0], next[1]);
                path.tangentArcTo(corner[0], corner[1], next[0], next[1], Math.min(r, Math.min(in, out) / 2.0F));
                prevX = corner[0];
                prevY = corner[1];
            }
            path.lineTo(padX, totalHeight);
            path.closePath();
            Skia.getCanvas().drawPath(path, paint);
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
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
        float padX = paddingX.get().floatValue();
        float padY = paddingY.get().floatValue();
        // 行高把垂直留白算进去，各行背景才是连续的一整条，文字也仍然居中。
        float rowHeight = font.getMetrics().getHeight() + padY * 2.0F;

        Skia.save();
        Skia.translate(mc.getWindow().getGuiScaledWidth() - offsetX.get().floatValue(), offsetY.get().floatValue());
        Skia.scale(s);

        // 先量好每行宽度和滑动后的 x：左侧轮廓是一条整体路径，画之前要知道全部行的位置。
        int count = ordered.size();
        float[] widths = new float[count];
        float[] xs = new float[count];
        for (int i = 0; i < count; i++) {
            widths[i] = rowWidth(ordered.get(i), font);
            xs[i] = -widths[i] + (1.0F - animations.get(ordered.get(i)).value) * SLIDE_DISTANCE;
        }

        if (background.get()) {
            drawBackgroundOutline(xs, padX, rowHeight, count);
        }

        float y = 0.0F;
        for (int i = 0; i < count; i++) {
            ClientModule module = ordered.get(i);
            float progress = animations.get(module).value;
            String name = module.getName();
            String suffix = suffixOf(module);
            float nameWidth = Skia.getTextWidth(name, font);

            // 原点在屏幕右边缘，所以整行往左画即右对齐；没播完的动画再往右滑出屏幕。
            float x = xs[i];
            // drawHeightCenteredText 的 y 是行的垂直中心，基线只由 FontMetrics 决定，
            // 这样名字和后缀（如 "ms" 这种没有大写和升部的）才会共用同一条基线。
            float centerY = y + rowHeight / 2.0F;

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

            y += rowHeight;
        }

        Skia.restore();
    }
}
