package today.starain.module.impl.client;

import today.starain.Starain;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.NumberValue;

import java.awt.*;

public class HUD extends ClientModule {

    private final BooleanValue blur = new BooleanValue("Blur", false, this);
    private final BooleanValue limitBlurFps = new BooleanValue("Limit Blur FPS", false, this, blur::get);
    private final NumberValue blurFps = new NumberValue("Blur FPS", 25F, 165F, 60F, 1F, this, () -> blur.get() && limitBlurFps.get());
    private final NumberValue blurRadius = new NumberValue("Blur Radius", 1F, 10F, 3F, 0.01F, this, blur::get);
    private final NumberValue blurIterations = new NumberValue("Blur Iterations", 1F, 5F, 1F, 1F, this, blur::get);
    private final BooleanValue shadow = new BooleanValue("Shadow", false, this);
    private final ColorValue shadowColor = new ColorValue("Shadow Color", new Color(0, 0, 0, 120), this, shadow::get);

    public HUD() {
        setName("HUD");
        setCategory(Category.CLIENT);
    }

be    // 如果直接去new一个对象这个zkm的indy没法混 直接是明文调用 所以要搞一个newInstance方法
    public static HUD newInstance() {
        return new HUD();
    }

    @Override
    protected void onEnable() {
        setEnabled(false);
    }

    public static boolean isBlurEnabled() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).blur.get();
    }

    public static boolean isLimitBlurFpsEnabled() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).limitBlurFps.get();
    }

    public static int getBlurFps() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).blurFps.get().intValue();
    }

    public static float getBlurRadius() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).blurRadius.get().floatValue();
    }

    public static int getBlurIterations() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).blurIterations.get().intValue();
    }

    public static boolean isShadowEnabled() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).shadow.get();
    }

    public static Color getShadowColor() {
        return Starain.getInstance().getModuleManager().getModule(HUD.class).shadowColor.get();
    }
}
