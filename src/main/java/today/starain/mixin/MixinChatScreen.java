package today.starain.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import today.starain.module.impl.client.HUD;

@Mixin(ChatScreen.class)
public class MixinChatScreen {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void starain$inputFill(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        if (HUD.isChatBackgroundEnabled()) {
            guiGraphics.fill(x1, y1, x2, y2, color);
        }
    }
}
