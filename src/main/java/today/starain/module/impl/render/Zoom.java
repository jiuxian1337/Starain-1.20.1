package today.starain.module.impl.render;

import org.lwjgl.glfw.GLFW;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

/**
 * 视角缩放（类似 OptiFine 的 C 键放大）。Hold 模式下松开按键自动关闭。
 */
public class Zoom extends ClientModule {

    private final ModeValue mode = new ModeValue("Mode", "Hold", new String[]{"Hold", "Toggle"}, this);
    private final NumberValue factor = new NumberValue("Factor", 1.5, 10.0, 4.0, 0.5, this);
    private final BooleanValue smooth = new BooleanValue("Smooth", true, this);
    private final BooleanValue scroll = new BooleanValue("Scroll Zoom", true, this);
    private static float scrollFactor = -1.0f;

    private static float current = 1.0f;
    private static long lastNano;

    public Zoom() {
        setName("Zoom");
        setCategory(Category.RENDER);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!mode.isMode("Hold") || getKey() <= 0 || getKey() >= 1000) return;
        if (mc.screen == null && GLFW.glfwGetKey(mc.getWindow().getWindow(), getKey()) != GLFW.GLFW_PRESS) {
            setEnabled(false);
        }
    }

    /** 开启时滚轮调整缩放倍率；返回 true 表示已消费滚轮事件。 */
    public static boolean onScroll(double amount) {
        Starain starain = Starain.getInstance();
        if (starain == null || starain.getModuleManager() == null) return false;
        Zoom zoom = starain.getModuleManager().getModule(Zoom.class);
        if (zoom == null || !zoom.isEnabled() || !zoom.scroll.get() || amount == 0.0) return false;
        float base = scrollFactor > 0.0f ? scrollFactor : zoom.factor.get().floatValue();
        scrollFactor = Math.max(1.0f, Math.min(50.0f, base * (amount > 0 ? 1.15f : 1.0f / 1.15f)));
        return true;
    }

    /** 由 MixinGameRenderer.getFov 每帧调用，返回 FOV 除数。 */
    public static double fovDivisor() {
        Starain starain = Starain.getInstance();
        if (starain == null || starain.getModuleManager() == null) return 1.0;
        Zoom zoom = starain.getModuleManager().getModule(Zoom.class);
        if (zoom == null) return 1.0;
        if (!zoom.isEnabled()) scrollFactor = -1.0f;
        float target = !zoom.isEnabled() ? 1.0f : scrollFactor > 0.0f ? scrollFactor : zoom.factor.get().floatValue();
        long now = System.nanoTime();
        float delta = lastNano == 0L ? 0.016f : Math.min((now - lastNano) / 1_000_000_000.0f, 0.1f);
        lastNano = now;
        if (zoom.smooth.get()) {
            current += (target - current) * (1.0f - (float) Math.exp(-delta * 12.0f));
        } else {
            current = target;
        }
        if (Math.abs(current - target) < 0.001f) current = target;
        return current;
    }
}


