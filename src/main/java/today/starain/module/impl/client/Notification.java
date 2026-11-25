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

public class Notification extends ClientModule {

    private static final float HEIGHT = 28.0f, GAP = 4.0f, RADIUS = 9.0f, MARGIN = 10.0f;

    private final ModeValue sound = new ModeValue("Toggle Sound", "Vanilla", new String[]{"None", "Vanilla", "JelloBounce"}, this);
    private final BooleanValue visual = new BooleanValue("Visual", true, this);
    private final ModeValue position = new ModeValue("Position", "Bottom Right", new String[]{"Bottom Right", "Top Right", "Top Left", "Bottom Left"}, this, visual::get);
    private final NumberValue duration = new NumberValue("Duration", 0.5, 6.0, 2.0, 0.1, this, visual::get);
    private final NumberValue maxStack = new NumberValue("Max Stack", 1, 10, 5, 1, this, visual::get);
    private final BooleanValue subtitle = new BooleanValue("Subtitle", true, this, visual::get);
    private final BooleanValue progress = new BooleanValue("Progress Bar", true, this, visual::get);
    private final NumberValue scale = new NumberValue("Scale", 0.5, 2.0, 1.0, 0.05, this, visual::get);

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
        if (!visual.get() || entries.isEmpty()) return;

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
        if (entries.isEmpty()) return;

        Font titleFont = Fonts.getBold(8.5f);
        Font subFont = Fonts.getMedium(6.5f);
        float sw = mc.getWindow().getGuiScaledWidth();
        float sh = mc.getWindow().getGuiScaledHeight();
        boolean right = position.get().endsWith("Right");
        boolean bottom = position.get().startsWith("Bottom");

        Skia.beginHudScale(right ? sw - MARGIN : MARGIN, bottom ? sh - MARGIN : MARGIN, scale.get().floatValue());
        float cursor = bottom ? sh - MARGIN - HEIGHT : MARGIN;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            float width = 44.0f + Skia.getTextWidth("ChestStealer", titleFont);
            float text = Skia.getTextWidth(e.title, titleFont);
            if (subtitle.get()) text = Math.max(text, Skia.getTextWidth(e.message, subFont));
            width = Math.min(260.0f, Math.max(width, 44.0f + text));

            float slide = (1.0f - e.show) * (width + MARGIN);
            float x = right ? sw - MARGIN - width + slide : MARGIN - slide;
            float y = cursor;
            draw(e, x, y, width, titleFont, subFont);
            float step = (HEIGHT + GAP) * e.show;
            cursor += bottom ? -step : step;
        }
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





