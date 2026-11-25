package today.starain.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import today.starain.Starain;
import today.starain.event.impl.MouseButtonEvent;

@Mixin(MouseHandler.class)
public abstract class MixinMouseHandler {
    @Shadow
    @Final
    private Minecraft minecraft;

    @org.spongepowered.asm.mixin.injection.Redirect(method = "onScroll", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;swapPaint(D)V"))
    private void starain$zoomScroll(net.minecraft.world.entity.player.Inventory inventory, double amount) {
        if (!today.starain.module.impl.render.Zoom.onScroll(amount)) {
            inventory.swapPaint(amount);
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "turnPlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void starain$freeLook(net.minecraft.client.player.LocalPlayer player, double dx, double dy) {
        if (today.starain.module.impl.render.FreeLook.isActive()) {
            today.starain.module.impl.render.FreeLook.turn(dx, dy);
        } else {
            player.turn(dx, dy);
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"))
    private void starain$onPress(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (Starain.getInstance() == null || Starain.getInstance().getEventManager() == null) {
            return;
        }

        if (window != minecraft.getWindow().getWindow()) {
            return;
        }

        Starain.getInstance().getEventManager().call(
                new MouseButtonEvent(button, action, modifiers, minecraft.screen));
    }
}
