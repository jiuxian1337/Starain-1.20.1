package today.starain.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.client.gui.screens.Screen;
import today.starain.event.api.Event;

@Getter
@AllArgsConstructor
public class KeyboardEvent implements Event {
    private final int key;
    private final int scancode;
    private final int action;
    private final int modifiers;
    // 事件发出时打开着的界面，null 表示在游戏里。用来避免在聊天框/背包里打字时触发绑定。
    private final Screen screen;
}
