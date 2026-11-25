package today.starain.command.impl;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import today.starain.command.Command;
import today.starain.utils.client.ChatUtils;
import today.starain.utils.player.FriendManager;
import today.starain.utils.wrapper.Wrapper;

import java.util.List;

public final class FriendCommand extends Command implements Wrapper {
    public FriendCommand() {
        super("friend", "f");
    }

    @Override
    public void onCommand(String[] args) {
        if (args.length < 2) {
            usage();
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "add" -> {
                String name = args.length >= 3 ? args[2] : crosshairPlayer();
                if (name == null) {
                    ChatUtils.addChatMessage("usage: .friend add <name>");
                    return;
                }
                ChatUtils.addChatMessage(FriendManager.addFriend(name)
                        ? "Added §a" + name + "§r to friends"
                        : name + " is already a friend");
            }
            case "remove", "del" -> {
                String name = args.length >= 3 ? args[2] : crosshairPlayer();
                if (name == null) {
                    ChatUtils.addChatMessage("usage: .friend remove <name>");
                    return;
                }
                ChatUtils.addChatMessage(FriendManager.removeFriend(name)
                        ? "Removed §c" + name + "§r from friends"
                        : name + " is not a friend");
            }
            case "toggle" -> {
                String name = args.length >= 3 ? args[2] : crosshairPlayer();
                if (name == null) {
                    ChatUtils.addChatMessage("usage: .friend toggle <name>");
                    return;
                }
                if (FriendManager.isFriend(name)) {
                    FriendManager.removeFriend(name);
                    ChatUtils.addChatMessage("Removed §c" + name + "§r from friends");
                } else {
                    FriendManager.addFriend(name);
                    ChatUtils.addChatMessage("Added §a" + name + "§r to friends");
                }
            }
            case "list" -> {
                List<String> friends = FriendManager.getFriends();
                if (friends.isEmpty()) {
                    ChatUtils.addChatMessage("Friend list is empty");
                    return;
                }
                ChatUtils.addChatMessage("Friends (" + friends.size() + "):");
                for (String friend : friends) {
                    ChatUtils.addChatMessage(false, "  §a" + friend);
                }
            }
            case "clear" -> {
                int count = FriendManager.getFriends().size();
                FriendManager.clear();
                ChatUtils.addChatMessage("Cleared " + count + " friend(s)");
            }
            default -> usage();
        }
    }

    // 未给名字时取准星指向的玩家
    private String crosshairPlayer() {
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player player) {
            return player.getName().getString();
        }
        return null;
    }

    private void usage() {
        ChatUtils.addChatMessage("usage: .friend <add|remove|toggle|list|clear> [name]");
    }
}
