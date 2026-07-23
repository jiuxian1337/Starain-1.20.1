package today.starain.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import today.starain.Starain;
import today.starain.event.impl.RenderWorldEvent;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    // 与 NaCl 相同的注入点：GameRenderer.renderLevel 里读取 renderHand 字段(GETFIELD)前，
    // 此时 PoseStack 已带相机朝向、处于世界渲染坐标系，适合画 3D ESP。
    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z",
                    opcode = 180,
                    ordinal = 0
            )
    )
    private void starain$renderWorld(float partialTicks, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        if (Starain.getInstance() != null && Starain.getInstance().getEventManager() != null) {
            Starain.getInstance().getEventManager().call(new RenderWorldEvent(partialTicks, poseStack));
        }
    }
}
