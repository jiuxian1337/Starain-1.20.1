package today.starain.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import today.starain.module.impl.render.HandLightning;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Inject(method = "render", at = @At("RETURN"))
    private void starain$handLightning(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                       PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                                       BakedModel model, CallbackInfo ci) {
        HandLightning.onItemRendered(stack, context, leftHand, poseStack, buffer, model);
    }
}
