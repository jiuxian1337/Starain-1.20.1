package today.starain.command.impl;

import today.starain.command.Command;
import today.starain.utils.client.ChatUtils;
import today.starain.utils.wrapper.Wrapper;

public final class SayCommand extends Command implements Wrapper {
    public SayCommand() {
        super("say");
    }

    /** 由 CommandManager 在 ChatEvent 中直接改写消息内容，从而绕过命令前缀拦截原样发送到服务器。 */
    public static String extract(String message) {
        String trimmed = message.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? "" : trimmed.substring(space + 1).trim();
    }

    @Override
    public void onCommand(String[] args) {
        ChatUtils.addChatMessage("usage: .say <message>");
    }
}
