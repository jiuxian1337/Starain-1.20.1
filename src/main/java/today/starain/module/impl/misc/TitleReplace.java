package today.starain.module.impl.misc;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.PacketEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;

public class TitleReplace extends ClientModule {

    private static final String FROM = "夏冲Plus";
    private static final String TO = "Starain User";

    public TitleReplace() {
        setName("TitleReplace");
        setCategory(Category.MISC);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.INCOMING) return;

        if (event.getPacket() instanceof ClientboundSetTitleTextPacket packet) {
            event.setPacket(new ClientboundSetTitleTextPacket(replace(packet.getText())));
        } else if (event.getPacket() instanceof ClientboundSetSubtitleTextPacket packet) {
            event.setPacket(new ClientboundSetSubtitleTextPacket(replace(packet.getText())));
        } else if (event.getPacket() instanceof ClientboundSetActionBarTextPacket packet) {
            event.setPacket(new ClientboundSetActionBarTextPacket(replace(packet.getText())));
        }
    }

    private Component replace(Component component) {
        if (component == null) return null;

        ComponentContents contents = component.getContents();
        MutableComponent result;
        if (contents instanceof LiteralContents literal) {
            result = Component.literal(literal.text().replace(FROM, TO));
        } else {
            result = MutableComponent.create(contents);
        }
        result.setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) {
            result.append(replace(sibling));
        }
        return result;
    }
}
