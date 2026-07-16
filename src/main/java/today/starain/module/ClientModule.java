package today.starain.module;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.sounds.SoundEvents;
import today.starain.Starain;
import today.starain.utils.wrapper.Wrapper;
import today.starain.value.Value;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
public class ClientModule implements Wrapper {
    private String name;
    private Category category;
    private int key;
    private boolean enabled;
    private final List<Value> values = new ArrayList<>();

    protected ClientModule() {

    }

    // 如果直接去new一个对象这个zkm的indy没法混 直接是明文调用 所以要搞一个newInstance方法
    public static ClientModule newInstance() {
        return new ClientModule();
    }

    protected void onEnable() {

    }

    protected void onDisable() {

    }

    public String getSuffix() {
        return null;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            if (enabled) {
                Starain.getInstance().getEventManager().register(this);
                mc.player.playSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.5F, 1.3F);
            } else {
                Starain.getInstance().getEventManager().unregister(this);
                mc.player.playSound(SoundEvents.WOODEN_BUTTON_CLICK_OFF, 0.5F, 0.8F);
            }
            this.enabled = enabled;
        }
    }

    public boolean toogle() {
        setEnabled(!enabled);
        return enabled;
    }

    public String getDisplayName() {
        String suffix = getSuffix();
        if (suffix == null || suffix.isEmpty()) {
            return name;
        } else {
            return name + " §7" + suffix;
        }
    }

}
