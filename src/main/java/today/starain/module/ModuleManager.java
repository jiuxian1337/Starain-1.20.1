package today.starain.module;

import today.starain.module.impl.movement.Sprint;
import today.starain.module.impl.render.FullBright;
import today.starain.utils.wrapper.Wrapper;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager implements Wrapper {
    private final List<ClientModule> modules = new ArrayList<>();

    private ModuleManager() {
        modules.add(Sprint.newInstance());
        modules.add(FullBright.newInstance());
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
}
