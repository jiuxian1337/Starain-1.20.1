package today.starain.module.impl.movement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.MotionEvent;
import today.starain.event.impl.MoveInputEvent;
import today.starain.event.impl.PacketEvent;
import today.starain.event.impl.SlowdownEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.utils.client.NetworkUtils;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ModeValue;

import java.util.concurrent.LinkedBlockingQueue;

public class NoSlow extends ClientModule {
    private final ModeValue mode = new ModeValue("Mode", "Vanilla", new String[]{"Vanilla", "Jump", "GrimFull", "Grim1/2", "Grim1/3"}, this);
    private final BooleanValue food = new BooleanValue("Food", true, this);
    private final BooleanValue bow = new BooleanValue("Bow", true, this);
    private final BooleanValue crossbow = new BooleanValue("Crossbow", true, this);

    private int ticks;
    private int useDuration = 32;
    private int onGroundTick = 0;
    private boolean eating;
    private final LinkedBlockingQueue<Packet<ClientGamePacketListener>> packets = new LinkedBlockingQueue<>();

    public NoSlow() {
        setName("NoSlow");
        setCategory(Category.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        flush();
        eating = false;
        ticks = 0;
        useDuration = 32;
    }

    @EventTarget
    private void onMotion(MotionEvent event) {
        if (event.getType() != EventType.PRE || mc.player == null) return;

        if (mc.player.onGround()) {
            onGroundTick++;
        } else {
            onGroundTick = 0;
        }

        if (!mode.isMode("GrimFull") || mc.player.tickCount < 30) return;

        if (eating) ticks++;

        UseAnim anim = mc.player.getUseItem().getUseAnimation();
        if (mc.player.isUsingItem() && (anim == UseAnim.EAT || anim == UseAnim.DRINK || anim == UseAnim.CROSSBOW) && !eating) {
            eating = true;
            ticks = 0;
            ItemStack useItem = mc.player.getUseItem();
            useDuration = useItem.getItem().getUseDuration(useItem);
        }

        if (eating) {
            if (ticks == 1) {
                NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
            }
            if (ticks == 2) {
                InteractionHand hand = mc.player.getUsedItemHand() == InteractionHand.OFF_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                mc.getConnection().send(new ServerboundUseItemPacket(hand, mc.level.getBlockStatePredictionHandler().startPredicting().currentSequence()));
            }
            if (ticks > useDuration + 3) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
                mc.options.keyUse.setDown(false);
                ticks = 0;
                eating = false;
            }
        }
    }

    @EventTarget
    private void onPacket(PacketEvent event) {
        if (mc.player == null || mc.player.tickCount < 30 || !eating || !mode.isMode("GrimFull")) return;

        Packet<?> packet = event.getPacket();

        if (event.getType() == EventType.INCOMING) {
            if (packet instanceof ClientboundPlayerPositionPacket) {
                flush();
                return;
            }
            if (packet instanceof ClientboundSetHealthPacket
                    || packet instanceof ClientboundSystemChatPacket
                    || packet instanceof ClientboundMoveEntityPacket
                    || packet instanceof ClientboundTeleportEntityPacket
                    || packet instanceof ClientboundEntityEventPacket
                    || packet instanceof ClientboundAddEntityPacket
                    || packet instanceof ClientboundBlockUpdatePacket
                    || packet instanceof ClientboundBlockEventPacket) {
                return;
            }
            event.setCancelled(true);
            packets.add((Packet<ClientGamePacketListener>) packet);
        }

        if (event.getType() == EventType.OUTGOING && packet instanceof ServerboundPlayerActionPacket actionPacket && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            eating = false;
            ticks = 0;
            flush();
            NetworkUtils.sendPacketNoEvent(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        }
    }

    @EventTarget
    private void onSlowdown(SlowdownEvent event) {
        if (mc.player.tickCount < 30) return;

        if (!food.get() && mc.player.getUseItem().getItem().isEdible()) return;
        if (!bow.get() && mc.player.getUseItem().is(Items.BOW)) return;
        if (!crossbow.get() && mc.player.getUseItem().is(Items.CROSSBOW)) return;

        switch (mode.get()) {
            case "Vanilla" -> event.setCancelled(true);
            case "GrimFull" -> grimFull(event);
            case "Jump" -> jump(event);
            case "Grim1/2" -> grim50(event);
            case "Grim1/3" -> grim33(event);
        }
    }

    @EventTarget
    private void onMoveInput(MoveInputEvent event) {
        if (mode.isMode("Jump") && mc.player.onGround() && mc.player.isUsingItem() && (event.getForward() != 0 || event.getStrafe() != 0)) {
            event.setJump(true);
        }
    }

    private void grimFull(SlowdownEvent event) {
        UseAnim anim = mc.player.getUseItem().getUseAnimation();
        if (mc.player.isUsingItem() && mc.player.getUseItemRemainingTicks() < 30 && (anim == UseAnim.EAT || anim == UseAnim.DRINK)) {
            event.setCancelled(true);
            mc.player.setSprinting(true);
        }
    }

    private void jump(SlowdownEvent event) {
        if (onGroundTick == 1 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setCancelled(true);
        }
    }

    private void grim50(SlowdownEvent event) {
        if (mc.player.getUseItemRemainingTicks() % 2 == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setCancelled(true);
        }
    }

    private void grim33(SlowdownEvent event) {
        if (mc.player.getUseItemRemainingTicks() % 3 == 0 && mc.player.getUseItemRemainingTicks() <= 30) {
            event.setCancelled(true);
        }
    }

    private void flush() {
        Packet<ClientGamePacketListener> packet;
        while ((packet = packets.poll()) != null) {
            packet.handle(mc.getConnection());
        }
    }
}
