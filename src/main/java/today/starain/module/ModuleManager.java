package today.starain.module;

import org.lwjgl.glfw.GLFW;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.KeyboardEvent;
import today.starain.event.impl.MouseButtonEvent;
import today.starain.module.impl.client.ArrayListModule;
import today.starain.module.impl.client.ClickGuiModule;
import today.starain.module.impl.client.HUD;
import today.starain.module.impl.client.Notification;
import today.starain.module.impl.movement.Sprint;
import today.starain.module.impl.render.FullBright;
import today.starain.utils.render.BindUtils;
import today.starain.utils.wrapper.Wrapper;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager implements Wrapper {
    private final List<ClientModule> modules = new ArrayList<>();

    private ModuleManager() {
        // HUD 必须在表里：MixinGui 每帧都读它的 blur/shadow 参数，缺了会 NPE 并吃掉整个 RenderSkiaEvent。
        modules.add(new HUD());
        modules.add(new Sprint());
        modules.add(new FullBright());
        modules.add(new ArrayListModule());
        modules.add(new ClickGuiModule());
        modules.add(new Notification());
        Starain.getInstance().getEventManager().register(this);
    }

    // 如果直接去new一个对象这个zkm的indy没法混 直接是明文调用 所以要搞一个newInstance方法
    public static ModuleManager newInstance() {
        return new ModuleManager();
    }

    public List<ClientModule> getModules() {
        return new ArrayList<>(modules);
    }

    public List<ClientModule> getModules(Category category) {
        List<ClientModule> result = new ArrayList<>();
        for (ClientModule module : modules) {
            if (module.getCategory() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public ClientModule getModule(String name) {
        for (ClientModule module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public <T extends ClientModule> T getModule(Class<T> clazz) {
        for (ClientModule module : modules) {
            if (clazz.isInstance(module)) {
                return clazz.cast(module);
            }
        }
        return null;
    }

    @EventTarget
    public void onKeyboard(KeyboardEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getScreen() != null) {
            return;
        }
        toggleByBind(BindUtils.keyboardCode(event.getKey()));
    }

    @EventTarget
    public void onMouse(MouseButtonEvent event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getScreen() != null) {
            return;
        }
        toggleByBind(BindUtils.mouseCode(event.getButton()));
    }

    // key 为 0 表示未绑定，绝不能被任何按键匹配上。
    private void toggleByBind(int bindCode) {
        if (bindCode == 0) {
            return;
        }
        for (ClientModule module : modules) {
            if (module.getKey() != 0 && module.getKey() == bindCode) {
                module.toogle();
            }
        }
    }
}
