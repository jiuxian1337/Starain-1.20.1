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
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 第一人称手持物品的科幻描线（多种风格）：沿贴图不透明像素的外轮廓画细霓虹线，
 * 亮色脉冲沿轮廓流动，并有一条扫描带横扫物品表面。由 MixinItemRenderer 在物品渲染后调用。
 */
public class HandLightning extends ClientModule {

    private final ModeValue style = new ModeValue("Style", "Circuit", new String[]{"Circuit", "Arc", "Plasma", "Glitch", "Aura", "Orbit"}, this);
    private final BooleanValue syncColor = new BooleanValue("Sync Color", true, this);
    private final ColorValue color = new ColorValue("Color", new Color(80, 220, 255), this, () -> !syncColor.get());
    private final NumberValue width = new NumberValue("Line Width", 0.1, 1.5, 0.35, 0.05, this);
    private final NumberValue pulses = new NumberValue("Pulses", 0, 6, 3, 1, this, () -> style.isMode("Circuit") || style.isMode("Arc"));
    private final NumberValue speed = new NumberValue("Speed", 0.1, 3.0, 1.0, 0.1, this);
    private final BooleanValue scan = new BooleanValue("Scan Line", true, this, () -> style.isMode("Circuit"));

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

        switch (style.get()) {
            case "Arc" -> renderArc(vc, m, faces, time, lw);
            case "Plasma" -> renderPlasma(vc, m, faces, time, lw);
            case "Glitch" -> renderGlitch(vc, m, faces, time, lw);
            case "Aura" -> renderAura(vc, m, faces, time, lw);
            case "Orbit" -> renderOrbit(vc, m, faces, time, lw);
            default -> renderCircuit(vc, m, faces, time, lw, pulseCount, scanPos);
        }
        poseStack.popPose();
    }

    private Color baseColor(float pos) {
        return syncColor.get() ? Theme.accent((int) (pos * 40)) : color.get();
    }

    private void renderCircuit(VertexConsumer vc, Matrix4f m, float[][] faces, float time, float lw, int pulseCount, float scanPos) {
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
    }

    // Arc：轮廓上随机两点间生成锯齿电弧，按时间片重新生成，形成噼啪放电
    private void renderArc(VertexConsumer vc, Matrix4f m, float[][] faces, float time, float lw) {
        Random rnd = new Random((long) (time * 12.0f) * 31L + edges.size());
        int bolts = Math.max(1, pulses.get().intValue());
        List<float[]> segs = new ArrayList<>();
        for (int b = 0; b < bolts; b++) {
            float[] a = edges.get(rnd.nextInt(edges.size()));
            float[] c = edges.get(rnd.nextInt(edges.size()));
            float ax = (a[0] + a[2]) * 0.5f, ay = (a[1] + a[3]) * 0.5f;
            float cx = (c[0] + c[2]) * 0.5f, cy = (c[1] + c[3]) * 0.5f;
            bolt(segs, rnd, ax, ay, cx, cy, 0.12f, 4);
        }
        for (float[] face : faces) {
            int axis = (int) face[0];
            for (int i = 0; i < edges.size(); i++) {
                quad(vc, m, edges.get(i), lw * 0.7f, axis, face[1], baseColor(edgePos.get(i)), 0.25f);
            }
            for (int i = 0; i < segs.size(); i++) {
                Color c = baseColor((float) i / segs.size());
                quad(vc, m, segs.get(i), lw * 2.2f, axis, face[1], c, 0.35f);
                quad(vc, m, segs.get(i), lw * 0.8f, axis, face[1], mix(c, Color.WHITE, 0.75f), 1.0f);
            }
        }
    }

    private static void bolt(List<float[]> out, Random rnd, float x0, float y0, float x1, float y1, float disp, int depth) {
        if (depth == 0) {
            out.add(new float[]{x0, y0, x1, y1});
            return;
        }
        float mx = (x0 + x1) * 0.5f, my = (y0 + y1) * 0.5f;
        float dx = x1 - x0, dy = y1 - y0;
        float off = (rnd.nextFloat() - 0.5f) * disp * 2.0f;
        mx += -dy * off;
        my += dx * off;
        bolt(out, rnd, x0, y0, mx, my, disp, depth - 1);
        bolt(out, rnd, mx, my, x1, y1, disp, depth - 1);
        // 偶尔分叉出一根短支线
        if (depth == 3 && rnd.nextFloat() < 0.35f) {
            float bx = mx + (dx * 0.4f - dy * 0.5f) * (rnd.nextFloat() * 0.6f + 0.2f);
            float by = my + (dy * 0.4f + dx * 0.5f) * (rnd.nextFloat() * 0.6f + 0.2f);
            bolt(out, rnd, mx, my, bx, by, disp, depth - 2);
        }
    }

    // Plasma：轮廓顶点沿径向正弦起伏，颜色随时间在主题色间流转
    private void renderPlasma(VertexConsumer vc, Matrix4f m, float[][] faces, float time, float lw) {
        for (float[] face : faces) {
            int axis = (int) face[0];
            for (int i = 0; i < edges.size(); i++) {
                float[] e = edges.get(i);
                float[] d = {e[0], e[1], e[2], e[3]};
                warp(d, 0, time);
                warp(d, 2, time);
                float pos = edgePos.get(i);
                Color c = syncColor.get() ? Theme.accent((int) (pos * 80 + time * 30)) : color.get();
                float wave = 0.5f + 0.5f * (float) Math.sin(pos * Math.PI * 6.0 - time * 4.0);
                quad(vc, m, d, lw * (2.4f + wave * 1.2f), axis, face[1], c, 0.22f);
                quad(vc, m, d, lw, axis, face[1], mix(c, Color.WHITE, wave * 0.5f), 0.65f + 0.35f * wave);
            }
        }
    }

    private static void warp(float[] d, int i, float time) {
        float vx = d[i] - 0.5f, vy = d[i + 1] - 0.5f;
        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len < 1.0e-4f) return;
        float ang = (float) Math.atan2(vy, vx);
        float amp = 0.025f * (float) Math.sin(ang * 5.0f + time * 3.0f) + 0.015f * (float) Math.sin(ang * 9.0f - time * 5.0f);
        d[i] += vx / len * amp;
        d[i + 1] += vy / len * amp;
    }

    // Glitch：RGB 分离的错位轮廓 + 随机水平切片位移，像信号故障
    private void renderGlitch(VertexConsumer vc, Matrix4f m, float[][] faces, float time, float lw) {
        Random rnd = new Random((long) (time * 9.0f));
        boolean burst = rnd.nextFloat() < 0.4f;
        float split = burst ? 0.02f + rnd.nextFloat() * 0.03f : 0.008f;
        float sliceY = rnd.nextFloat(), sliceH = 0.08f + rnd.nextFloat() * 0.12f;
        float sliceOff = burst ? (rnd.nextFloat() - 0.5f) * 0.12f : 0.0f;
        Color[] layers = {new Color(255, 40, 90), new Color(40, 230, 255), Color.WHITE};
        float[] offsets = {-split, split, 0.0f};
        for (float[] face : faces) {
            int axis = (int) face[0];
            for (int l = 0; l < 3; l++) {
                for (int i = 0; i < edges.size(); i++) {
                    float[] e = edges.get(i);
                    float my = (e[1] + e[3]) * 0.5f;
                    float sx = Math.abs(my - sliceY) < sliceH * 0.5f ? sliceOff : 0.0f;
                    float[] d = {e[0] + offsets[l] + sx, e[1], e[2] + offsets[l] + sx, e[3]};
                    Color c = l == 2 ? mix(baseColor(edgePos.get(i)), Color.WHITE, 0.4f) : layers[l];
                    quad(vc, m, d, lw * (l == 2 ? 1.0f : 0.8f), axis, face[1], c, l == 2 ? 0.95f : 0.55f);
                }
            }
        }
    }

    // Aura：多层放大的轮廓副本由内向外扩散并淡出，形成能量光环
    private void renderAura(VertexConsumer vc, Matrix4f m, float[][] faces, float time, float lw) {
        int rings = 4;
        for (float[] face : faces) {
            int axis = (int) face[0];
            for (int i = 0; i < edges.size(); i++) {
                quad(vc, m, edges.get(i), lw, axis, face[1], mix(baseColor(edgePos.get(i)), Color.WHITE, 0.3f), 0.9f);
            }
            for (int r = 0; r < rings; r++) {
                float t = (time * 0.5f + (float) r / rings) % 1.0f;
                float sc = 1.0f + t * 0.35f;
                float alpha = (1.0f - t) * 0.6f;
                for (int i = 0; i < edges.size(); i++) {
                    float[] e = edges.get(i);
                    float[] d = {0.5f + (e[0] - 0.5f) * sc, 0.5f + (e[1] - 0.5f) * sc, 0.5f + (e[2] - 0.5f) * sc, 0.5f + (e[3] - 0.5f) * sc};
                    quad(vc, m, d, lw * (1.0f - t * 0.5f), axis, face[1], baseColor(edgePos.get(i) + t), alpha);
                }
            }
        }
    }

    // Orbit：两条倾斜的 3D 轨道环绕物品旋转，亮点沿轨道运动，物品保留淡轮廓
    private void renderOrbit(VertexConsumer vc, Matrix4f m, float[][] faces, float time, float lw) {
        for (float[] face : faces) {
            for (int i = 0; i < edges.size(); i++) {
                quad(vc, m, edges.get(i), lw * 0.8f, (int) face[0], face[1], baseColor(edgePos.get(i)), 0.45f);
            }
        }
        int seg = 48;
        float radius = 0.62f;
        for (int ring = 0; ring < 2; ring++) {
            float tilt = ring == 0 ? 1.1f : -1.1f;
            float spin = time * (ring == 0 ? 1.2f : -0.9f);
            float head = (time * 0.6f + ring * 0.5f) % 1.0f;
            float[] prev = null;
            for (int k = 0; k <= seg; k++) {
                float th = (float) (Math.PI * 2.0 * k / seg) + spin;
                float cx = (float) Math.cos(th) * radius, cy = (float) Math.sin(th) * radius;
                // 绕 x 轴倾斜轨道平面
                float[] p = {0.5f + cx, 0.5f + cy * (float) Math.cos(tilt), 0.5f + cy * (float) Math.sin(tilt)};
                if (prev != null) {
                    float u = (float) k / seg;
                    float d = head - u;
                    if (d < 0) d += 1.0f;
                    float boost = d < 0.18f ? 1.0f - d / 0.18f : 0.0f;
                    Color c = mix(baseColor(u + ring * 0.5f), Color.WHITE, boost * 0.7f);
                    ribbon(vc, m, prev, p, lw * (1.0f + boost * 1.5f), c, 0.35f + 0.65f * boost);
                }
                prev = p;
            }
        }
    }

    /** 3D 线段：沿两个互相垂直的方向各画一条带，任意视角都不会变成零宽。 */
    private static void ribbon(VertexConsumer vc, Matrix4f m, float[] a, float[] b, float w, Color c, float alpha) {
        float dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-5f) return;
        dx /= len; dy /= len; dz /= len;
        float[] ref = Math.abs(dy) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        float[] n1 = norm(cross(dx, dy, dz, ref[0], ref[1], ref[2]));
        float[] n2 = norm(cross(dx, dy, dz, n1[0], n1[1], n1[2]));
        float r = c.getRed() / 255.0f, g = c.getGreen() / 255.0f, bl = c.getBlue() / 255.0f;
        for (float[] n : new float[][]{n1, n2}) {
            float ox = n[0] * w, oy = n[1] * w, oz = n[2] * w;
            float[][] q = {{a[0] - ox, a[1] - oy, a[2] - oz}, {b[0] - ox, b[1] - oy, b[2] - oz},
                    {b[0] + ox, b[1] + oy, b[2] + oz}, {a[0] + ox, a[1] + oy, a[2] + oz}};
            for (int pass = 0; pass < 2; pass++) {
                for (int k = 0; k < 4; k++) {
                    float[] p = q[pass == 0 ? k : 3 - k];
                    vc.vertex(m, p[0], p[1], p[2]).color(r, g, bl, alpha).endVertex();
                }
            }
        }
    }

    private static float[] cross(float ax, float ay, float az, float bx, float by, float bz) {
        return new float[]{ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    private static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1.0e-6f ? new float[]{0, 0, 1} : new float[]{v[0] / l, v[1] / l, v[2] / l};
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
