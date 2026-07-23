package today.starain.utils.render.shader.impl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import today.starain.utils.render.shader.Framebuffer;
import today.starain.utils.render.shader.PostProcessRenderer;
import today.starain.utils.render.shader.Shader;
import today.starain.utils.render.shader.ShaderHelper;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11C.GL_ONE;
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

    private static void bobView(PoseStack poseStack, Player player, float partialTick) {
        float walked = player.walkDist - player.walkDistO;
        float phase = -(player.walkDist + walked * partialTick);
        float amount = Mth.lerp(partialTick, player.oBob, player.bob);
        poseStack.translate(Mth.sin(phase * (float) Math.PI) * amount * 0.5F,
                -Math.abs(Mth.cos(phase * (float) Math.PI) * amount), 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(phase * (float) Math.PI) * amount * 3.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(phase * (float) Math.PI - 0.2F) * amount) * 5.0F));
    }

    private Shader shader;
    private Framebuffer fbo;
    private int width;
    private int height;

    public void draw(PoseStack poseStack, float partialTick, List<Player> targets,
                     Color colorA, Color colorB, float radius, float strength, boolean antiAliasing,
                     boolean renderHand) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (targets.isEmpty() && !renderHand)) {
            return;
        }

        if (shader == null) {
            shader = new Shader("glow.vert", "glow.frag");
        }

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
        for (Player player : targets) {
            try {
                double dx = Mth.lerp(partialTick, player.xo, player.getX()) - camera.x;
                double dy = Mth.lerp(partialTick, player.yo, player.getY()) - camera.y;
                double dz = Mth.lerp(partialTick, player.zo, player.getZ()) - camera.z;
                float yaw = Mth.lerp(partialTick, player.yRotO, player.getYRot());
                dispatcher.render(player, dx, dy, dz, yaw, partialTick, poseStack, maskBuffer, LightTexture.FULL_BRIGHT);
            } catch (Throwable ignored) {
            }
        }

        if (renderHand && mc.player != null) {
            Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
            VertexSorting oldSorting = RenderSystem.getVertexSorting();
            double fov = mc.gameRenderer.getFov(mc.gameRenderer.getMainCamera(), partialTick, false);
            RenderSystem.setProjectionMatrix(mc.gameRenderer.getProjectionMatrix(fov), VertexSorting.DISTANCE_TO_ORIGIN);

            poseStack.pushPose();
            poseStack.setIdentity();
            if (mc.options.bobView().get()) {
                bobView(poseStack, mc.player, partialTick);
            }
            try {
                mc.gameRenderer.itemInHandRenderer.renderHandsWithItems(partialTick, poseStack, maskBuffer,
                        mc.player, LightTexture.FULL_BRIGHT);
            } catch (Throwable ignored) {
            }
            poseStack.popPose();

            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        }

        fbo.bind();
        maskBuffer.endBatch();
        dispatcher.setRenderShadow(true);

        mc.getMainRenderTarget().bindWrite(true);

        shader.bind();
        ShaderHelper.bindTexture(fbo.texture);
        shader.set("uMask", 0);
        shader.set("uTexelSize", 1.0 / fbo.width, 1.0 / fbo.height);
        shader.set("uColorA", colorA.getRed() / 255.0, colorA.getGreen() / 255.0,
                colorA.getBlue() / 255.0, colorA.getAlpha() / 255.0);
        shader.set("uColorB", colorB.getRed() / 255.0, colorB.getGreen() / 255.0,
                colorB.getBlue() / 255.0, colorB.getAlpha() / 255.0);
        shader.set("uRadius", radius * fbo.sizeMulti);
        shader.set("uStrength", strength);

        RenderSystem.enableBlend();
        GlStateManager._blendFunc(GL_SRC_ALPHA, GL_ONE);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        PostProcessRenderer.beginRender();
        PostProcessRenderer.render();
        PostProcessRenderer.endRender();

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
