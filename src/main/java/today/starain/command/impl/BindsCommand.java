package today.starain.command.impl;

import today.starain.Starain;
import today.starain.command.Command;
import today.starain.command.CommandManager;
import today.starain.module.ClientModule;
import today.starain.utils.client.ChatUtils;
import today.starain.utils.render.BindUtils;

public final class BindsCommand extends Command {
    public BindsCommand() {
        super("binds", "keybinds");
    }

    @Override
    public void onCommand(String[] args) {
        int count = 0;
        ChatUtils.addChatMessage("Binds:");
        for (ClientModule module : Starain.getInstance().getModuleManager().getModules()) {
            if (module.getKey() == 0) continue;
            count++;
            ChatUtils.addChatMessage(false, "  §f", module.getName(), " §7-> §b", BindUtils.getBindName(module.getKey()),
                    module.isEnabled() ? " §a(on)" : "");
        }
        if (count == 0) {
            ChatUtils.addChatMessage(false, "  §7no modules are bound");
        }

        StringBuilder sb = new StringBuilder();
        for (Command command : Starain.getInstance().getCommandManager().getCommands()) {
            if (!sb.isEmpty()) sb.append("§7, ");
            sb.append("§f").append(CommandManager.PREFIX).append(command.getName());
            if (command.getAliases().length > 0) {
                sb.append(" §7(").append(String.join("/", command.getAliases())).append(")");
            }
        }
        ChatUtils.addChatMessage("Commands: ", sb.toString());
    }
}
