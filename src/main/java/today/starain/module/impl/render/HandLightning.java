package today.starain.module.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import today.starain.Starain;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Theme;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 第一人称手持物品的科幻描线：沿贴图不透明像素的外轮廓画细霓虹线，
 * 亮色脉冲沿轮廓流动，并有一条扫描带横扫物品表面。由 MixinItemRenderer 在物品渲染后调用。
 */
public class HandLightning extends ClientModule {

    private final BooleanValue syncColor = new BooleanValue("Sync Color", true, this);
    private final ColorValue color = new ColorValue("Color", new Color(80, 220, 255), this, () -> !syncColor.get());
    private final NumberValue width = new NumberValue("Line Width", 0.1, 1.5, 0.35, 0.05, this);
    private final NumberValue pulses = new NumberValue("Pulses", 0, 6, 3, 1, this);
    private final NumberValue speed = new NumberValue("Speed", 0.1, 3.0, 1.0, 0.1, this);
    private final BooleanValue scan = new BooleanValue("Scan Line", true, this);

    private TextureAtlasSprite cachedSprite;
    private boolean cachedFlat;
    /** 轮廓线段：x0, y0, x1, y1（物品模型 0~1 坐标） */
    private final List<float[]> edges = new ArrayList<>();
    /** 每条线段沿轮廓的累计位置 0~1，用于脉冲流动 */
    private final List<Float> edgePos = new ArrayList<>();
    private boolean[][] solid;
    private int texW, texH;

    public HandLightning() {
        setName("HandLightning");
        setCategory(Category.RENDER);
    }

    public static void onItemRendered(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                      PoseStack poseStack, MultiBufferSource buffer, BakedModel model) {
        if (stack.isEmpty() || !context.firstPerson()) return;
        Starain starain = Starain.getInstance();
        if (starain == null || starain.getModuleManager() == null) return;
        HandLightning module = starain.getModuleManager().getModule(HandLightning.class);
        if (module == null || !module.isEnabled()) return;
        module.render(context, leftHand, poseStack, buffer, model);
    }

    private void render(ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
                        MultiBufferSource buffer, BakedModel model) {
        boolean flat = !model.isGui3d();
        buildEdges(model.getParticleIcon(), flat);
        if (edges.isEmpty()) return;

        poseStack.pushPose();
        model.getTransforms().getTransform(context).apply(leftHand, poseStack);
        poseStack.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f m = poseStack.last().pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());

        float time = (System.currentTimeMillis() % 1_000_000L) / 1000.0f * speed.get().floatValue();
        float lw = width.get().floatValue() / 64.0f;
        int pulseCount = pulses.get().intValue();
        float scanPos = (time * 0.6f) % 1.6f - 0.3f;

        float[][] faces = flat
                ? new float[][]{{2, 0.5f + 1.0f / 32.0f + 0.003f}, {2, 0.5f - 1.0f / 32.0f - 0.003f}}
                : new float[][]{{2, 1.003f}, {2, -0.003f}, {1, 1.003f}, {1, -0.003f}, {0, 1.003f}, {0, -0.003f}};

        for (float[] face : faces) {
            int axis = (int) face[0];
            float depth = face[1];
            for (int i = 0; i < edges.size(); i++) {
                float[] e = edges.get(i);
                float pos = edgePos.get(i);
                Color c = syncColor.get() ? Theme.accent((int) (pos * 40)) : color.get();
                // 脉冲：沿轮廓位置的若干亮点，带拖尾
                float pulse = 0.0f;
                for (int k = 0; k < pulseCount; k++) {
                    float head = (time * 0.35f + (float) k / pulseCount) % 1.0f;
                    float d = head - pos;
                    if (d < 0) d += 1.0f;
                    if (d < 0.08f) pulse = Math.max(pulse, 1.0f - d / 0.08f);
                }
                // 扫描带：x+y 方向的斜向横扫
                float s = 0.0f;
                if (scan.get()) {
                    float diag = (e[0] + e[1] + e[2] + e[3]) * 0.25f;
                    float dist = Math.abs(diag - scanPos);
                    if (dist < 0.08f) s = 1.0f - dist / 0.08f;
                }
                float boost = Math.max(pulse, s * 0.8f);
                float alpha = 0.45f + 0.55f * boost;
                Color lineColor = mix(c, Color.WHITE, boost * 0.6f);
                quad(vc, m, e, lw * (1.0f + boost * 0.8f), axis, depth, lineColor, alpha);
            }
        }
        poseStack.popPose();
    }

    private void buildEdges(TextureAtlasSprite sprite, boolean flat) {
        if (sprite == cachedSprite && flat == cachedFlat) return;
        cachedSprite = sprite;
        cachedFlat = flat;
        edges.clear();
        edgePos.clear();
        if (!flat) {
            // 方块物品：每个面画外框 + 内框，形成科技面板感
            addRect(0.0f, 0.0f, 1.0f, 1.0f);
            addRect(0.18f, 0.18f, 0.82f, 0.82f);
            finishPositions();
            return;
        }
        SpriteContents contents = sprite.contents();
        texW = contents.width();
        texH = contents.height();
        solid = new boolean[texW][texH];
        for (int x = 0; x < texW; x++) {
            for (int y = 0; y < texH; y++) {
                solid[x][y] = !contents.isTransparent(0, x, y);
            }
        }
        // 只描不透明像素与透明区域相邻的边，得到物品外轮廓
        for (int x = 0; x < texW; x++) {
            for (int y = 0; y < texH; y++) {
                if (!solid[x][y]) continue;
                float x0 = (float) x / texW, x1 = (float) (x + 1) / texW;
                float y0 = 1.0f - (float) (y + 1) / texH, y1 = 1.0f - (float) y / texH;
                if (!isSolid(x, y - 1)) edges.add(new float[]{x0, y1, x1, y1});
                if (!isSolid(x, y + 1)) edges.add(new float[]{x0, y0, x1, y0});
                if (!isSolid(x - 1, y)) edges.add(new float[]{x0, y0, x0, y1});
                if (!isSolid(x + 1, y)) edges.add(new float[]{x1, y0, x1, y1});
            }
        }
        finishPositions();
    }

    private void addRect(float x0, float y0, float x1, float y1) {
        edges.add(new float[]{x0, y0, x1, y0});
        edges.add(new float[]{x1, y0, x1, y1});
        edges.add(new float[]{x1, y1, x0, y1});
        edges.add(new float[]{x0, y1, x0, y0});
    }

    /** 按角度绕中心排序得到“沿轮廓”的近似位置，使脉冲绕着物品流动。 */
    private void finishPositions() {
        for (float[] e : edges) {
            float cx = (e[0] + e[2]) * 0.5f - 0.5f, cy = (e[1] + e[3]) * 0.5f - 0.5f;
            float angle = (float) ((Math.atan2(cy, cx) + Math.PI) / (Math.PI * 2.0));
            edgePos.add(angle);
        }
    }

    private boolean isSolid(int x, int y) {
        return x >= 0 && y >= 0 && x < texW && y < texH && solid[x][y];
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float[] e, float w, int axis, float depth,
                             Color c, float alpha) {
        float dx = e[2] - e[0], dy = e[3] - e[1];
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-5f) return;
        float nx = -dy / len * w, ny = dx / len * w;
        // 线段两端各延长半个线宽，拐角处不留缺口
        float ex = dx / len * w, ey = dy / len * w;
        float[][] q = {
                {e[0] - ex - nx, e[1] - ey - ny}, {e[2] + ex - nx, e[3] + ey - ny},
                {e[2] + ex + nx, e[3] + ey + ny}, {e[0] - ex + nx, e[1] - ey + ny}};
        float r = c.getRed() / 255.0f, g = c.getGreen() / 255.0f, b = c.getBlue() / 255.0f;
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                float[] p = q[pass == 0 ? k : 3 - k];
                float x, y, z;
                switch (axis) {
                    case 0 -> { x = depth; y = p[1]; z = p[0]; }
                    case 1 -> { x = p[0]; y = depth; z = p[1]; }
                    default -> { x = p[0]; y = p[1]; z = depth; }
                }
                vc.vertex(m, x, y, z).color(r, g, b, alpha).endVertex();
            }
        }
    }
}
