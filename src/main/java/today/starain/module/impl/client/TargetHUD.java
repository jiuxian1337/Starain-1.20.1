package today.starain.module.impl.client;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import today.starain.Starain;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.module.impl.combat.KillAura;
import today.starain.ui.Design;
import today.starain.ui.clickgui.opal.OpalClickGUI;
import today.starain.utils.animation.SmoothAnimationTimer;
import today.starain.utils.render.ProjectionUtils;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.vector.Vector2f;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

import today.starain.ui.Theme;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import today.starain.module.impl.render.Glow;
import today.starain.ui.HudDrag;
import today.starain.utils.render.skia.font.Icon;

public class TargetHUD extends ClientModule {
    private final ModeValue style = new ModeValue("Style", "Default", new String[]{"Default", "Material"}, this);
    private final ModeValue healthBar = new ModeValue("Health Bar", "Solid", new String[]{"Solid", "Wave", "Segment"}, this, () -> style.isMode("Material"));
    private final NumberValue scale = new NumberValue("Scale", 0.5, 2.0, 1.0, 0.05, this);
    private final NumberValue x = new NumberValue("X", 0, 1000, 300, 1, this, () -> false);
    private final NumberValue y = new NumberValue("y", 0, 1000, 300, 1, this, () -> false);
    private final BooleanValue syncColor = new BooleanValue("Sync Color", false, this);
    private final BooleanValue glow = new BooleanValue("Glow", false, this);
    private final BooleanValue followPlayer = new BooleanValue("Follow Player", false, this);
    private final BooleanValue multiTarget = new BooleanValue("Multi Target", false, this);
    private final BooleanValue hitParticles = new BooleanValue("Hit Particles", false, this);
    private final NumberValue particleCount = new NumberValue("Particle Count", 4, 48, 20, 1, this, hitParticles::get);
    private final ModeValue side = new ModeValue("Side", "Right", new String[]{"Right", "Left", "Auto"}, this, followPlayer::get);
    private final NumberValue sideOffset = new NumberValue("Side Offset", 0, 80, 16, 1, this, followPlayer::get);
    private final NumberValue heightRatio = new NumberValue("Height", 0.0, 1.5, 0.8, 0.05, this, followPlayer::get);
    private final Map<Integer, SmoothAnimationTimer> healTimers = new HashMap<>();
    private final Map<Integer, SmoothAnimationTimer> hurtTimers = new HashMap<>();
    private final Map<Integer, SmoothAnimationTimer> fadeTimers = new java.util.LinkedHashMap<>();
    private final Map<Integer, SmoothAnimationTimer> slotTimers = new HashMap<>();
    private final SmoothAnimationTimer heal = new SmoothAnimationTimer(20.0F);
    private final SmoothAnimationTimer alpha = new SmoothAnimationTimer(255.0F);
    private final SmoothAnimationTimer hurtAlpha = new SmoothAnimationTimer(0.0F);
    private final Map<Integer, HitEffect> hitEffects = new HashMap<>();
    private final Map<Integer, RollText> healthRolls = new HashMap<>();
    private long lastFrameNano;
    private float frameDelta;
    private float previewTimer;
    private AbstractClientPlayer target;

    private static final class Particle {
        float offsetX;
        float offsetY;
        float velocityX;
        float velocityY;
        float life;
        float maxLife;
        float size;
        float phase;
    }

    private static final class RollSlot {
        char shown;
        char incoming;
        float progress = 1.0f;
        int direction = 1;
    }

    private static final class RollText {
        final List<RollSlot> slots = new ArrayList<>();
        float value = Float.NaN;
        long lastSeen;
    }

    private static final class HitEffect {
        final List<Particle> particles = new ArrayList<>();
        int lastHurtTime;
        float lastHealth = Float.NaN;
    }

    public TargetHUD() {
        setName("TargetHUD");
        setCategory(Category.CLIENT);
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        if (mc.player == null) return;
        float partialTick = event.getPartialTick();
        updateFrameDelta();

        if (mc.screen instanceof OpalClickGUI) {
            alpha.update(true);
            alpha.value = 255.0f;
            target = mc.player;
            heal.update(true);
            hurtAlpha.update(true);
            previewTimer += frameDelta;
            if (hitParticles.get() && previewTimer >= 1.2f) {
                previewTimer = 0.0f;
                spawnBurst(effectFor(mc.player));
            }
            drawHud(mc.player, this.x.get().floatValue(), this.y.get().floatValue(), "Fixed",
                    alpha.value, heal.value, hurtAlpha.value);
            return;
        }

        // 聊天拖拽编辑时始终在固定位置完整显示（无视跟随/多目标），保证可以拖动
        if (HudDrag.isEditing()) {
            AbstractClientPlayer preview = target != null && alpha.value >= 1.0f ? target : mc.player;
            heal.update(true);
            float health = preview == target ? heal.value : preview.getHealth();
            drawHud(preview, this.x.get().floatValue(), this.y.get().floatValue(), "Fixed",
                    255.0f, health, 0.0f);
            return;
        }

        KillAura killAura = Starain.getInstance().getModuleManager().getModule(KillAura.class);
        boolean multiActive = multiTarget.get() && killAura != null && killAura.isEnabled() && killAura.isSwitchMode()
                && KillAura.targets != null && !KillAura.targets.isEmpty();
        if (multiTarget.get() && (multiActive || !fadeTimers.isEmpty())) {
            drawMulti(multiActive, partialTick);
            return;
        }

        alpha.update(true);
        if (alpha.value < 1.0f || target == null) return;
        heal.update(true);
        hurtAlpha.update(true);

        if (followPlayer.get()) {
            Vector2f screen = projectSide(target, partialTick);
            if (screen == null) return;
            drawHud(target, screen.x, screen.y, anchorFor(screen), alpha.value, heal.value, hurtAlpha.value);
            return;
        }
        drawHud(target, this.x.get().floatValue(), this.y.get().floatValue(), "Fixed",
                alpha.value, heal.value, hurtAlpha.value);
    }

    // 多目标：目标进入时淡入放大，离开时保留最后的实体淡出，槽位平滑移动
    private void drawMulti(boolean active, float partialTick) {
        List<Integer> current = new ArrayList<>();
        if (active) {
            for (Entity entity : KillAura.targets) {
                if (entity instanceof AbstractClientPlayer) current.add(entity.getId());
            }
        }
        for (Integer id : current) {
            fadeTimers.computeIfAbsent(id, key -> new SmoothAnimationTimer(255.0f, 0.0f, 0.25f)).target = 255.0f;
        }
        int slot = 0;
        Iterator<Map.Entry<Integer, SmoothAnimationTimer>> it = fadeTimers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, SmoothAnimationTimer> entry = it.next();
            int id = entry.getKey();
            SmoothAnimationTimer fade = entry.getValue();
            Entity entity = mc.level == null ? null : mc.level.getEntity(id);
            if (!current.contains(id)) fade.target = 0.0f;
            fade.update(true);
            if (!(entity instanceof AbstractClientPlayer player) || (fade.target == 0.0f && fade.value < 2.0f)) {
                it.remove();
                healTimers.remove(id);
                hurtTimers.remove(id);
                slotTimers.remove(id);
                continue;
            }
            int index = current.contains(id) ? current.indexOf(id) : slot;
            SmoothAnimationTimer slotTimer = slotTimers.computeIfAbsent(id, key -> new SmoothAnimationTimer(index * 45.0f));
            slotTimer.target = index * 45.0f;
            slotTimer.update(true);
            drawTracked(player, partialTick, fade.value, slotTimer.value);
            slot++;
        }
    }

    private void drawTracked(AbstractClientPlayer player, float partialTick, float alphaValue, float slotOffset) {
        SmoothAnimationTimer healTimer = healTimers.computeIfAbsent(player.getId(), id -> new SmoothAnimationTimer(player.getHealth()));
        SmoothAnimationTimer hurtTimer = hurtTimers.computeIfAbsent(player.getId(), id -> new SmoothAnimationTimer(0.0F));
        healTimer.target = player.getHealth();
        hurtTimer.target = player.hurtTime > 0 ? 120.0f : 0.0f;
        healTimer.update(true);
        hurtTimer.update(true);

        if (followPlayer.get()) {
            Vector2f screen = projectSide(player, partialTick);
            if (screen == null) return;
            drawHud(player, screen.x, screen.y, anchorFor(screen), alphaValue, healTimer.value, hurtTimer.value);
            return;
        }
        drawHud(player, this.x.get().floatValue(), this.y.get().floatValue() + slotOffset, "Fixed",
                alphaValue, healTimer.value, hurtTimer.value);
    }

    private Vector2f projectSide(AbstractClientPlayer player, float partialTick) {
        double px = Mth.lerp(partialTick, player.xo, player.getX());
        double py = Mth.lerp(partialTick, player.yo, player.getY()) + player.getBbHeight() * heightRatio.get().doubleValue();
        double pz = Mth.lerp(partialTick, player.zo, player.getZ());
        Vector2f screen = ProjectionUtils.project(px, py, pz, partialTick);
        if (screen.x == Float.MAX_VALUE || !Float.isFinite(screen.x) || !Float.isFinite(screen.y)) {
            return null;
        }
        return screen;
    }

    private String anchorFor(Vector2f screen) {
        if (side.isMode("Auto")) {
            return screen.x < mc.getWindow().getGuiScaledWidth() / 2.0f ? "Right" : "Left";
        }
        return side.get();
    }

    private void drawHud(AbstractClientPlayer target, float baseX, float baseY, String anchor,
                         float alphaValue, float healValue, float hurtValue) {
        // 淡入淡出时附带轻微缩放，避免组件突然出现/消失
        float pop = 0.88f + 0.12f * Mth.clamp(alphaValue / 255.0f, 0.0f, 1.0f);
        Skia.beginHudScale(baseX, baseY, scale.get().floatValue() * pop);
        try {
            if (style.isMode("Material")) {
                drawMaterial(target, baseX, baseY, anchor, alphaValue, healValue, hurtValue);
            } else {
                drawDefault(target, baseX, baseY, anchor, alphaValue, healValue, hurtValue);
            }
        } finally {
            Skia.endHudScale();
        }
    }

    private void drawDefault(AbstractClientPlayer target, float baseX, float baseY, String anchor,
                             float alphaValue, float healValue, float hurtValue) {
        float width = 125.0f;
        float height = 40.0f;
        Font miSans = Fonts.getNormal(10.0f);
        Font iconFill = Fonts.getIconFill(10.0f);
        float heartIconWidth = iconFill.measureTextWidth("\ue87d");
        String name = target.getName().getString();
        float nameWidth = Skia.getTextWidth(name, miSans);
        float healBarWidth = (nameWidth + 45.0f > width) ? nameWidth : 78.0f;
        if (nameWidth + 45.0f > width) width = nameWidth + 45.0f;

        float offset = sideOffset.get().floatValue();
        float x;
        float y;
        switch (anchor) {
            case "Right" -> {
                x = baseX + offset;
                y = baseY - height / 2.0f;
            }
            case "Left" -> {
                x = baseX - offset - width;
                y = baseY - height / 2.0f;
            }
            default -> {
                x = baseX;
                y = baseY;
            }
        }
        if (anchor.equals("Fixed") && baseY == this.y.get().floatValue()) {
            HudDrag.report(this, this.x, this.y, x, y, width, height);
        }

        if (glow.get()) {
            Glow.queueHud(x, y, width, height, 6.0f, alphaValue / 255.0f, true);
        }

        try (Paint layerPaint = new Paint()) {
            layerPaint.setAlpha((int) alphaValue);
            Skia.getCanvas().saveLayer(Rect.makeXYWH(x - 10, y - 10, width + 20, height + 20), layerPaint);
            if (alphaValue >= 75f) {
                Skia.drawShadow(x, y, width, height, 6.0f);
                Skia.drawBlur(x, y, width, height, 6.0f);
            }
            Skia.drawRoundedRect(x, y, width, height, 6.0f, Design.BACKGROUND_COLOR);
            Skia.drawPlayerHead(target, x + 5.0f, y + 5.0f, 30.0f, 30.0f, 4.0f);
            if (hurtValue > 1.0f) {
                Skia.drawRoundedRect(x + 5.0f, y + 5.0f, 30.0f, 30.0f, 4.0f, new Color(255, 80, 80, (int) hurtValue));
            }
            Path path = Path.makeRRect(RRect.makeXYWH(x + 40.0f, y + 30.5f, healBarWidth, 5.0f, 3.0f));
            Skia.save();
            Skia.getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            Skia.drawRect(x + 40.0f, y + 30.5f, healBarWidth, 5.0f, new Color(0, 0, 0, 50));
            float healthWidth = (healBarWidth / 20.0f) * healValue;
            if (syncColor.get()) {
                Skia.drawThemeGradientRect(x + 40.0f, y + 30.5f, healthWidth, 5.0f,
                        x + 40.0f, x + 40.0f + healBarWidth, Math.max(2, Math.round(healBarWidth / 6.0f)));
            } else {
                Skia.drawRect(x + 40.0f, y + 30.5f, healthWidth, 5.0f, new Color(200, 200, 200));
            }
            Skia.restore();
            String healthText = String.format("%.2f", target.getHealth());
            if (syncColor.get()) {
                Skia.drawGradientText(name, x + 40.0f, y + 4.5f, miSans);
                Skia.drawGradientText("\ue87d", x + 40.0f, y + 18f, iconFill);
            } else {
                Skia.drawText(name, x + 40.0f, y + 4.5f, Design.TEXT_COLOR, miSans);
                Skia.drawText("\ue87d", x + 40.0f, y + 18f, new Color(200, 200, 200), iconFill);
            }
            drawRolling(target.getId(), healthText, target.getHealth(),
                    x + 40.0f + heartIconWidth, y + 17.5f, miSans);
            path.close();
            Skia.restore();
        }
        if (hitParticles.get()) {
            renderParticles(target, x + 20.0f, y + 20.0f, alphaValue / 255.0f);
        }
    }

    private void drawRolling(int id, String text, float value, float x, float yTop, Font font) {
        if (healthRolls.size() > 32) healthRolls.clear();
        RollText roll = healthRolls.computeIfAbsent(id, key -> new RollText());
        long now = System.nanoTime();
        boolean fresh = Float.isNaN(roll.value) || now - roll.lastSeen > 300_000_000L;
        roll.lastSeen = now;
        if (fresh) {
            snap(roll, text);
            roll.value = value;
        } else if (Math.abs(value - roll.value) > 0.001f) {
            sync(roll, text, value >= roll.value ? 1 : -1);
            roll.value = value;
        }

        Rect bounds = font.measureText(text);
        float penX = x - bounds.getLeft();
        float baseline = yTop - bounds.getTop();
        float travel = bounds.getHeight() + 2.0f;

        Skia.save();
        Skia.clipRect(x - 1.0f, yTop - 1.0f, font.measureTextWidth(text) + 3.0f, bounds.getHeight() + 3.0f);
        for (int i = 0; i < roll.slots.size(); i++) {
            RollSlot slot = roll.slots.get(i);
            slot.progress = Math.min(1.0f, slot.progress + frameDelta * 4.5f);
            String incoming = String.valueOf(slot.incoming);
            Color color = syncColor.get() ? Theme.accent(-i) : new Color(200, 200, 200);
            if (slot.progress >= 1.0f) {
                Skia.drawBaselineText(incoming, penX, baseline, color.getRGB() | 0xFF000000, font);
            } else {
                float eased = 1.0f - (float) Math.pow(1.0f - slot.progress, 3.0d);
                int outAlpha = (int) (255.0f * (1.0f - eased));
                int inAlpha = (int) (255.0f * eased);
                Skia.drawBaselineText(String.valueOf(slot.shown), penX,
                        baseline - slot.direction * travel * eased,
                        (outAlpha << 24) | (color.getRGB() & 0xFFFFFF), font);
                Skia.drawBaselineText(incoming, penX,
                        baseline + slot.direction * travel * (1.0f - eased),
                        (inAlpha << 24) | (color.getRGB() & 0xFFFFFF), font);
            }
            penX += font.measureTextWidth(incoming);
        }
        Skia.restore();
    }

    private static void snap(RollText roll, String text) {
        roll.slots.clear();
        for (int i = 0; i < text.length(); i++) {
            RollSlot slot = new RollSlot();
            slot.shown = text.charAt(i);
            slot.incoming = text.charAt(i);
            slot.progress = 1.0f;
            roll.slots.add(slot);
        }
    }

    private static void sync(RollText roll, String text, int direction) {
        while (roll.slots.size() > text.length()) {
            roll.slots.remove(roll.slots.size() - 1);
        }
        while (roll.slots.size() < text.length()) {
            RollSlot slot = new RollSlot();
            slot.shown = ' ';
            slot.incoming = text.charAt(roll.slots.size());
            slot.progress = 0.0f;
            slot.direction = direction;
            roll.slots.add(slot);
        }
        for (int i = 0; i < text.length(); i++) {
            RollSlot slot = roll.slots.get(i);
            char next = text.charAt(i);
            if (slot.incoming != next) {
                slot.shown = slot.incoming;
                slot.incoming = next;
                slot.progress = 0.0f;
                slot.direction = direction;
            }
        }
    }

    private void updateFrameDelta() {
        long now = System.nanoTime();
        frameDelta = lastFrameNano == 0L ? 0.0f : Math.min(0.05f, (now - lastFrameNano) / 1.0E9f);
        lastFrameNano = now;
    }

    private HitEffect effectFor(AbstractClientPlayer player) {
        if (hitEffects.size() > 32) hitEffects.clear();
        return hitEffects.computeIfAbsent(player.getId(), id -> new HitEffect());
    }

    private void drawMaterial(AbstractClientPlayer target, float baseX, float baseY, String anchor,
                              float alphaValue, float healValue, float hurtValue) {
        final float pad = 5.0f, avatar = 26.0f, height = 38.5f, radius = 11.0f;
        final float contentX = pad + avatar + 7.0f;
        Font nameFont = Fonts.getBold(9.0f);
        Font microFont = Fonts.getMedium(5.5f);
        Font statFont = Fonts.getMedium(6.0f);
        Font icon = Fonts.getIconFill(7.0f);

        String name = target.getName().getString();
        String dist = mc.player == null || target == mc.player ? "0.0m"
                : String.format(java.util.Locale.ROOT, "%.1fm", target.distanceTo(mc.player));
        // 按最宽数字预留宽度，数值变化时胶囊不伸缩
        float chipW = Skia.getTextWidth(dist.replaceAll("[0-9]", "0"), microFont) + 10.0f;
        float nameW = Skia.getTextWidth(name, nameFont);
        float width = Mth.clamp(contentX + Math.max(nameW + chipW + 8.0f, 100.0f) + pad + 4.0f, 158.5f, 240.0f);

        float offset = sideOffset.get().floatValue();
        float x, y;
        switch (anchor) {
            case "Right" -> { x = baseX + offset; y = baseY - height / 2.0f; }
            case "Left" -> { x = baseX - offset - width; y = baseY - height / 2.0f; }
            default -> { x = baseX; y = baseY; }
        }
        if (anchor.equals("Fixed") && baseY == this.y.get().floatValue()) {
            HudDrag.report(this, this.x, this.y, x, y, width, height);
        }

        Color accent = Theme.accent(0);
        float a = alphaValue / 255.0f;
        if (alphaValue >= 75f) {
            if (glow.get()) Glow.queueHud(x, y, width, height, radius, a, true);
            Skia.drawShadow(x, y, width, height, radius);
            Skia.drawBlur(x, y, width, height, radius);
        }
        Skia.setAlpha((int) alphaValue);
        Skia.drawRoundedRect(x, y, width, height, radius, Design.BACKGROUND_COLOR);

        Skia.drawPlayerHead(target, x + pad, y + pad, avatar, avatar, 8.0f);
        if (hurtValue > 1.0f) {
            Skia.drawRoundedRect(x + pad, y + pad, avatar, avatar, 8.0f, new Color(255, 75, 75, (int) Math.min(160, hurtValue)));
        }

        float chipX = x + width - pad - chipW;
        String shown = name;
        float nameMax = chipX - (x + contentX) - 6.0f;
        while (shown.length() > 1 && Skia.getTextWidth(shown, nameFont) > nameMax) shown = shown.substring(0, shown.length() - 1);
        Skia.drawText(shown, x + contentX, y + 6.0f, accent, nameFont);
        Skia.drawRoundedRect(chipX, y + 4.0f, chipW, 11.0f, 5.5f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45));
        // 固定基线 + 居中，避免数字变化时按字形边界重新定位导致抖动
        try (Paint distPaint = new Paint()) {
            distPaint.setColor(Design.TEXT_COLOR.getRGB());
            distPaint.setAntiAlias(true);
            float textW = Skia.getTextWidth(dist, microFont);
            float baseline = y + 4.0f + 5.5f + microFont.getMetrics().getCapHeight() / 2.0f;
            Skia.getCanvas().drawString(dist, chipX + (chipW - textW) / 2.0f, baseline, microFont, distPaint);
        }

        float barX = x + contentX, barY = y + 18.5f, barW = Math.min(width - contentX - pad - 4.0f, 90.0f);
        float max = Math.max(1.0f, target.getMaxHealth());
        float ratio = Mth.clamp(healValue / max, 0.0f, 1.0f);
        drawHealthBar(barX, barY, barW, ratio, accent);

        String hp = String.format(java.util.Locale.ROOT, "%.1f / %d", target.getHealth(), Math.round(max));
        Skia.drawText(hp, barX, y + 27.0f, new Color(200, 200, 205), statFont);
        String armor = String.valueOf(target.getArmorValue());
        float armorW = Skia.getTextWidth(armor, statFont);
        float iconW = Skia.getTextWidth(Icon.SHIELD, icon);
        float ax = x + width - pad - 4.0f - armorW;
        Skia.drawText(armor, ax, y + 27.0f, new Color(200, 200, 205), statFont);
        Skia.drawText(Icon.SHIELD, ax - iconW - 2.0f, y + 26.5f, accent, icon);
        Skia.restore();

        if (hitParticles.get()) {
            renderParticles(target, x + pad + avatar / 2.0f, y + pad + avatar / 2.0f, a);
        }
    }

    private void drawHealthBar(float barX, float barY, float barW, float ratio, Color accent) {
        Color track = new Color(255, 255, 255, 25);
        switch (healthBar.get()) {
            case "Wave" -> {
                Skia.drawRoundedRect(barX, barY, barW, 4.0f, 2.0f, track);
                float fill = barW * ratio;
                if (fill < 1.0f) return;
                float phase = (System.currentTimeMillis() % 100000L) / 180.0f;
                // 0.143 起 Path 由 PathBuilder 生成；build 出来的是独立对象，用完立刻关闭。
                try (PathBuilder waveBuilder = new PathBuilder(); Paint line = new Paint(); Paint glowPaint = new Paint()) {
                    for (float px = 0; px <= fill; px += 1.0f) {
                        float py = barY + 2.0f + (float) Math.sin(px / 5.0f - phase) * 1.4f;
                        if (px == 0) waveBuilder.moveTo(barX, py); else waveBuilder.lineTo(barX + px, py);
                    }
                    try (Path wave = waveBuilder.build()) {
                        glowPaint.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110).getRGB());
                        glowPaint.setMode(PaintMode.STROKE);
                        glowPaint.setStrokeWidth(3.0f);
                        glowPaint.setAntiAlias(true);
                        glowPaint.setMaskFilter(MaskFilter.makeBlur(FilterBlurMode.NORMAL, 1.5f));
                        line.setColor(accent.getRGB());
                        line.setMode(PaintMode.STROKE);
                        line.setStrokeWidth(1.4f);
                        line.setStrokeCap(PaintStrokeCap.ROUND);
                        line.setAntiAlias(true);
                        Skia.getCanvas().drawPath(wave, glowPaint);
                        Skia.getCanvas().drawPath(wave, line);
                    }
                }
            }
            case "Segment" -> {
                final float seg = 2.2f, gap = 1.0f, h = 5.0f;
                int count = Math.max(1, (int) ((barW + gap) / (seg + gap)));
                int filled = Math.round(count * ratio);
                for (int i = 0; i < count; i++) {
                    float sx = barX + i * (seg + gap);
                    Color c = i < filled ? Theme.accent(i * 2) : track;
                    Skia.drawRoundedRect(sx, barY - 0.5f, seg, h, 0.6f, c);
                }
            }
            default -> {
                Skia.drawRoundedRect(barX, barY, barW, 4.0f, 2.0f, track);
                if (ratio > 0.01f) {
                    Skia.drawRoundedRect(barX, barY, Math.max(4.0f, barW * ratio), 4.0f, 2.0f, accent);
                }
            }
        }
    }

    private void renderParticles(AbstractClientPlayer player, float centerX, float centerY, float alphaScale) {
        HitEffect effect = effectFor(player);
        if (effect.particles.isEmpty() && player.hurtTime <= effect.lastHurtTime) {
            effect.lastHurtTime = player.hurtTime;
            effect.lastHealth = player.getHealth();
            return;
        }
        float health = player.getHealth();
        boolean hurt = player.hurtTime > effect.lastHurtTime
                || (!Float.isNaN(effect.lastHealth) && health < effect.lastHealth - 0.01f);
        effect.lastHurtTime = player.hurtTime;
        effect.lastHealth = health;
        if (hurt) spawnBurst(effect);

        double phaseSpan = 360.0 / themeSpread();
        boolean glowSync = Glow.additiveSync();
        Iterator<Particle> iterator = effect.particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.life -= frameDelta;
            if (particle.life <= 0.0f) {
                iterator.remove();
                continue;
            }
            particle.velocityY += 110.0f * frameDelta;
            particle.offsetX += particle.velocityX * frameDelta;
            particle.offsetY += particle.velocityY * frameDelta;

            float progress = particle.life / particle.maxLife;
            int particleAlpha = (int) (255.0f * progress * alphaScale);
            if (particleAlpha <= 2) continue;
            Color base = syncColor.get() ? Theme.accent(particle.phase * phaseSpan) : new Color(255, 160, 160);
            float drawX = centerX + particle.offsetX;
            float drawY = centerY + particle.offsetY;
            float radius = particle.size * (0.5f + 0.5f * progress);
            if (glowSync) {
                Glow.queueHud(drawX - radius, drawY - radius, radius * 2.0f, radius * 2.0f, radius, particleAlpha / 255.0f);
            } else {
                Skia.drawCircle(drawX, drawY, radius * 2.6f,
                        new Color(base.getRed(), base.getGreen(), base.getBlue(), particleAlpha / 6));
            }
            Skia.drawCircle(drawX, drawY, radius,
                    new Color(base.getRed(), base.getGreen(), base.getBlue(), particleAlpha));
        }
    }

    private int themeSpread() {
        HUD hud = Starain.getInstance().getModuleManager().getModule(HUD.class);
        return hud == null ? 1 : Math.max(1, hud.getThemeSpread());
    }

    private void spawnBurst(HitEffect effect) {
        int count = particleCount.get().intValue();
        if (effect.particles.size() > count * 6) return;
        for (int i = 0; i < count; i++) {
            Particle particle = new Particle();
            double angle = i / (double) count * Math.PI * 2.0d + (Math.random() - 0.5d) * 0.4d;
            double speed = 48.0d + Math.random() * 62.0d;
            particle.velocityX = (float) (Math.cos(angle) * speed);
            particle.velocityY = (float) (Math.sin(angle) * speed) - 26.0f;
            particle.maxLife = 0.55f + (float) Math.random() * 0.4f;
            particle.life = particle.maxLife;
            particle.size = 1.7f + (float) Math.random() * 1.7f;
            particle.phase = i / (float) count;
            effect.particles.add(particle);
        }
    }

    @EventTarget
    public void onPreTick(GameTickEvent event) {
        if (mc.player == null || event.getType() != EventType.PRE) return;
        KillAura module = Starain.getInstance().getModuleManager().getModule(KillAura.class);
        if (mc.hitResult instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof AbstractClientPlayer player) {
            alpha.target = 255.0f;
            target = player;
            heal.target = player.getHealth();
            hurtAlpha.target = player.hurtTime > 0 ? 120.0f : 0.0f;
        } else if (module.isEnabled() && KillAura.getTarget() != null && KillAura.getTarget() instanceof AbstractClientPlayer player) {
            alpha.target = 255.0f;
            target = player;
            heal.target = player.getHealth();
            hurtAlpha.target = player.hurtTime > 0 ? 120.0f : 0.0f;
        } else {
            alpha.target = 0.0f;
            hurtAlpha.target = 0.0f;
        }
    }
}




