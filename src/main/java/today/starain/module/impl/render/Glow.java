package today.starain.module.impl.render;

import net.minecraft.world.entity.player.Player;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.RenderWorldEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Theme;
import today.starain.utils.render.shader.impl.OutlineGlow;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Glow extends ClientModule {

    private final BooleanValue syncColor = new BooleanValue("Sync Color", true, this);
    private final ColorValue color = new ColorValue("Color", new Color(0, 186, 255), this, () -> !syncColor.get());
    private final NumberValue radius = new NumberValue("Radius", 1.0, 32.0, 7.0, 1.0, this);
    private final NumberValue strength = new NumberValue("Strength", 0.1, 3.0, 1.0, 0.1, this);
    private final NumberValue spread = new NumberValue("Color Spread", 0.0, 32.0, 7.0, 1.0, this, syncColor::get);
    private final BooleanValue antiAliasing = new BooleanValue("Anti-Aliasing", true, this);
    private final BooleanValue self = new BooleanValue("Self", false, this);

    public Glow() {
        setName("Glow");
        setCategory(Category.RENDER);
    }

    @EventTarget
    public void onRenderWorld(RenderWorldEvent event) {
        if (mc.level == null || mc.player == null) return;

        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        List<Player> targets = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player && (!self.get() || firstPerson)) continue;
            if (!player.isAlive() || player.isSpectator() || player.isInvisible()) continue;
            targets.add(player);
        }
        boolean handGlow = self.get() && firstPerson;
        if (targets.isEmpty() && !handGlow) return;

        Color colorA;
        Color colorB;
        if (syncColor.get()) {
            colorA = Theme.accent(0);
            colorB = Theme.accent(-spread.get().intValue());
        } else {
            colorA = color.get();
            colorB = color.get();
        }

        OutlineGlow.INSTANCE.draw(event.getPoseStack(), event.getPartialTick(), targets,
                colorA, colorB, radius.get().floatValue(), strength.get().floatValue(), antiAliasing.get(),
                handGlow);
    }
}
