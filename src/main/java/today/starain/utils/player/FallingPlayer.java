package today.starain.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class FallingPlayer {
    private static final Minecraft mc = Minecraft.getInstance();

    public double x, y, z;
    public double motionX, motionY, motionZ;
    public float yaw;

    public FallingPlayer(Player player) {
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.motionX = player.getDeltaMovement().x;
        this.motionY = player.getDeltaMovement().y;
        this.motionZ = player.getDeltaMovement().z;
        this.yaw = player.getYRot();
    }

    public FallingPlayer(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.motionX = motionX;
        this.motionY = motionY;
        this.motionZ = motionZ;
        this.yaw = yaw;
    }

    public void calculate(int ticks) {
        for (int i = 0; i < ticks; i++) {
            update();
        }
    }

    public void update() {
        motionY -= 0.08;
        motionY *= 0.98;
        motionX *= 0.91;
        motionZ *= 0.91;

        x += motionX;
        y += motionY;
        z += motionZ;
    }

    public Vec3 getPosition() {
        return new Vec3(x, y, z);
    }

    public double getDistanceTo(double x, double y, double z) {
        double dx = this.x - x;
        double dy = this.y - y;
        double dz = this.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean isCollidedHorizontally() {
        if (mc.level == null) return false;

        // 简单的碰撞检测
        return mc.level.getBlockState(net.minecraft.core.BlockPos.containing(x, y, z)).blocksMotion();
    }

    public boolean isCollidedVertically() {
        if (mc.level == null) return false;

        return mc.level.getBlockState(net.minecraft.core.BlockPos.containing(x, y - 0.1, z)).blocksMotion();
    }
}