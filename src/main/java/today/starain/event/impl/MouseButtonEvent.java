package today.starain.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.screens.Screen;
import today.starain.event.api.Event;

@Getter
@AllArgsConstructor
public class MouseButtonEvent implements Event {
    private final int button;
    private final int action;
    private final int modifiers;
    private final Screen screen;
}
