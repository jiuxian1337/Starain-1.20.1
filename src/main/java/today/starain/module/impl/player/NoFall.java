package today.starain.module.impl.player;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import today.starain.Starain;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.MotionEvent;
import today.starain.event.impl.MoveInputEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.utils.client.NetworkUtils;
import today.starain.value.impl.NumberValue;

public class NoFall extends ClientModule {
    private final NumberValue fallDistance = new NumberValue("FallDistance", 3.0, 15.0, 3.0, 0.1, this);
    private float grimLastFallDistance;
    private boolean grimQueuedJump;
    public NoFall() {
        setName("NoFall");
        setCategory(Category.PLAYER);
    }

    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getType() != EventType.PRE) return;

        float fallDistance1 = mc.player.fallDistance;

        if (grimLastFallDistance >= fallDistance.get().floatValue() && event.isOnGround()) {
            event.setOnGround(false);
            NetworkUtils.sendPacketNoEvent(new ServerboundMovePlayerPacket.StatusOnly(true));
            Starain.skipTicks++;
            grimQueuedJump = true;
        }

        grimLastFallDistance = fallDistance1;
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {

        if (grimLastFallDistance >= fallDistance.get().floatValue() && mc.player.onGround()) {
            event.setSneak(false);
        }

        if (grimQueuedJump) {
            event.setJump(true);
            event.setForward(0);
            event.setStrafe(0);
            grimQueuedJump = false;
        }
    }
}
