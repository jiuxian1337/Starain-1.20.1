package today.starain.module.impl.client;

import io.github.humbleui.skija.Font;
import net.minecraft.sounds.SoundEvents;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Design;
import today.starain.ui.Theme;
import today.starain.utils.client.SoundUtils;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.utils.render.skia.font.Icon;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import today.starain.ui.HudDrag;

public class Notification extends ClientModule {

    private static final float HEIGHT = 28.0f, GAP = 4.0f, RADIUS = 9.0f, MARGIN = 10.0f;

    private final ModeValue sound = new ModeValue("Toggle Sound", "Vanilla", new String[]{"None", "Vanilla", "JelloBounce"}, this);
    private final BooleanValue visual = new BooleanValue("Visual", true, this);
    private final ModeValue style = new ModeValue("Style", "Material", new String[]{"Material", "Island", "Card", "Toast", "Minimal"}, this, visual::get);
    private final ModeValue position = new ModeValue("Position", "Bottom Right", new String[]{"Bottom Right", "Top Right", "Top Left", "Bottom Left"}, this, visual::get);
    private final NumberValue duration = new NumberValue("Duration", 0.5, 6.0, 2.0, 0.1, this, visual::get);
    private final NumberValue maxStack = new NumberValue("Max Stack", 1, 10, 5, 1, this, visual::get);
    private final BooleanValue subtitle = new BooleanValue("Subtitle", true, this, visual::get);
    private final BooleanValue progress = new BooleanValue("Progress Bar", true, this, visual::get);
    private final NumberValue scale = new NumberValue("Scale", 0.5, 2.0, 1.0, 0.05, this, visual::get);
    // 相对所选角落的偏移，聊天界面中可拖拽调整
    private final NumberValue offsetX = new NumberValue("X", 0, 1000, MARGIN, 1, this, () -> false);
    private final NumberValue offsetY = new NumberValue("Y", 0, 1000, MARGIN, 1, this, () -> false);

    private final List<Entry> entries = new ArrayList<>();
    private long lastFrame;

    private static final class Entry {
        final String title, message;
        final boolean positive;
        final float hold;
        float remaining;
        float show;

        Entry(String title, String message, boolean positive, float hold) {
            this.title = title;
            this.message = message;
            this.positive = positive;
            this.hold = this.remaining = hold;
        }
    }

    public Notification() {
        setName("Notification");
        setCategory(Category.CLIENT);
    }

    public void toggleModule(boolean enabled, ClientModule module) {
        if (mc.player == null) return;
        Notification notification = Starain.getInstance().getModuleManager().getModule(Notification.class);
        if (!notification.isEnabled()) return;
        switch (notification.sound.get()) {
            case "Vanilla" -> {
                if (enabled) {
                    mc.player.playSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1F, 1.3F);
                } else {
                    mc.player.playSound(SoundEvents.WOODEN_BUTTON_CLICK_OFF, 1F, 0.8F);
                }
            }
            case "JelloBounce" -> {
                if (enabled) {
                    SoundUtils.playSound("enable.wav", 1f);
                } else {
                    SoundUtils.playSound("disable.wav", 1f);
                }
            }
        }
        if (notification.visual.get() && module != notification) {
            notification.post(module.getName(), enabled ? "Enabled" : "Disabled", enabled);
        }
    }

    public void post(String title, String message, boolean positive) {
        entries.add(new Entry(title, message, positive, duration.get().floatValue() * 1000.0f));
        while (entries.size() > Math.max(1, maxStack.get().intValue())) entries.remove(0);
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0f : Math.min((now - lastFrame) / 1_000_000.0f, 100.0f);
        lastFrame = now;
        if (!visual.get()) return;

        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            e.remaining -= delta;
            boolean expired = e.remaining <= 0.0f;
            // 进入 300ms，退出 200ms 的指数缓动
            float target = expired ? 0.0f : 1.0f;
            float speed = expired ? 200.0f : 300.0f;
            e.show += (target - e.show) * Math.min(1.0f, delta / speed * 4.0f);
            if (expired && e.show < 0.01f) it.remove();
        }
        List<Entry> list = entries;
        if (list.isEmpty()) {
            if (!HudDrag.isEditing()) return;
            Entry sample = new Entry("Notification", "Drag to move", true, 1.0f);
            sample.show = 1.0f;
            list = List.of(sample);
        }

        Font titleFont = Fonts.getBold(8.5f);
        Font subFont = Fonts.getMedium(6.5f);
        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();
        boolean right = position.get().endsWith("Right");
        boolean bottom = position.get().startsWith("Bottom");

        float mx = offsetX.get().floatValue(), my = offsetY.get().floatValue();
        Skia.beginHudScale(right ? sw - mx : mx, bottom ? sh - my : my, scale.get().floatValue());
        float cursor = bottom ? sh - my - heightFor() : my;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = list.size() - 1; i >= 0; i--) {
            Entry e = list.get(i);
            float width = widthFor(e, titleFont, subFont);
            float h = heightFor();

            float slide = (1.0f - e.show) * (width + mx);
            float x = right ? sw - mx - width + slide : mx - slide;
            float y = cursor;
            switch (style.get()) {
                case "Island" -> drawIsland(e, x, y, width, h, titleFont, subFont);
                case "Card" -> drawCard(e, x, y, width, h, titleFont, subFont);
                case "Toast" -> drawToast(e, x, y, width, h, titleFont, subFont);
                case "Minimal" -> drawMinimal(e, x, y, width, h, subFont, right);
                default -> draw(e, x, y, width, titleFont, subFont);
            }
            minX = Math.min(minX, x); maxX = Math.max(maxX, x + width);
            minY = Math.min(minY, y); maxY = Math.max(maxY, y + h);
            float step = (h + GAP) * e.show;
            cursor += bottom ? -step : step;
        }
        HudDrag.report(this, offsetX, offsetY, minX, minY, maxX - minX, maxY - minY, right, bottom);
        Skia.endHudScale();
    }

    private void draw(Entry e, float x, float y, float width, Font titleFont, Font subFont) {
        float a = Math.max(0.0f, Math.min(1.0f, e.show));
        Color accent = e.positive ? Theme.accent(0) : new Color(255, 90, 90);
        if (a > 0.3f) {
            Skia.drawShadow(x, y, width, HEIGHT, RADIUS);
            Skia.drawBlur(x, y, width, HEIGHT, RADIUS);
        }
        Skia.setAlpha((int) (a * 255));
        Skia.drawRoundedRect(x, y, width, HEIGHT, RADIUS, Design.BACKGROUND_COLOR);

        Font icon = Fonts.getIconFill(12.0f);
        String glyph = e.positive ? Icon.CHECK_CIRCLE : Icon.CANCEL;
        float iw = Skia.getTextWidth(glyph, icon);
        Skia.drawText(glyph, x + 14.0f - iw / 2.0f, y + 8.0f, accent, icon);

        String title = truncate(e.title, titleFont, width - 38.0f);
        boolean sub = subtitle.get();
        Skia.drawText(title, x + 26.0f, y + (sub ? 6.0f : 10.0f), Design.TEXT_COLOR, titleFont);
        if (sub) {
            Skia.drawText(truncate(e.message, subFont, width - 38.0f), x + 26.0f, y + 15.5f,
                    new Color(200, 200, 205), subFont);
        }
        if (progress.get() && e.hold > 0.0f) {
            float p = Math.max(0.0f, Math.min(1.0f, e.remaining / e.hold));
            Skia.drawRoundedRect(x + 10.0f, y + 25.0f, (width - 20.0f) * p, 1.5f, 0.75f, withAlpha(accent, 217));
        }
        Skia.restore();
    }

    private float heightFor() {
        return switch (style.get()) {
            case "Island" -> 20.0f;
            case "Card" -> 34.0f;
            case "Toast" -> 26.0f;
            case "Minimal" -> 14.0f;
            default -> HEIGHT;
        };
    }

    private float widthFor(Entry e, Font titleFont, Font subFont) {
        switch (style.get()) {
            case "Island" -> {
                float w = 34.0f + Skia.getTextWidth(e.title, titleFont) + (subtitle.get() ? 6.0f + Skia.getTextWidth(e.message, subFont) : 0.0f);
                return Math.min(260.0f, Math.max(90.0f, w));
            }
            case "Card" -> {
                float text = Math.max(Skia.getTextWidth(e.title, titleFont), subtitle.get() ? Skia.getTextWidth(e.message, subFont) : 0.0f);
                return Math.min(260.0f, Math.max(130.0f, 50.0f + text));
            }
            case "Minimal" -> {
                return Math.min(260.0f, 18.0f + Skia.getTextWidth(e.title + "  " + e.message, subFont));
            }
            default -> {
                float width = 44.0f + Skia.getTextWidth("ChestStealer", titleFont);
                float text = Skia.getTextWidth(e.title, titleFont);
                if (subtitle.get()) text = Math.max(text, Skia.getTextWidth(e.message, subFont));
                return Math.min(260.0f, Math.max(width, 44.0f + text));
            }
        }
    }

    private float progressOf(Entry e) {
        return e.hold <= 0.0f ? 0.0f : Math.max(0.0f, Math.min(1.0f, e.remaining / e.hold));
    }

    // Island：灵动岛式黑色胶囊，左侧状态圆点
    private void drawIsland(Entry e, float x, float y, float w, float h, Font titleFont, Font subFont) {
        float a = Math.max(0.0f, Math.min(1.0f, e.show));
        Color accent = e.positive ? Theme.accent(0) : new Color(255, 90, 90);
        float r = h / 2.0f;
        if (a > 0.3f) {
            Skia.drawShadow(x, y, w, h, r);
            Skia.drawBlur(x, y, w, h, r);
        }
        Skia.setAlpha((int) (a * 255));
        Skia.drawRoundedRect(x, y, w, h, r, new Color(8, 8, 10, 215));
        Skia.drawCircle(x + r, y + r, 5.0f, withAlpha(accent, 60));
        Skia.drawCircle(x + r, y + r, 2.6f, accent);
        Skia.drawText(e.title, x + h + 4.0f, y + 6.0f, Design.TEXT_COLOR, titleFont);
        if (subtitle.get()) {
            Skia.drawText(e.message, x + h + 10.0f + Skia.getTextWidth(e.title, titleFont), y + 7.2f, withAlpha(accent, 230), subFont);
        }
        Skia.restore();
    }

    // Card：方形图标块 + 两行文字，图标块颜色随状态变化
    private void drawCard(Entry e, float x, float y, float w, float h, Font titleFont, Font subFont) {
        float a = Math.max(0.0f, Math.min(1.0f, e.show));
        Color accent = e.positive ? Theme.accent(0) : new Color(255, 90, 90);
        if (a > 0.3f) {
            Skia.drawShadow(x, y, w, h, 7.0f);
            Skia.drawBlur(x, y, w, h, 7.0f);
        }
        Skia.setAlpha((int) (a * 255));
        Skia.drawRoundedRect(x, y, w, h, 7.0f, Design.BACKGROUND_COLOR);
        float tile = h - 10.0f;
        Skia.drawRoundedRect(x + 5.0f, y + 5.0f, tile, tile, 5.0f, withAlpha(accent, 55));
        Font icon = Fonts.getIconFill(13.0f);
        String glyph = e.positive ? Icon.CHECK_CIRCLE : Icon.CANCEL;
        Skia.drawText(glyph, x + 5.0f + tile / 2.0f - Skia.getTextWidth(glyph, icon) / 2.0f, y + 5.0f + tile / 2.0f - 6.5f, accent, icon);
        float tx = x + tile + 11.0f;
        Skia.drawText(truncate(e.title, titleFont, w - tile - 18.0f), tx, y + (subtitle.get() ? 8.0f : 12.5f), Design.TEXT_COLOR, titleFont);
        if (subtitle.get()) {
            Skia.drawText(truncate(e.message, subFont, w - tile - 18.0f), tx, y + 19.0f, new Color(190, 190, 198), subFont);
        }
        Skia.restore();
    }

    // Toast：小圆角矩形 + 左侧满高色条，进度表现为色条从上往下收缩
    private void drawToast(Entry e, float x, float y, float w, float h, Font titleFont, Font subFont) {
        float a = Math.max(0.0f, Math.min(1.0f, e.show));
        Color accent = e.positive ? Theme.accent(0) : new Color(255, 90, 90);
        if (a > 0.3f) {
            Skia.drawShadow(x, y, w, h, 4.0f);
            Skia.drawBlur(x, y, w, h, 4.0f);
        }
        Skia.setAlpha((int) (a * 255));
        Skia.drawRoundedRect(x, y, w, h, 4.0f, Design.BACKGROUND_COLOR);
        float barH = progress.get() ? (h - 8.0f) * progressOf(e) : h - 8.0f;
        Skia.drawRoundedRect(x + 4.0f, y + 4.0f + (h - 8.0f - barH), 2.0f, barH, 1.0f, accent);
        Skia.drawText(truncate(e.title, titleFont, w - 20.0f), x + 12.0f, y + (subtitle.get() ? 4.5f : 8.5f), Design.TEXT_COLOR, titleFont);
        if (subtitle.get()) {
            Skia.drawText(truncate(e.message, subFont, w - 20.0f), x + 12.0f, y + 14.5f, withAlpha(accent, 220), subFont);
        }
        Skia.restore();
    }

    // Minimal：无背景，仅一行文字 + 状态点，文字带阴影保证可读
    private void drawMinimal(Entry e, float x, float y, float w, float h, Font font, boolean right) {
        float a = Math.max(0.0f, Math.min(1.0f, e.show));
        Color accent = e.positive ? Theme.accent(0) : new Color(255, 90, 90);
        Skia.setAlpha((int) (a * 255));
        float dotX = right ? x + w - 5.0f : x + 5.0f;
        Skia.drawCircle(dotX, y + h / 2.0f, 2.0f, accent);
        float tx = right ? x + 2.0f : x + 12.0f;
        Skia.drawText(e.title, tx, y + 3.5f, Design.TEXT_COLOR, font);
        Skia.drawText("  " + e.message, tx + Skia.getTextWidth(e.title, font), y + 3.5f, accent, font);
        Skia.restore();
    }

    private static String truncate(String s, Font font, float max) {
        if (Skia.getTextWidth(s, font) <= max) return s;
        String r = s;
        while (r.length() > 1 && Skia.getTextWidth(r + "...", font) > max) r = r.substring(0, r.length() - 1);
        return r + "...";
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}





