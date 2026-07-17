package today.starain.utils.render.skia.font;

import io.github.humbleui.skija.Font;

public class Fonts {

    public static Font getSfProNormal(float size) {
        return FontHelper.load("SFPRODISPLAYREGULAR.ttf", size);
    }

    public static Font getSfProMedium(float size) {
        return FontHelper.load("SFPRODISPLAYMEDIUM.ttf", size);
    }

    public static Font getSfProBold(float size) {
        return FontHelper.load("SFPRODISPLAYBOLD.ttf", size);
    }

    public static Font getBorel(float size) {
        return FontHelper.load("Borel-Regular.ttf", size);
    }

    public static Font getUrbanistVariable(float size) {
        return FontHelper.load("Urbanist-VariableFont_wght.ttf", size);
    }

    public static Font getInterBold(float size) {
        return FontHelper.load("Inter-Bold.ttf", size);
    }

    public static Font getInterMedium(float size) {
        return FontHelper.load("Inter-Medium.ttf", size);
    }

    public static Font getInterRegular(float size) {
        return FontHelper.load("Inter-Regular.ttf", size);
    }

    public static Font getInterLight(float size) {
        return FontHelper.load("Inter-Light.ttf", size);
    }

    public static Font getIconFill(float size) {
        return FontHelper.load("MaterialSymbolsRounded_Fill.ttf", size);
    }

    public static Font getIcon(float size) {
        return FontHelper.load("MaterialSymbolsRounded.ttf", size);
    }
}
