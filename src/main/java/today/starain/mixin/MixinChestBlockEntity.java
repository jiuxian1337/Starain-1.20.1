package today.starain.mixin;

import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import today.starain.module.impl.player.ChestStealer;

@Mixin(ChestBlockEntity.class)
public class MixinChestBlockEntity {

    // ChestStealer 静默模式：被偷的箱子保持合盖，去掉开箱动画
    @Inject(method = "getOpenNess", at = @At("HEAD"), cancellable = true)
    private void starain$silentLid(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (ChestStealer.hideLid(((ChestBlockEntity) (Object) this).getBlockPos())) {
            cir.setReturnValue(0.0f);
        }
    }
}