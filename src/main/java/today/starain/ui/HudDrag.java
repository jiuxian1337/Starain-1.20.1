package today.starain.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import today.starain.Starain;
import today.starain.module.ClientModule;
import today.starain.utils.render.skia.Skia;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天输入框打开时拖拽 HUD 组件。
 * 各 HUD 每帧通过 {@link #report} 上报绘制区域（在 beginHudScale 内调用，坐标为缩放前的局部坐标），
 * 点击命中后直接改写该组件的 X/Y 数值，松开鼠标时保存配置。
 */
public final class HudDrag {

    private static final class Region {
        ClientModule module;
        NumberValue xValue, yValue;
        boolean invertX, invertY;
        float x, y, w, h;
    }

    private static final List<Region> regions = new ArrayList<>();
    private static List<Region> lastFrame = new ArrayList<>();

    private static ClientModule dragging;
    private static NumberValue dragX, dragY;
    private static boolean dragInvertX, dragInvertY;
    private static double startMouseX, startMouseY, startValueX, startValueY;

    private HudDrag() {
    }

    public static boolean isEditing() {
        return Minecraft.getInstance().screen instanceof ChatScreen;
    }

    public static void report(ClientModule module, NumberValue xValue, NumberValue yValue,
                              float x, float y, float w, float h) {
        report(module, xValue, yValue, x, y, w, h, false, false);
    }

    /**
     * @param invertX X 数值为距屏幕右边缘的偏移（向右拖动时数值减小）
     * @param invertY Y 数值为距屏幕下边缘的偏移
     */
    public static void report(ClientModule module, NumberValue xValue, NumberValue yValue,
                              float x, float y, float w, float h, boolean invertX, boolean invertY) {
        if (!isEditing() || w <= 0 || h <= 0) return;
        Region region = new Region();
        region.module = module;
        region.xValue = xValue;
        region.yValue = yValue;
        region.invertX = invertX;
        region.invertY = invertY;
        float s = Skia.hudScale();
        region.x = Skia.hudX(x);
        region.y = Skia.hudY(y);
        region.w = w * s;
        region.h = h * s;
        regions.add(region);
    }

    /** 由 ChatScreen.mouseClicked 调用；命中组件时返回 true 并吞掉这次点击。 */
    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        // 后上报的组件画在上层，优先命中
        for (int i = lastFrame.size() - 1; i >= 0; i--) {
            Region r = lastFrame.get(i);
            if (mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h) {
                dragging = r.module;
                dragX = r.xValue;
                dragY = r.yValue;
                dragInvertX = r.invertX;
                dragInvertY = r.invertY;
                startMouseX = mouseX;
                startMouseY = mouseY;
                startValueX = r.xValue.getValue();
                startValueY = r.yValue.getValue();
                return true;
            }
        }
        return false;
    }

    /** 在 RenderSkiaEvent 之后、GUI 缩放坐标系下调用：更新拖拽并绘制编辑框。 */
    public static void renderOverlay() {
        Minecraft mc = Minecraft.getInstance();
        if (!isEditing()) {
            finishDrag();
            regions.clear();
            lastFrame.clear();
            return;
        }

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());

        if (dragging != null) {
            if (GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
                finishDrag();
            } else {
                double dx = (mouseX - startMouseX) * (dragInvertX ? -1 : 1);
                double dy = (mouseY - startMouseY) * (dragInvertY ? -1 : 1);
                dragX.setValue(Math.round(startValueX + dx));
                dragY.setValue(Math.round(startValueY + dy));
            }
        }

        Color accent = Theme.accent(0);
        for (Region r : regions) {
            boolean active = r.module == dragging;
            boolean hover = mouseX >= r.x && mouseX <= r.x + r.w && mouseY >= r.y && mouseY <= r.y + r.h;
            Color color = active || hover
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), active ? 230 : 170)
                    : new Color(255, 255, 255, 70);
            Skia.drawOutline(r.x - 2.0f, r.y - 2.0f, r.w + 4.0f, r.h + 4.0f, 4.0f, 1.0f, color);
        }

        lastFrame = new ArrayList<>(regions);
        regions.clear();
    }

    private static void finishDrag() {
        if (dragging == null) return;
        dragging = null;
        dragX = dragY = null;
        Starain.getInstance().getConfigManager().saveConfig();
    }
}
