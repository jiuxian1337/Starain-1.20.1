package today.starain.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import today.starain.module.impl.render.WorldColor;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {

    @Inject(method = "getShaderColor", at = @At("RETURN"), cancellable = true)
    private static void starain$worldColor(CallbackInfoReturnable<float[]> cir) {
        if (!WorldColor.shouldTintNow()) {
            return;
        }
        cir.setReturnValue(WorldColor.modulate(cir.getReturnValue()));
    }
}
