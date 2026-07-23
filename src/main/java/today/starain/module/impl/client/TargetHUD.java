package today.starain.module.impl.client;

import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.player.AbstractClientPlayer;
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
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.NumberValue;

import java.awt.*;

public class TargetHUD extends ClientModule {
    private final NumberValue x = new NumberValue("X", 0, 1000, 300, 1, this);
    private final NumberValue y = new NumberValue("y", 0, 1000, 300, 1, this);
    private final BooleanValue syncColor = new BooleanValue("Sync Color", false, this);
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
        alpha.update(true);
        if (mc.screen instanceof OpalClickGUI) {
            target = mc.player;
            alpha.value = 255.0f;
        }
        if (alpha.value < 1.0f) return;
        heal.update(true);
        hurtAlpha.update(true);
        float x = this.x.get().floatValue();
        float y = this.y.get().floatValue();
        float width = 125.0f;
        float height = 40.0f;
        Font miSans = Fonts.getNormal(10.0f);
        Font iconFill = Fonts.getIconFill(10.0f);
        float heartIconWidth = iconFill.measureTextWidth("\ue87d");
        String name = target.getName().getString();
        float nameWidth = Skia.getTextWidth(name, miSans);
        float healBarWidth = (nameWidth + 45.0f > width) ? nameWidth : 78.0f;
        if (nameWidth + 45.0f > width) width = nameWidth + 45.0f;

        try (Paint layerPaint = new Paint()) {
            layerPaint.setAlpha((int) alpha.value);
            Skia.getCanvas().saveLayer(Rect.makeXYWH(x - 10, y - 10, width + 20, height + 20), layerPaint);
            if (alpha.value >= 75f) {
                Skia.drawShadow(x, y, width, height, 6.0f);
                Skia.drawBlur(x, y, width, height, 6.0f);
            }
            Skia.drawRoundedRect(x, y, width, height, 6.0f, Design.BACKGROUND_COLOR);
            Skia.drawPlayerHead(target, x + 5.0f, y + 5.0f, 30.0f, 30.0f, 4.0f);
            if (hurtAlpha.value > 1.0f) {
                Skia.drawRoundedRect(x + 5.0f, y + 5.0f, 30.0f, 30.0f, 4.0f, new Color(255, 80, 80, (int) hurtAlpha.value));
            }
            Path path = new Path();
            path.addRRect(RRect.makeXYWH(x + 40.0f, y + 30.5f, healBarWidth, 5.0f, 3.0f));
            Skia.save();
            Skia.getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            Skia.drawRect(x + 40.0f, y + 30.5f, healBarWidth, 5.0f, new Color(0, 0, 0, 50));
            float healthWidth = (healBarWidth / 20.0f) * heal.value;
            if (syncColor.get()) {
                Skia.drawThemeGradientRect(x + 40.0f, y + 30.5f, healthWidth, 5.0f,
                        x + 40.0f, x + 40.0f + healBarWidth, Math.max(2, Math.round(healBarWidth / 6.0f)));
            } else {
                Skia.drawRect(x + 40.0f, y + 30.5f, healthWidth, 5.0f, new Color(200, 200, 200));
            }
            Skia.restore();
            String healthText = String.format("%.2f", heal.value);
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
