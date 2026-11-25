package today.starain.command.impl;

import today.starain.command.Command;
import today.starain.utils.wrapper.Wrapper;

public final class ClearCommand extends Command implements Wrapper {
    public ClearCommand() {
        super("clear");
    }

    @Override
    public void onCommand(String[] args) {
        if (mc.gui == null) return;
        // false：保留已发送消息的上下键历史，只清空聊天栏显示
        mc.gui.getChat().clearMessages(false);
    }
}
