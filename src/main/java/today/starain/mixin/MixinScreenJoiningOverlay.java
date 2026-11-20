package today.starain.mixin;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({JoinMultiplayerScreen.class, ConnectScreen.class, ReceivingLevelScreen.class})
public abstract class MixinScreenJoiningOverlay {
}
