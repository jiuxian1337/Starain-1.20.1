package today.starain.module.impl.client;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
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

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class TargetHUD extends ClientModule {
    private final NumberValue x = new NumberValue("X", 0, 1000, 300, 1, this);
    private final NumberValue y = new NumberValue("y", 0, 1000, 300, 1, this);
    private final BooleanValue syncColor = new BooleanValue("Sync Color", false, this);
    private final BooleanValue glow = new BooleanValue("Glow", false, this);
    private final BooleanValue followPlayer = new BooleanValue("Follow Player", false, this);
    private final BooleanValue multiTarget = new BooleanValue("Multi Target", false, this);
    private final ModeValue side = new ModeValue("Side", "Right", new String[]{"Right", "Left", "Auto"}, this, followPlayer::get);
    private final NumberValue sideOffset = new NumberValue("Side Offset", 0, 80, 16, 1, this, followPlayer::get);
    private final NumberValue heightRatio = new NumberValue("Height", 0.0, 1.5, 0.8, 0.05, this, followPlayer::get);
    private final Map<Integer, SmoothAnimationTimer> healTimers = new HashMap<>();
    private final Map<Integer, SmoothAnimationTimer> hurtTimers = new HashMap<>();
    private final SmoothAnimationTimer heal = new SmoothAnimationTimer(20.0F);
    private final SmoothAnimationTimer alpha = new SmoothAnimationTimer(255.0F);
    private final SmoothAnimationTimer hurtAlpha = new SmoothAnimationTimer(0.0F);
    private AbstractClientPlayer target;

    public TargetHUD() {
        setName("TargetHUD");
        setCategory(Category.CLIENT);
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        if (mc.player == null) return;
        float partialTick = event.getPartialTick();

        if (mc.screen instanceof OpalClickGUI) {
            alpha.update(true);
            alpha.value = 255.0f;
            target = mc.player;
            heal.update(true);
            hurtAlpha.update(true);
            drawHud(mc.player, this.x.get().floatValue(), this.y.get().floatValue(), "Fixed",
                    alpha.value, heal.value, hurtAlpha.value);
            return;
        }

        KillAura killAura = Starain.getInstance().getModuleManager().getModule(KillAura.class);
        if (multiTarget.get() && killAura != null && killAura.isEnabled() && killAura.isSwitchMode()
                && KillAura.targets != null && !KillAura.targets.isEmpty()) {
            healTimers.keySet().removeIf(id -> mc.level == null || mc.level.getEntity(id) == null);
            hurtTimers.keySet().removeIf(id -> mc.level == null || mc.level.getEntity(id) == null);
            for (Entity entity : KillAura.targets) {
                if (entity instanceof AbstractClientPlayer player) {
                    drawTracked(player, partialTick);
                }
            }
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

    private void drawTracked(AbstractClientPlayer player, float partialTick) {
        SmoothAnimationTimer healTimer = healTimers.computeIfAbsent(player.getId(), id -> new SmoothAnimationTimer(player.getHealth()));
        SmoothAnimationTimer hurtTimer = hurtTimers.computeIfAbsent(player.getId(), id -> new SmoothAnimationTimer(0.0F));
        healTimer.target = player.getHealth();
        hurtTimer.target = player.hurtTime > 0 ? 120.0f : 0.0f;
        healTimer.update(true);
        hurtTimer.update(true);

        if (followPlayer.get()) {
            Vector2f screen = projectSide(player, partialTick);
            if (screen == null) return;
            drawHud(player, screen.x, screen.y, anchorFor(screen), 255.0f, healTimer.value, hurtTimer.value);
            return;
        }
        int index = KillAura.targets.indexOf(player);
        drawHud(player, this.x.get().floatValue(), this.y.get().floatValue() + index * 45.0f, "Fixed",
                255.0f, healTimer.value, hurtTimer.value);
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

        if (glow.get()) {
            today.starain.module.impl.render.Glow.queueHud(x, y, width, height, 6.0f, alphaValue / 255.0f, true);
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
            Path path = new Path();
            path.addRRect(RRect.makeXYWH(x + 40.0f, y + 30.5f, healBarWidth, 5.0f, 3.0f));
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
            String healthText = String.format("%.2f", healValue);
            if (syncColor.get()) {
                Skia.drawGradientText(name, x + 40.0f, y + 4.5f, miSans);
                Skia.drawGradientText("\ue87d", x + 40.0f, y + 18f, iconFill);
                Skia.drawGradientText(healthText, x + 40.0f + heartIconWidth, y + 17.5f, miSans);
            } else {
                Skia.drawText(name, x + 40.0f, y + 4.5f, Design.TEXT_COLOR, miSans);
                Skia.drawText("\ue87d", x + 40.0f, y + 18f, new Color(200, 200, 200), iconFill);
                Skia.drawText(healthText, x + 40.0f + heartIconWidth, y + 17.5f, new Color(200, 200, 200), miSans);
            }
            path.close();
            Skia.restore();
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
