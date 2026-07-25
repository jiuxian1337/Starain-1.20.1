package today.starain.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import today.starain.module.impl.client.HUD;
import today.starain.utils.client.ChatFlow;

@Mixin(ChatComponent.class)
public class MixinChatComponent {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V")
    )
    private void starain$chatFill(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color) {
        if (HUD.isChatBackgroundEnabled() || Math.abs(x2 - x1) <= 8 || Math.abs(y2 - y1) < 8) {
            guiGraphics.fill(x1, y1, x2, y2, color);
        }
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I")
    )
    private int starain$flowPrefix(net.minecraft.client.gui.GuiGraphics guiGraphics, net.minecraft.client.gui.Font font,
                                   FormattedCharSequence text, int x, int y, int color) {
        return guiGraphics.drawString(font, ChatFlow.recolor(text), x, y, color);
    }
}
