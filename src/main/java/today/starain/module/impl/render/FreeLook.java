package today.starain.module.impl.render;

import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ModeValue;

/**
 * 自由视角：切到第三人称并锁定玩家朝向，鼠标只转动相机。
 */
public class FreeLook extends ClientModule {

    private final ModeValue mode = new ModeValue("Mode", "Hold", new String[]{"Hold", "Toggle"}, this);
    private final BooleanValue front = new BooleanValue("Front View", false, this);

    private static float cameraYaw, cameraPitch;
    private CameraType previous;

    public FreeLook() {
        setName("FreeLook");
        setCategory(Category.RENDER);
    }

    @Override
    protected void onEnable() {
        if (mc.player == null) {
            setEnabled(false);
            return;
        }
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        previous = mc.options.getCameraType();
        mc.options.setCameraType(front.get() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
    }

    @Override
    protected void onDisable() {
        if (previous != null) mc.options.setCameraType(previous);
        previous = null;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (!mode.isMode("Hold") || getKey() <= 0 || getKey() >= 1000) return;
        if (mc.screen == null && GLFW.glfwGetKey(mc.getWindow().getWindow(), getKey()) != GLFW.GLFW_PRESS) {
            setEnabled(false);
        }
    }

    private static FreeLook active() {
        Starain starain = Starain.getInstance();
        if (starain == null || starain.getModuleManager() == null) return null;
        FreeLook module = starain.getModuleManager().getModule(FreeLook.class);
        return module != null && module.isEnabled() ? module : null;
    }

    public static boolean isActive() {
        return active() != null;
    }

    /** 代替 Entity.turn：只转相机，系数与原版一致。 */
    public static void turn(double dx, double dy) {
        cameraYaw += (float) dx * 0.15f;
        cameraPitch = Math.max(-90.0f, Math.min(90.0f, cameraPitch + (float) dy * 0.15f));
    }

    public static float yaw() {
        return cameraYaw;
    }

    public static float pitch() {
        return cameraPitch;
    }
}

