package today.starain.module.impl.player;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.MotionEvent;
import today.starain.event.impl.PlayerTickEvent;
import today.starain.event.impl.RespawnEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.utils.rotation.RayCastUtil;
import today.starain.utils.rotation.RotationManager;
import today.starain.utils.rotation.RotationUtils;
import today.starain.utils.timer.TimerUtils;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.NumberValue;

import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import today.starain.Starain;
import today.starain.module.impl.misc.GhostHand;
import today.starain.module.impl.combat.KillAura;

import net.minecraft.util.Mth;
import today.starain.utils.rotation.Rotation;

import java.util.HashSet;
import java.util.Set;

public class ChestAura extends ClientModule {
    // 最近一次由本模块打开的箱子，供 ChestStealer 的静默渲染定位
    public static BlockPos lastOpened;

    private final NumberValue range = new NumberValue("Range", 1.0, 6.0, 4.5, 0.1, this);
    private final NumberValue delay = new NumberValue("Delay", 0, 2000, 300, 10, this);
    private final BooleanValue rotate = new BooleanValue("Rotate", true, this);
    private final NumberValue rotationSpeed = new NumberValue("Rotation Speed", 1, 180, 180, 1, this, rotate::get);
    private final BooleanValue enderChest = new BooleanValue("Ender Chest", false, this);
    private final BooleanValue pauseKillAura = new BooleanValue("Pause On KillAura", true, this);
    private final BooleanValue swing = new BooleanValue("Swing", true, this);

    private final Set<BlockPos> opened = new HashSet<>();
    private final TimerUtils timer = new TimerUtils();
    private BlockPos pending;

    public ChestAura() {
        setName("ChestAura");
        setCategory(Category.PLAYER);
    }

    @Override
    protected void onDisable() {
        opened.clear();
        pending = null;
        super.onDisable();
    }

    @EventTarget
    public void onRespawn(RespawnEvent e) {
        opened.clear();
        pending = null;
    }

    @EventTarget
    public void onPlayerTick(PlayerTickEvent e) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.screen != null || ChestStealer.isWorking()) {
            pending = null;
            return;
        }
        if (pauseKillAura.get()) {
            KillAura aura = Starain.getInstance().getModuleManager().getModule(KillAura.class);
            if (aura != null && aura.isEnabled()) {
                pending = null;
                return;
            }
        }
        if (!timer.delay(delay.get().longValue())) return;

        double r = range.get().doubleValue();
        if (pending == null || !valid(pending, r)) pending = findTarget(r);
        if (pending == null) return;

        Vec3 center = Vec3.atCenterOf(pending);
        GhostHand ghost = Starain.getInstance().getModuleManager().getModule(GhostHand.class);
        boolean ghostHand = ghost != null && ghost.isEnabled();
        if (rotate.get()) {
            Rotation aim = RotationUtils.calculate(center);
            RotationManager.setRotations(aim, rotationSpeed.get().doubleValue());
            // GhostHand 隔墙开箱时射线会被墙挡住，改为判断视角是否已对准箱子方向
            if (ghostHand) {
                Rotation cur = RotationManager.rotations;
                if (cur == null || Math.abs(Mth.wrapDegrees(cur.getYaw() - aim.getYaw())) > 4.0f
                        || Math.abs(cur.getPitch() - aim.getPitch()) > 4.0f) return;
            } else
            // 等转头对准后再交互，避免服务端视角校验
            if (RotationManager.rotations == null || !RayCastUtil.overBlock(RotationManager.rotations, pending)) return;
        }

        BlockHitResult hit = new BlockHitResult(center, Direction.UP, pending, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (swing.get()) mc.player.swing(InteractionHand.MAIN_HAND);
        opened.add(pending);
        lastOpened = pending;
        pending = null;
        timer.reset();
    }

    private BlockPos findTarget(double r) {
        BlockPos origin = mc.player.blockPosition();
        int ir = (int) Math.ceil(r);
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int x = -ir; x <= ir; x++) {
            for (int y = -ir; y <= ir + 1; y++) {
                for (int z = -ir; z <= ir; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!valid(pos, r)) continue;
                    double d = mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                    if (d < bestDist) {
                        bestDist = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean valid(BlockPos pos, double r) {
        if (opened.contains(pos)) return false;
        BlockEntity be = mc.level.getBlockEntity(pos);
        boolean chest = be instanceof ChestBlockEntity || (enderChest.get() && be instanceof EnderChestBlockEntity);
        if (!chest || mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > r) return false;
        GhostHand ghost = Starain.getInstance().getModuleManager().getModule(GhostHand.class);
        if (ghost != null && ghost.isEnabled()) return true;
        BlockHitResult sight = mc.level.clip(new ClipContext(mc.player.getEyePosition(), Vec3.atCenterOf(pos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return sight.getType() == HitResult.Type.MISS || sight.getBlockPos().equals(pos);
    }
}
