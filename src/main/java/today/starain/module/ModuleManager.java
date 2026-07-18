package today.starain.module;

import org.lwjgl.glfw.GLFW;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.KeyboardEvent;
import today.starain.event.impl.MouseButtonEvent;
import today.starain.module.impl.client.*;
import today.starain.module.impl.combat.Criticals;
import today.starain.module.impl.combat.KillAura;
import today.starain.module.impl.combat.Velocity;
import today.starain.module.impl.misc.AntiBot;
import today.starain.module.impl.misc.HealthBypass;
import today.starain.module.impl.misc.Target;
import today.starain.module.impl.misc.Teams;
import today.starain.module.impl.movement.NoJumpDelay;
import today.starain.module.impl.movement.FastWeb;
import today.starain.module.impl.movement.Sprint;
import today.starain.module.impl.player.*;
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
        modules.add(new ScoreboardModule());
        modules.add(new Criticals());
        modules.add(new KillAura());
        modules.add(new Velocity());
        modules.add(new AntiBot());
        modules.add(new Target());
        modules.add(new Teams());
        modules.add(new Scaffold());
        modules.add(new NoJumpDelay());
        modules.add(new FastWeb());
        modules.add(new AutoTools());
        modules.add(new ChestStealer());
        modules.add(new InvManager());
        modules.add(new TargetHUD());
        modules.add(new NoFall());
        modules.add(new HealthBypass());
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
        if (event.action() != GLFW.GLFW_PRESS || event.screen() != null) {
            return;
        }
        toggleByBind(BindUtils.keyboardCode(event.key()));
    }

    @EventTarget
    public void onMouse(MouseButtonEvent event) {
        if (event.action() != GLFW.GLFW_PRESS || event.screen() != null) {
            return;
        }
        toggleByBind(BindUtils.mouseCode(event.button()));
    }

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
