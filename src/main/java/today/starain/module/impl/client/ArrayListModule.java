package today.starain.module.impl.client;

import io.github.humbleui.skija.Font;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Design;
import today.starain.ui.Theme;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;
import today.starain.module.impl.render.Glow;
import today.starain.ui.HudDrag;

import java.awt.Color;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ArrayListModule extends ClientModule {

    private final ModeValue style = new ModeValue("Style", "Material", new String[]{"Material", "Novoline", "Exhibition", "Outline"}, this);
    private final NumberValue x = new NumberValue("X", 0, 1000, 10, 1, this, () -> false);
    private final NumberValue y = new NumberValue("y", 0, 1000, 30, 1, this, () -> false);
    private final BooleanValue syncColor = new BooleanValue("Sync Color", false, this);
    private final BooleanValue background = new BooleanValue("Background", true, this);
    private final BooleanValue glow = new BooleanValue("Glow", false, this);
    private final ModeValue backgroundMode = new ModeValue("Background Mode", "Blur", new String[]{"Blur", "Glow"}, this, () -> background.get() && style.isMode("Material"));
    private final BooleanValue sidebar = new BooleanValue("Sidebar", false, this, () -> style.isMode("Material"));
    private final NumberValue scale = new NumberValue("Scale", 0.5, 2.0, 1.0, 0.05, this);
    private final BooleanValue hideRenderModule = new BooleanValue("Hide Render Module", true, this);

    public ArrayListModule() {
        setName("ArrayList");
        setCategory(Category.CLIENT);
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        Skia.beginHudScale(mc.getWindow().getGuiScaledWidth() - this.x.get().floatValue(), this.y.get().floatValue(), scale.get().floatValue());
        try {
            renderScaled(event);
        } finally {
            Skia.endHudScale();
        }
    }

    private void renderScaled(RenderSkiaEvent event) {
        String mode = style.get();
        List<ClientModule> modules = new ArrayList<>(Starain.getInstance().getModuleManager().getModules());
        Font font = fontFor(mode);
        modules.sort(Comparator.comparing((m) -> Skia.getTextWidth(((ClientModule) m).getDisplayName(), font)).reversed());
        modules.removeIf((m) -> (m.getCategory() == Category.RENDER || m.getCategory() == Category.CLIENT) && hideRenderModule.get());
        int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
        float startY = y.get().floatValue();
        float right = guiScaledWidth - this.x.get().floatValue();
        float minX = right;
        float padX = padFor(mode), height = rowHeight(mode), gap = gapFor(mode);
        int index = 0;
        float lastWidth = -1.0f;
        for (ClientModule module : modules) {
            module.progress.update(module.isEnabled());
            if (module.progress.value <= 0) continue;
            float a = module.progress.value / 100f;
            float textWidth = Skia.getTextWidth(module.getDisplayName(), font);
            float width = padX + textWidth + padX + (mode.equals("Exhibition") ? 2.0f : 0.0f);
            float x = right - width * ((module.progress.value - 50f) / 50f);
            Color accent = Theme.accent(-startY * 0.1d);
            Color textColor = syncColor.get() || !mode.equals("Material") ? accent : Design.TEXT_COLOR;

            if (glow.get()) {
                Glow.queueHud(x, startY - 0.5F, width, height + 1.0F, radiusFor(mode, height), a);
            }
            Skia.setAlpha((int) (Math.min(1.0f, a) * 255));
            switch (mode) {
                case "Novoline" -> {
                    // 经典 Novoline：半透明黑底 + 右侧细条，文字主题色带阴影
                    if (background.get()) Skia.drawRect(x, startY, width, height, new Color(0, 0, 0, 95));
                    Skia.drawRect(right - 1.0f, startY, 1.0f, height, accent);
                    Skia.drawText(module.getDisplayName(), x + padX - 1.0f, startY + 2.0f, accent, font);
                }
                case "Exhibition" -> {
                    // Exhibition：深色方块 + 左侧色条，后缀灰色
                    if (background.get()) Skia.drawRect(x, startY, width, height, new Color(12, 12, 12, 175));
                    Skia.drawRect(x, startY, 1.5f, height, accent);
                    Skia.drawText(module.getDisplayName(), x + padX + 1.0f, startY + 2.0f, accent, font);
                }
                case "Outline" -> {
                    // Outline：连续折线描边，宽度变化处画台阶
                    if (background.get()) Skia.drawRect(x, startY, width, height, new Color(0, 0, 0, 80));
                    Skia.drawRect(x - 1.0f, startY, 1.0f, height, accent);
                    Skia.drawRect(right, startY, 1.0f, height, accent);
                    if (lastWidth < 0) {
                        Skia.drawRect(x - 1.0f, startY - 1.0f, width + 2.0f, 1.0f, accent);
                    } else if (lastWidth > width) {
                        Skia.drawRect(right - lastWidth - 1.0f, startY, lastWidth - width, 1.0f, accent);
                    }
                    Skia.drawText(module.getDisplayName(), x + padX, startY + 2.5f, accent, font);
                }
                default -> {
                    if (background.get() && backgroundMode.isMode("Glow")) {
                        // 发光背景：主题色淡填充 + 合并的外发光轮廓
                        Glow.queueHud(x, startY - 0.5F, width, height + 1.0F, 0, a);
                        Skia.drawRoundedRect(x, startY, width, height, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
                    } else if (background.get()) {
                        Skia.drawShadow(x, startY, width, height, 0);
                        Skia.drawBlur(x, startY, width, height, 0);
                        Skia.drawRoundedRect(x, startY, width, height, 0, Design.BACKGROUND_COLOR);
                    }
                    Skia.drawText(module.getDisplayName(), x + 2.5F, startY + 2.5F, textColor, font);
                    if (sidebar.get()) {
                        Skia.drawRoundedRect(x + width - 2.5F, startY + 1.5F, 2.0F, height - 3.0F, 1.0F, textColor);
                    }
                }
            }
            Skia.restore();
            minX = Math.min(minX, x);
            startY += (height + gap) * a;
            lastWidth = width;
            index++;
        }
        if (mode.equals("Outline") && lastWidth > 0) {
            Skia.drawRect(right - lastWidth - 1.0f, startY, lastWidth + 2.0f, 1.0f, Theme.accent(-startY * 0.1d));
        }
        float top = y.get().floatValue();
        // 列表为空时也留一个可拖动的小区域
        if (minX >= right) minX = right - 40.0f;
        HudDrag.report(this, this.x, this.y, minX, top, right - minX, Math.max(startY - top, 12.0f), true, false);
    }

    private Font fontFor(String mode) {
        return switch (mode) {
            case "Novoline", "Exhibition" -> Fonts.getMedium(8.0f);
            default -> Fonts.getNormal(7.5f);
        };
    }

    private float padFor(String mode) {
        return switch (mode) {
            default -> 3.5f;
        };
    }

    private float rowHeight(String mode) {
        return switch (mode) {
            case "Novoline", "Exhibition" -> 11.0f;
            default -> 12.5f;
        };
    }

    private float gapFor(String mode) {
        return switch (mode) {
            default -> 0.0f;
        };
    }

    private float radiusFor(String mode, float height) {
        return switch (mode) {
            default -> 0.0f;
        };
    }
}