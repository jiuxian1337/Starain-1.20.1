package today.starain.module.impl.client;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Design;
import today.starain.utils.animation.SmoothAnimationTimer;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.ColorValue;
import today.starain.value.impl.ModeValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArrayListModule extends ClientModule {

    private final BooleanValue hideRenderModule = new BooleanValue("Hide Render Module", true, this);

    public ArrayListModule() {
        setName("ArrayList");
        setCategory(Category.CLIENT);
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        List<ClientModule> modules = new ArrayList<>(Starain.getInstance().getModuleManager().getModules());
        Font font = Fonts.getSfProNormal(7.5f);
        modules.sort(Comparator.comparing((m) -> Skia.getTextWidth(((ClientModule)m).getDisplayName(), font)).reversed());
        modules.removeIf((m) -> (m.getCategory() == Category.RENDER || m.getCategory() == Category.CLIENT) && hideRenderModule.get());
        int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
        // 距离屏幕顶部10像素
        float startY = 10;
        for (ClientModule module : modules) {
            module.progress.update(module.isEnabled());
            if (module.progress.value > 0) {
                float textWidth = Skia.getTextWidth(module.getDisplayName(), font);
                // 距离屏幕右边缘10像素 文字间距上下2.5左右3.5
                float width = 3.5F + textWidth + 3.5F;
                float height = 2.5f + 7.5f + 2.5f;
                float x = guiScaledWidth - 10F - width * ((module.progress.value - 50f) / 50f);
                Skia.drawShadow(x, startY, width, height, 0);
                Skia.drawBlur(x, startY, width, height, 0);
                Skia.drawRoundedRect(x, startY, width, height, 0, Design.BACKGROUND_COLOR);
                Skia.drawText(module.getDisplayName(), x + 2.5F, startY + 2.5F, Design.TEXT_COLOR, font);
                startY += height * (module.progress.value / 100f);
            }
        }
    }
}
