package today.starain.module;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.sounds.SoundEvent;
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
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;
        if (enabled) {
            Starain.getInstance().getEventManager().register(this);
            playToggleSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.3F);
            onEnable();
        } else {
            Starain.getInstance().getEventManager().unregister(this);
            playToggleSound(SoundEvents.WOODEN_BUTTON_CLICK_OFF, 0.8F);
            onDisable();
        }
    }

    // 读配置时会在 Minecraft 构造阶段调 setEnabled，那时 mc.player 还是 null。
    private void playToggleSound(SoundEvent sound, float pitch) {
        if (Starain.isCanPlaySound() && mc.player != null) {
            mc.player.playSound(sound, 0.5F, pitch);
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
