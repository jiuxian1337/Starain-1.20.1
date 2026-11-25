package today.starain.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import today.starain.module.impl.player.ChestStealer;

@Mixin(ContainerScreen.class)
public class MixinContainerScreen {

    // ChestStealer 静默模式：不画原版箱子界面和背景遮罩，改画毛玻璃面板
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void starain$silentRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ChestStealer.renderSilent((ContainerScreen) (Object) this, guiGraphics, partialTick)) {
            ci.cancel();
        }
    }
}