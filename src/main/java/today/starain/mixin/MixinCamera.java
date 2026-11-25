package today.starain.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import today.starain.module.impl.render.FreeLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

@Mixin(Camera.class)
public class MixinCamera {

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void starain$freeLook(Args args) {
        if (!FreeLook.isActive()) return;
        // 正面视角时原版会传入 yaw+180、-pitch，这里保持同样的偏移
        float yaw = args.get(0);
        float pitch = args.get(1);
        LocalPlayer player = Minecraft.getInstance().player;
        boolean front = player != null && Math.abs(Mth.wrapDegrees(yaw - player.getViewYRot(1.0f))) > 90.0f;
        args.set(0, front ? FreeLook.yaw() + 180.0f : FreeLook.yaw());
        args.set(1, front ? -FreeLook.pitch() : FreeLook.pitch());
    }
}
