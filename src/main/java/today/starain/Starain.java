package today.starain;

import lombok.Getter;
import today.starain.event.api.EventManager;
import today.starain.module.ModuleManager;

@Getter
public class Starain {
    public static final String NAME = "Starain";
    public static final String VERSION = "260717";
    @Getter
    private static Starain instance;

    // 防止在初始化模块的时候噔一声
    @Getter
    private static boolean canPlaySound = false;

    private EventManager eventManager;
    private ModuleManager moduleManager;

    private Starain() {
        instance = this;
        init();
    }

    // 如果直接去new一个对象这个zkm的indy没法混 直接是明文调用 所以要搞一个newInstance方法
    public static Starain newInstance() {
        return new Starain();
    }

    private void init() {
        eventManager = EventManager.netInstance();
        moduleManager = ModuleManager.newInstance();
    }

}
