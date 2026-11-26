package today.starain.module.impl.player;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Shader;
import io.github.humbleui.types.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import today.starain.ui.Design;
import today.starain.ui.Theme;
import today.starain.utils.render.ProjectionUtils;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.context.SkiaContext;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.utils.vector.Vector2f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import io.github.humbleui.skija.FilterTileMode;
import io.github.humbleui.skija.GradientStyle;
import io.github.humbleui.skija.Matrix33;
import io.github.humbleui.types.Rect;

/**
 * ChestStealer 静默模式的箱子界面：毛玻璃面板 + 果冻形变流光边框，拿取时格子上掠过一道流光。
 */
final class SilentChestRenderer {
    private static final float CELL = 18.0f;
    private static final float PAD = 6.0f;
    private static final float HEADER = 13.0f;
    private static final float RADIUS = 6.0f;
    private static final long TAKE_FX_MS = 450L;

    private final Map<Integer, Long> takenAt = new HashMap<>();
    private long openedAt = System.currentTimeMillis();
    private long lastTakeAt;
    private float smoothX = Float.NaN, smoothY = Float.NaN;

    void reset() {
        takenAt.clear();
        openedAt = System.currentTimeMillis();
        lastTakeAt = 0L;
        smoothX = Float.NaN;
        smoothY = Float.NaN;
    }

    void markTaken(int slot) {
        long now = System.currentTimeMillis();
        takenAt.put(slot, now);
        lastTakeAt = now;
    }

    void render(ContainerScreen screen, GuiGraphics guiGraphics, float partialTick, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        ChestMenu menu = screen.getMenu();
        int rows = menu.getRowCount();
        float w = PAD * 2 + CELL * 9;
        float h = HEADER + PAD + CELL * rows;
        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();

        // 锚定到箱子上方，投影失败时退回屏幕中央
        float ax = sw / 2.0f, ay = sh / 2.0f - h / 2.0f - 10.0f;
        if (pos != null) {
            Vector2f p = ProjectionUtils.project(pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5, partialTick);
            if (p.x != Float.MAX_VALUE) {
                ax = p.x;
                ay = p.y - h - 6.0f;
            }
        }
        if (Float.isNaN(smoothX)) {
            smoothX = ax;
            smoothY = ay;
        } else {
            smoothX += (ax - smoothX) * 0.25f;
            smoothY += (ay - smoothY) * 0.25f;
        }
        float x = clamp(smoothX - w / 2.0f, 4.0f, sw - w - 4.0f);
        float y = clamp(smoothY, 4.0f, sh - h - 4.0f);

        long now = System.currentTimeMillis();
        float open = Math.min(1.0f, (now - openedAt) / 320.0f);
        float scale = easeOutBack(open);
        float alpha = Math.min(1.0f, open * 1.6f);
        float takePulse = lastTakeAt == 0L ? 0.0f : Math.max(0.0f, 1.0f - (now - lastTakeAt) / 380.0f);
        float time = now / 1000.0f;
        float cx = x + w / 2.0f, cy = y + h / 2.0f;
        float gx = x + PAD, gy = y + HEADER;

        int filled = 0;
        for (int i = 0; i < rows * 9; i++) {
            if (!menu.getSlot(i).getItem().isEmpty()) filled++;
        }
        final int itemCount = filled;
        // 先把原版已排队的 HUD 绘制刷出去，保证面板盖在其上、物品再盖在面板上
        guiGraphics.flush();
        final float fx = x, fy = y;

        SkiaContext.draw(canvas -> {
            Skia.save();
            Skia.scale((float) mc.getWindow().getGuiScale());
            Skia.beginHudScale(cx, cy, scale);
            try {
                if (alpha < 1.0f) Skia.setAlpha((int) (alpha * 255));
                Skia.drawShadow(fx, fy, w, h, RADIUS);
                Skia.drawBlur(fx, fy, w, h, RADIUS);
                Skia.drawRoundedRect(fx, fy, w, h, RADIUS, Design.BACKGROUND_COLOR);
                // 玻璃顶部高光
                Skia.drawRoundedRect(fx + 1, fy + 1, w - 2, HEADER - 2, RADIUS - 1, new Color(255, 255, 255, 14));

                Skia.drawText("Chest", fx + PAD + 1, fy + 3.5f, Color.WHITE, Fonts.getMedium(8));
                String info = itemCount + "/" + (rows * 9);
                Skia.drawText(info, fx + w - PAD - 1 - Skia.getTextWidth(info, Fonts.getNormal(7)), fy + 4.0f,
                        new Color(255, 255, 255, 150), Fonts.getNormal(7));

                for (int i = 0; i < rows * 9; i++) {
                    float sx = gx + (i % 9) * CELL + 1.0f;
                    float sy = gy + (i / 9) * CELL + 1.0f;
                    boolean has = !menu.getSlot(i).getItem().isEmpty();
                    Skia.drawRoundedRect(sx, sy, CELL - 2, CELL - 2, 3.0f, new Color(255, 255, 255, has ? 26 : 12));
                    Long t = takenAt.get(i);
                    if (t != null) {
                        float k = (now - t) / (float) TAKE_FX_MS;
                        if (k >= 1.0f) {
                            takenAt.remove(i);
                        } else {
                            drawTakeSweep(sx, sy, CELL - 2, k);
                        }
                    }
                }

        
                if (alpha < 1.0f) Skia.restore();
            } finally {
                Skia.endHudScale();
                Skia.restore();
            }
        });

        // 物品用原版管线画在 Skia 面板之上，保持与动画同一缩放
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx, cy, 0);
        guiGraphics.pose().scale(scale, scale, 1);
        guiGraphics.pose().translate(-cx, -cy, 0);
        for (int i = 0; i < rows * 9; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            int ix = (int) (gx + (i % 9) * CELL + 1);
            int iy = (int) (gy + (i / 9) * CELL + 1);
            guiGraphics.renderItem(stack, ix, iy);
            guiGraphics.renderItemDecorations(mc.font, stack, ix, iy);
        }
        guiGraphics.pose().popPose();
    }

    // 拿取瞬间：一道主题色斜向流光扫过格子并淡出
    private static void drawTakeSweep(float x, float y, float size, float k) {
        Color c = Theme.accent(0);
        int a = (int) (200 * (1.0f - k));
        float bandX = x - size + k * size * 3.0f;
        int on = io.github.humbleui.skija.Color.makeARGB(a, c.getRed(), c.getGreen(), c.getBlue());
        int off = io.github.humbleui.skija.Color.makeARGB(0, c.getRed(), c.getGreen(), c.getBlue());
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(Shader.makeLinearGradient(new Point(bandX - size * 0.6f, y), new Point(bandX + size * 0.6f, y + size),
                new int[]{off, on, off}, new float[]{0.0f, 0.5f, 1.0f}));
        Skia.save();
        Skia.clip(x, y, size, size, 3.0f);
        Skia.getCanvas().drawRect(Rect.makeXYWH(x, y, size, size), paint);
        Skia.restore();
        Skia.drawRoundedRect(x, y, size, size, 3.0f, new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (40 * (1.0f - k))));
    }

    // 超椭圆近似圆角矩形，沿外法线叠加正弦波形成果冻抖动，描边用旋转的主题色扫描渐变
    private static void drawJellyBorder(float x, float y, float w, float h, float time, float amp) {
        float cx = x + w / 2.0f, cy = y + h / 2.0f;
        float a = w / 2.0f, b = h / 2.0f;
        int samples = 160;
        double e = 2.0 / 10.0;
        PathBuilder pathBuilder = new PathBuilder();
        for (int i = 0; i <= samples; i++) {
            double t = Math.PI * 2 * i / samples;
            double ct = Math.cos(t), st = Math.sin(t);
            double px = a * Math.signum(ct) * Math.pow(Math.abs(ct), e);
            double py = b * Math.signum(st) * Math.pow(Math.abs(st), e);
            double wob = amp * (Math.sin(t * 6 + time * 3.1) * 0.6 + Math.sin(t * 11 - time * 4.7) * 0.4);
            double len = Math.max(1e-3, Math.hypot(px, py));
            float vx = (float) (cx + px + px / len * wob);
            float vy = (float) (cy + py + py / len * wob);
            if (i == 0) pathBuilder.moveTo(vx, vy);
            else pathBuilder.lineTo(vx, vy);
        }
        pathBuilder.closePath();
        Path path = pathBuilder.build();
        pathBuilder.close();

        int stops = 6;
        int[] colors = new int[stops + 1];
        for (int i = 0; i <= stops; i++) {
            Color c = Theme.accent(-(i % stops));
            int alpha = i % 2 == 0 ? 230 : 90;
            colors[i] = io.github.humbleui.skija.Color.makeARGB(alpha, c.getRed(), c.getGreen(), c.getBlue());
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStroke(true);
        paint.setStrokeWidth(1.2f);
        paint.setStrokeJoin(PaintStrokeJoin.ROUND);
        float[] positions = new float[stops + 1];
        for (int i = 0; i <= stops; i++) positions[i] = i / (float) stops;
        GradientStyle style = new GradientStyle(
                FilterTileMode.CLAMP, true,
                Matrix33.makeRotate((time * 90.0f) % 360.0f, cx, cy));
        paint.setShader(Shader.makeSweepGradient(cx, cy, colors, positions, style));
        Skia.getCanvas().drawPath(path, paint);
        path.close();
        paint.close();
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }

    private static float clamp(float v, float min, float max) {
        return max < min ? min : Math.max(min, Math.min(max, v));
    }
}
