package today.starain.module.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import today.starain.Starain;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.event.impl.RenderWorldEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.module.impl.combat.KillAura;
import today.starain.ui.Theme;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;

public class TargetESP extends ClientModule {
    private static final int SEGMENTS = 64;

    private final BooleanValue syncColor = new BooleanValue("Sync Color", true, this);
    private final ColorValue color = new ColorValue("Color", new Color(255, 255, 255, 233), this, () -> !syncColor.get());
    private final NumberValue radius = new NumberValue("Radius", 0.1, 2.0, 0.75, 0.05, this);
    private final NumberValue alphaFactor = new NumberValue("Alpha Factor", 0.0, 2.0, 1.0, 0.05, this);
    private final BooleanValue through = new BooleanValue("Through Walls", true, this);
    private final ModeValue mode = new ModeValue("Mode", "Jello", new String[]{"Jello", "Circle", "Box", "Orbit", "Helix"}, this);
    private final BooleanValue attackedOnly = new BooleanValue("Attacked Only", false, this);
    private final NumberValue attackTimeout = new NumberValue("Attack Timeout", 5, 100, 40, 1, this, attackedOnly::get);

    private LivingEntity target;

    public TargetESP() {
        setName("TargetESP");
        setCategory(Category.RENDER);
    }

    @Override
    public void onDisable() {
        target = null;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (event.getType() != EventType.PRE) return;
        if (mc.player == null || mc.level == null) {
            target = null;
            return;
        }

        if (attackedOnly.get()) {
            // Player.attack 会在客户端调用 setLastHurtMob，用它判断“被本地玩家攻击”
            LivingEntity hurt = mc.player.getLastHurtMob();
            boolean recent = mc.player.tickCount - mc.player.getLastHurtMobTimestamp() <= attackTimeout.get().intValue();
            target = hurt != null && hurt != mc.player && hurt.isAlive() && recent ? hurt : null;
            return;
        }

        if (mc.hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof LivingEntity living
                && living != mc.player) {
            target = living;
            return;
        }

        KillAura killAura = Starain.getInstance().getModuleManager().getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled()) {
            Entity auraTarget = KillAura.getTarget();
            if (auraTarget instanceof LivingEntity living && living != mc.player) {
                target = living;
                return;
            }
        }

        target = null;
    }

    @EventTarget
    public void onRenderWorld(RenderWorldEvent event) {
        LivingEntity entity = target;
        if (entity == null || mc.player == null || !entity.isAlive()) return;
        if (!mode.isMode("Jello")) {
            renderStyle(event, entity);
            return;
        }

        float partialTick = event.getPartialTick();
        float ticks = (float) (System.currentTimeMillis() % 1000000L) * 0.004f;
        float pulse = 0.35f + 0.65f * ((Mth.sin(ticks * 1.8f) + 1.0f) * 0.5f) * alphaFactor.get().floatValue();
        float ringRadius = radius.get().floatValue();
        float skirtBottom = (float) (-Math.sin(ticks + 1.0f) / 2.7f);

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - camera.x;
        double y = Mth.lerp(partialTick, entity.yo, entity.getY()) - camera.y + Math.sin(ticks) + 1.0;
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - camera.z;

        boolean depthTest = !through.get() && mc.player.hasLineOfSight(entity);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = (float) (Math.PI * 2.0 * i / SEGMENTS);
            float vx = (float) (ringRadius * Math.cos(angle));
            float vz = (float) (ringRadius * Math.sin(angle));
            Color segment = segmentColor(i);
            float r = segment.getRed() / 255.0f;
            float g = segment.getGreen() / 255.0f;
            float b = segment.getBlue() / 255.0f;
            float a = (segment.getAlpha() / 255.0f) * 0.52f * pulse;
            buffer.vertex(matrix, vx, skirtBottom, vz).color(r, g, b, 0.0f).endVertex();
            buffer.vertex(matrix, vx, 0.0f, vz).color(r, g, b, a).endVertex();
        }
        tesselator.end();

        RenderSystem.lineWidth(2.0f);
        buffer.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            float angle = (float) (Math.PI * 2.0 * i / SEGMENTS);
            float vx = (float) (ringRadius * Math.cos(angle));
            float vz = (float) (ringRadius * Math.sin(angle));
            Color segment = segmentColor(i);
            buffer.vertex(matrix, vx, 0.0f, vz)
                    .color(segment.getRed() / 255.0f, segment.getGreen() / 255.0f, segment.getBlue() / 255.0f,
                            (segment.getAlpha() / 255.0f) * pulse)
                    .endVertex();
        }
        tesselator.end();
        RenderSystem.lineWidth(1.0f);

        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderStyle(RenderWorldEvent event, LivingEntity entity) {
        float partialTick = event.getPartialTick();
        float time = (float) (System.currentTimeMillis() % 1000000L) * 0.001f;
        float alpha = Mth.clamp(0.5f + 0.5f * alphaFactor.get().floatValue(), 0.0f, 1.0f);
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - camera.x;
        double y = Mth.lerp(partialTick, entity.yo, entity.getY()) - camera.y;
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - camera.z;
        float height = entity.getBbHeight();
        float r = radius.get().floatValue();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        Matrix4f m = poseStack.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        if (!through.get() && mc.player.hasLineOfSight(entity)) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0f);
        Tesselator t = Tesselator.getInstance();
        BufferBuilder b = t.getBuilder();

        switch (mode.get()) {
            case "Circle" -> {
                // 脚下呼吸圆环 + 内部渐隐填充
                float rr = r * (0.9f + 0.1f * Mth.sin(time * 4.0f));
                b.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                for (int i = 0; i <= SEGMENTS; i++) {
                    float ang = (float) (Math.PI * 2.0 * i / SEGMENTS);
                    Color c = segmentColor(i);
                    float cx = Mth.cos(ang), cz = Mth.sin(ang);
                    vertex(b, m, cx * rr * 0.6f, 0.02f, cz * rr * 0.6f, c, 0.0f);
                    vertex(b, m, cx * rr, 0.02f, cz * rr, c, 0.35f * alpha);
                }
                t.end();
                b.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                for (int i = 0; i <= SEGMENTS; i++) {
                    float ang = (float) (Math.PI * 2.0 * i / SEGMENTS);
                    vertex(b, m, Mth.cos(ang) * rr, 0.02f, Mth.sin(ang) * rr, segmentColor(i), alpha);
                }
                t.end();
            }
            case "Box" -> {
                float w = entity.getBbWidth() / 2.0f + 0.05f;
                float h = height + 0.1f;
                Color c = segmentColor(0);
                float fa = 0.18f * alpha * (0.7f + 0.3f * Mth.sin(time * 5.0f));
                b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                float[][] faces = {
                        {-w, 0, -w, w, 0, -w, w, h, -w, -w, h, -w}, {-w, 0, w, w, 0, w, w, h, w, -w, h, w},
                        {-w, 0, -w, -w, 0, w, -w, h, w, -w, h, -w}, {w, 0, -w, w, 0, w, w, h, w, w, h, -w},
                        {-w, h, -w, w, h, -w, w, h, w, -w, h, w}, {-w, 0, -w, w, 0, -w, w, 0, w, -w, 0, w}};
                for (float[] f : faces) for (int i = 0; i < 12; i += 3) vertex(b, m, f[i], f[i + 1], f[i + 2], c, fa);
                t.end();
                b.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
                float[] xs = {-w, w, w, -w};
                float[] zs = {-w, -w, w, w};
                for (int i = 0; i < 4; i++) {
                    int n = (i + 1) % 4;
                    Color ci = segmentColor(i * 8), cn = segmentColor(n * 8);
                    vertex(b, m, xs[i], 0, zs[i], ci, alpha); vertex(b, m, xs[n], 0, zs[n], cn, alpha);
                    vertex(b, m, xs[i], h, zs[i], ci, alpha); vertex(b, m, xs[n], h, zs[n], cn, alpha);
                    vertex(b, m, xs[i], 0, zs[i], ci, alpha); vertex(b, m, xs[i], h, zs[i], ci, alpha);
                }
                t.end();
            }
            case "Orbit" -> {
                // 三条倾斜轨道上的拖尾光点
                for (int ring = 0; ring < 3; ring++) {
                    float tilt = ring * (float) (Math.PI / 3.0);
                    float speed = 2.2f + ring * 0.6f;
                    b.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                    int trail = 24;
                    for (int i = 0; i <= trail; i++) {
                        float ang = time * speed - i * 0.08f + ring * 2.1f;
                        float ox = Mth.cos(ang) * r, oz = Mth.sin(ang) * r;
                        float oy = height / 2.0f + Mth.sin(ang) * Mth.sin(tilt) * r * 0.8f;
                        float rx = ox * Mth.cos(tilt) - oz * Mth.sin(tilt) * 0.2f;
                        vertex(b, m, rx, oy, oz, segmentColor(ring * 10 + i), alpha * (1.0f - (float) i / trail));
                    }
                    t.end();
                }
            }
            case "Helix" -> {
                // 双螺旋缠绕上升
                for (int strand = 0; strand < 2; strand++) {
                    b.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
                    int steps = 48;
                    for (int i = 0; i <= steps; i++) {
                        float p = (float) i / steps;
                        float ang = p * (float) (Math.PI * 4.0) + time * 3.0f + strand * (float) Math.PI;
                        float fade = Mth.sin(p * (float) Math.PI);
                        vertex(b, m, Mth.cos(ang) * r, p * height, Mth.sin(ang) * r, segmentColor(i), alpha * fade);
                    }
                    t.end();
                }
            }
        }

        RenderSystem.lineWidth(1.0f);
        poseStack.popPose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder b, Matrix4f m, float x, float y, float z, Color c, float alpha) {
        b.vertex(m, x, y, z).color(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f,
                (c.getAlpha() / 255.0f) * alpha).endVertex();
    }

    private Color segmentColor(int index) {
        return syncColor.get() ? Theme.accent(-index) : color.get();
    }
}

