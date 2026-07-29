package today.starain.utils.render.shader.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import today.starain.utils.render.ProjectionUtils;
import today.starain.utils.vector.Vector2f;
import org.joml.Matrix4f;
import today.starain.utils.render.shader.Framebuffer;
import today.starain.utils.render.shader.PostProcessRenderer;
import today.starain.utils.render.shader.Shader;
import today.starain.utils.render.shader.ShaderHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_ONE;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;

public class OutlineGlow {

    public static final OutlineGlow INSTANCE = new OutlineGlow();

    private final MultiBufferSource.BufferSource maskBuffer = createMaskBuffer();

    private static MultiBufferSource.BufferSource createMaskBuffer() {
        Map<RenderType, BufferBuilder> fixed = new LinkedHashMap<>();
        put(fixed, RenderType.glint());
        put(fixed, RenderType.glintDirect());
        put(fixed, RenderType.glintTranslucent());
        put(fixed, RenderType.entityGlint());
        put(fixed, RenderType.entityGlintDirect());
        put(fixed, RenderType.armorGlint());
        put(fixed, RenderType.armorEntityGlint());
        return MultiBufferSource.immediateWithBuffers(fixed, new BufferBuilder(2048));
    }

    private static void put(Map<RenderType, BufferBuilder> map, RenderType renderType) {
        map.put(renderType, new BufferBuilder(renderType.bufferSize()));
    }

    private static void bobHurt(PoseStack poseStack, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.getCameraEntity() instanceof LivingEntity living)) {
            return;
        }

        float hurt = (float) living.hurtTime - partialTick;
        if (living.isDeadOrDying()) {
            float death = Math.min((float) living.deathTime + partialTick, 20.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (death + 200.0F)));
        }
        if (hurt < 0.0F) {
            return;
        }

        hurt /= (float) living.hurtDuration;
        hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
        float dir = living.getHurtDir();
        poseStack.mulPose(Axis.YP.rotationDegrees(-dir));
        float tilt = (float) ((double) (-hurt) * 14.0 * mc.options.damageTiltStrength().get());
        poseStack.mulPose(Axis.ZP.rotationDegrees(tilt));
        poseStack.mulPose(Axis.YP.rotationDegrees(dir));
    }

    private static void bobView(PoseStack poseStack, Player player, float partialTick) {
        float walked = player.walkDist - player.walkDistO;
        float phase = -(player.walkDist + walked * partialTick);
        float amount = Mth.lerp(partialTick, player.oBob, player.bob);
        poseStack.translate(Mth.sin(phase * (float) Math.PI) * amount * 0.5F,
                -Math.abs(Mth.cos(phase * (float) Math.PI) * amount), 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase * (float) Math.PI) * amount * 3.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(phase * (float) Math.PI - 0.2F) * amount) * 5.0F));
    }

    private static final int[] EMPTY_SCISSOR = new int[]{0, 0, 0, 0};

    private Shader shader;
    private Shader maskShader;
    private Framebuffer fbo;
    private int width;
    private int height;

    public void draw(PoseStack poseStack, float partialTick, List<? extends Entity> targets,
                     List<? extends BlockEntity> blockTargets,
                     float[] colors, int stops, float alpha, float radius, float strength, boolean antiAliasing,
                     boolean renderHand, boolean additive, boolean innerGlow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (targets.isEmpty() && blockTargets.isEmpty() && !renderHand)) {
            return;
        }

        if (shader == null) {
            shader = new Shader("glow.vert", "glow.frag");
        }

        prepareFbo(mc, antiAliasing);

        fbo.bind();
        fbo.setViewport();
        GlStateManager._clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager._clear(GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity player : targets) {
            try {
                double dx = Mth.lerp(partialTick, player.xo, player.getX()) - camera.x;
                double dy = Mth.lerp(partialTick, player.yo, player.getY()) - camera.y;
                double dz = Mth.lerp(partialTick, player.zo, player.getZ()) - camera.z;
                float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
                dispatcher.render(player, dx, dy, dz, yaw, partialTick, poseStack, maskBuffer, LightTexture.FULL_BRIGHT);
            } catch (Throwable ignored) {
            }
        }

        for (BlockEntity blockEntity : blockTargets) {
            try {
                BlockPos pos = blockEntity.getBlockPos();
                poseStack.pushPose();
                poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
                mc.getBlockEntityRenderDispatcher().render(blockEntity, partialTick, poseStack, maskBuffer);
                poseStack.popPose();
            } catch (Throwable ignored) {
            }
        }

        fbo.bind();
        maskBuffer.endBatch();
        dispatcher.setRenderShadow(true);

        if (renderHand && mc.player != null) {
            Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
            VertexSorting oldSorting = RenderSystem.getVertexSorting();
            double fov = mc.gameRenderer.getFov(mc.gameRenderer.getMainCamera(), partialTick, false);
            RenderSystem.setProjectionMatrix(mc.gameRenderer.getProjectionMatrix(fov), VertexSorting.DISTANCE_TO_ORIGIN);

            poseStack.pushPose();
            poseStack.setIdentity();
            bobHurt(poseStack, partialTick);
            if (mc.options.bobView().get()) {
                bobView(poseStack, mc.player, partialTick);
            }
            try {
                mc.gameRenderer.itemInHandRenderer.renderHandsWithItems(partialTick, poseStack, maskBuffer,
                        mc.player, LightTexture.FULL_BRIGHT);
            } catch (Throwable ignored) {
            }
            poseStack.popPose();

            fbo.bind();
            maskBuffer.endBatch();

            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        }

        applyGlowPass(mc, colors, stops, alpha, radius, strength, additive, innerGlow,
                computeScissor(mc, targets, blockTargets, partialTick, radius, renderHand));
    }

    public void drawRects(List<float[]> rects, float[] colors, int stops, float alpha,
                          float radius, float strength, boolean antiAliasing, boolean additive, boolean innerGlow) {
        Minecraft mc = Minecraft.getInstance();
        if (rects.isEmpty()) {
            return;
        }
        if (shader == null) {
            shader = new Shader("glow.vert", "glow.frag");
        }
        if (maskShader == null) {
            maskShader = new Shader("glow.vert", "glowmask.frag");
        }

        prepareFbo(mc, antiAliasing);

        fbo.bind();
        fbo.setViewport();
        GlStateManager._clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GlStateManager._clear(GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        int count = Math.min(rects.size(), 64);
        float[] data = new float[count * 6];
        for (int i = 0; i < count; i++) {
            float[] r = rects.get(i);
            data[i * 6] = r[0];
            data[i * 6 + 1] = r[1];
            data[i * 6 + 2] = r[2];
            data[i * 6 + 3] = r[3];
            data[i * 6 + 4] = r.length > 4 ? r[4] : 0.0F;
            data[i * 6 + 5] = r.length > 5 ? r[5] : 1.0F;
        }

        maskShader.bind();
        maskShader.set("uRects", data);
        maskShader.set("uRectCount", count);
        maskShader.set("uScreen", mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        PostProcessRenderer.beginRender();
        PostProcessRenderer.render();
        PostProcessRenderer.endRender();
        BufferUploader.reset();
        ShaderHelper.useProgram(0);

        applyGlowPass(mc, colors, stops, alpha, radius, strength, additive, innerGlow,
                rectScissor(mc, rects, radius));
    }

    private int[] rectScissor(Minecraft mc, List<float[]> rects, float radius) {
        double scale = mc.getWindow().getGuiScale();
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (float[] r : rects) {
            minX = Math.min(minX, r[0]);
            minY = Math.min(minY, r[1]);
            maxX = Math.max(maxX, r[0] + r[2]);
            maxY = Math.max(maxY, r[1] + r[3]);
        }
        return toScissor(mc, (float) (minX * scale), (float) (minY * scale),
                (float) (maxX * scale), (float) (maxY * scale), radius);
    }

    private int[] computeScissor(Minecraft mc, List<? extends Entity> targets,
                                 List<? extends BlockEntity> blockTargets,
                                 float partialTick, float radius, boolean renderHand) {
        if (renderHand) {
            return null;
        }
        double scale = mc.getWindow().getGuiScale();
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (Entity entity : targets) {
            if (!accumulate(entity.getBoundingBox().inflate(0.6), partialTick, bounds)) {
                return null;
            }
        }
        for (BlockEntity blockEntity : blockTargets) {
            BlockPos pos = blockEntity.getBlockPos();
            if (!accumulate(new AABB(pos).inflate(0.2), partialTick, bounds)) {
                return null;
            }
        }
        if (bounds[0] > bounds[2]) {
            return null;
        }
        return toScissor(mc, (float) (bounds[0] * scale), (float) (bounds[1] * scale),
                (float) (bounds[2] * scale), (float) (bounds[3] * scale), radius);
    }

    private boolean accumulate(AABB box, float partialTick, float[] bounds) {
        for (int i = 0; i < 8; i++) {
            double px = (i & 1) == 0 ? box.minX : box.maxX;
            double py = (i & 2) == 0 ? box.minY : box.maxY;
            double pz = (i & 4) == 0 ? box.minZ : box.maxZ;
            Vector2f p = ProjectionUtils.project(px, py, pz, partialTick);
            if (p.x == Float.MAX_VALUE || !Float.isFinite(p.x) || !Float.isFinite(p.y)) {
                return false;
            }
            bounds[0] = Math.min(bounds[0], p.x);
            bounds[1] = Math.min(bounds[1], p.y);
            bounds[2] = Math.max(bounds[2], p.x);
            bounds[3] = Math.max(bounds[3], p.y);
        }
        return true;
    }

    private int[] toScissor(Minecraft mc, float minX, float minY, float maxX, float maxY, float radius) {
        if (minX > maxX || minY > maxY) {
            return null;
        }
        int winW = mc.getWindow().getWidth();
        int winH = mc.getWindow().getHeight();
        float pad = radius * 2.0F + 8.0F;
        int x0 = (int) Math.floor(minX - pad);
        int y0 = (int) Math.floor(minY - pad);
        int x1 = (int) Math.ceil(maxX + pad);
        int y1 = (int) Math.ceil(maxY + pad);
        x0 = Math.max(0, Math.min(winW, x0));
        x1 = Math.max(0, Math.min(winW, x1));
        y0 = Math.max(0, Math.min(winH, y0));
        y1 = Math.max(0, Math.min(winH, y1));
        int w = x1 - x0;
        int h = y1 - y0;
        if (w <= 0 || h <= 0) {
            return EMPTY_SCISSOR;
        }
        if (w >= winW && h >= winH) {
            return null;
        }
        return new int[]{x0, winH - y1, w, h};
    }

    private void prepareFbo(Minecraft mc, boolean antiAliasing) {
        double multi = antiAliasing ? 2.0 : 1.0;
        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();
        if (fbo == null) {
            fbo = new Framebuffer(multi, true);
        } else if (fbo.sizeMulti != multi || windowWidth != width || windowHeight != height) {
            fbo.sizeMulti = multi;
            fbo.resize();
        }
        width = windowWidth;
        height = windowHeight;
    }

    private void applyGlowPass(Minecraft mc, float[] colors, int stops, float alpha,
                               float radius, float strength, boolean additive, boolean innerGlow,
                               int[] scissor) {
        if (scissor != null && (scissor[2] <= 0 || scissor[3] <= 0)) {
            mc.getMainRenderTarget().bindWrite(true);
            return;
        }
        mc.getMainRenderTarget().bindWrite(true);

        shader.bind();
        ShaderHelper.bindTexture(fbo.texture);
        shader.set("uMask", 0);
        shader.set("uTexelSize", 1.0 / fbo.width, 1.0 / fbo.height);
        shader.set("uColors", colors);
        shader.set("uCount", stops);
        shader.set("uAlpha", alpha);
        shader.set("uRadius", radius * fbo.sizeMulti);
        shader.set("uStrength", strength);
        shader.set("uInner", innerGlow ? 1.0 : 0.0);

        RenderSystem.enableBlend();
        GlStateManager._blendFunc(GL_SRC_ALPHA, additive ? GL_ONE : GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        if (scissor != null) {
            RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        }
        PostProcessRenderer.beginRender();
        PostProcessRenderer.render();
        PostProcessRenderer.endRender();
        if (scissor != null) {
            RenderSystem.disableScissor();
        }

        BufferUploader.reset();
        ShaderHelper.useProgram(0);
        ShaderHelper.resetTextureSlot();

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
