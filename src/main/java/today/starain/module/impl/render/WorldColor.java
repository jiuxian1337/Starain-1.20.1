package today.starain.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;
import today.starain.Starain;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Theme;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;

public class WorldColor extends ClientModule {

    private static boolean inWorld;
    private static WorldColor cached;

    private final BooleanValue syncColor = new BooleanValue("Sync Color", false, this);
    private final ColorValue color = new ColorValue("Color", new Color(120, 70, 210), this, () -> !syncColor.get());
    private final NumberValue strength = new NumberValue("Strength", 0.0, 100.0, 100.0, 1.0, this);
    private final BooleanValue sky = new BooleanValue("Sky", true, this);
    private final BooleanValue hand = new BooleanValue("Hand", false, this);

    public WorldColor() {
        setName("WorldColor");
        setCategory(Category.RENDER);
    }

    public static void setInWorld(boolean value) {
        inWorld = value;
    }

    public static boolean shouldTintNow() {
        if (!inWorld) {
            return false;
        }
        WorldColor module = get();
        if (module == null || !module.isEnabled() || module.strength.get().floatValue() <= 0.0F) {
            return false;
        }
        if (!module.sky.get()) {
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                String name = shader.getName();
                if (name != null && (name.startsWith("rendertype_sky") || name.startsWith("rendertype_clouds"))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static float[] modulate(float[] original) {
        WorldColor module = get();
        if (module == null || original == null || original.length < 4) {
            return original;
        }
        float s = module.strength.get().floatValue() / 100.0F;
        Color base = module.syncColor.get() ? Theme.accent(0) : module.color.get();
        float tr = 1.0F - s + (base.getRed() / 255.0F) * s;
        float tg = 1.0F - s + (base.getGreen() / 255.0F) * s;
        float tb = 1.0F - s + (base.getBlue() / 255.0F) * s;
        return new float[]{original[0] * tr, original[1] * tg, original[2] * tb, original[3]};
    }

    public static boolean tintHand() {
        WorldColor module = get();
        return module != null && module.hand.get();
    }

    private static WorldColor get() {
        if (cached != null) {
            return cached;
        }
        Starain starain = Starain.getInstance();
        if (starain == null || starain.getModuleManager() == null) {
            return null;
        }
        cached = starain.getModuleManager().getModule(WorldColor.class);
        return cached;
    }
}
