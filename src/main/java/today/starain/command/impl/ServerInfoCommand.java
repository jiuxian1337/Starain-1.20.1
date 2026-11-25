package today.starain.command.impl;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import today.starain.command.Command;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.PacketEvent;
import today.starain.utils.client.ChatUtils;
import today.starain.utils.wrapper.Wrapper;

import java.util.Locale;

public final class ServerInfoCommand extends Command implements Wrapper {
    // 服务器每 20 tick 发一次时间包，用相邻两次的间隔估算 TPS
    private static final int SAMPLES = 10;
    private final float[] samples = new float[SAMPLES];
    private int sampleCount, sampleIndex;
    private long lastTimePacket;

    public ServerInfoCommand() {
        super("serverinfo", "si");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.INCOMING || !(event.getPacket() instanceof ClientboundSetTimePacket)) return;
        long now = System.nanoTime();
        synchronized (samples) {
            if (lastTimePacket != 0L) {
                float seconds = (now - lastTimePacket) / 1.0e9f;
                if (seconds > 0.05f) {
                    samples[sampleIndex] = Math.min(20.0f, 20.0f / seconds);
                    sampleIndex = (sampleIndex + 1) % SAMPLES;
                    sampleCount = Math.min(SAMPLES, sampleCount + 1);
                }
            }
            lastTimePacket = now;
        }
    }

    private float tps() {
        synchronized (samples) {
            if (sampleCount == 0) return -1.0f;
            float sum = 0.0f;
            for (int i = 0; i < sampleCount; i++) sum += samples[i];
            return sum / sampleCount;
        }
    }

    @Override
    public void onCommand(String[] args) {
        if (mc.player == null || mc.getConnection() == null) {
            ChatUtils.addChatMessage("Not connected");
            return;
        }
        ServerData server = mc.getCurrentServer();
        String ip = mc.isLocalServer() || server == null ? "Singleplayer" : server.ip;
        String version = server != null && server.version != null ? server.version.getString() : "Unknown";
        String brand = mc.player.getServerBrand() == null ? "Unknown" : mc.player.getServerBrand();
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        int ping = info == null ? 0 : info.getLatency();
        float tps = tps();

        ChatUtils.addChatMessage("Server Info");
        ChatUtils.addChatMessage(false, "  §7IP: §f", ip);
        ChatUtils.addChatMessage(false, "  §7Version: §f", version, " §7(", brand, ")");
        ChatUtils.addChatMessage(false, "  §7TPS: ", tps < 0 ? "§fcalculating..." : color(tps / 20.0f) + String.format(Locale.ROOT, "%.2f", tps));
        ChatUtils.addChatMessage(false, "  §7Ping: ", color(1.0f - Math.min(1.0f, ping / 300.0f)), ping + "ms");
        ChatUtils.addChatMessage(false, "  §7Players: §f", String.valueOf(mc.getConnection().getOnlinePlayers().size()));
    }

    private static String color(float quality) {
        return quality >= 0.9f ? "§a" : quality >= 0.6f ? "§e" : "§c";
    }
}
