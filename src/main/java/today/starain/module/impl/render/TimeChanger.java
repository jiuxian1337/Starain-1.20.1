package today.starain.module.impl.render;

import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

/**
 * 客户端改世界时间（仅本地渲染，服务端不受影响）。每 tick 把 ClientLevel 的 dayTime 覆盖成目标值，
 * 压过服务端周期性下发的 SetTime 包。预设用常见时刻，Custom 用滑条自定义 0~24000。
 */
public final class TimeChanger extends ClientModule {
    private final ModeValue mode = new ModeValue("Mode", "Day",
            new String[]{"Custom", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight"}, this);
    private final NumberValue time = new NumberValue("Time", 0.0, 24000.0, 1000.0, 100.0, this,
            () -> mode.isMode("Custom"));

    public TimeChanger() {
        setName("TimeChanger");
        setCategory(Category.RENDER);
    }

    @EventTarget
    public void onGameTick(GameTickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (mc.level == null) {
            return;
        }
        mc.level.setDayTime(targetTime());
    }

    private long targetTime() {
        return switch (mode.get()) {
            case "Sunrise" -> 0L;
            case "Day" -> 1000L;
            case "Noon" -> 6000L;
            case "Sunset" -> 12000L;
            case "Night" -> 13000L;
            case "Midnight" -> 18000L;
            default -> time.get().longValue();
        };
    }

    @Override
    public String getSuffix() {
        return mode.get();
    }
}
