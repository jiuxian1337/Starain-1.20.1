/*
package today.starain.module.impl.combat;

import today.starain.event.api.EventType;
import today.starain.event.api.Priority;
import today.starain.event.api.annotations.EventPriority;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.event.impl.MotionEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;

public class Criticals extends ClientModule {
    public static int fallTicks;
    public static boolean stopSprint;

    public Criticals() {
        setName("Criticals");
        setCategory(Category.COMBAT);
    }

    @EventPriority(Priority.HIGH)
    @EventTarget
    public void onGameTick(GameTickEvent event) {
        if (mc.player == null || event.getType() != EventType.PRE) return;
        if (mc.player.fallDistance <= 0 || mc.player.onGround()) {
            fallTicks = 0;
        } else {
            fallTicks++;
            if (KillAura.targets != null && !KillAura.targets.isEmpty() && Velocity.attackQueue == 0) {
                mc.options.keySprint.setDown(false);
                mc.player.setSprinting(false);
                stopSprint = true;
            }
        }
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getType() != EventType.PRE || !stopSprint) return;
        stopSprint = false;
        mc.player.setSprinting(false);
        mc.options.keySprint.setDown(false);
    }

    @Override
    public void onDisable() {
        fallTicks = 0;
        stopSprint = false;
    }
}
*/
