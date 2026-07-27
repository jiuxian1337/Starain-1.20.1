package today.starain.ui;

import today.starain.Starain;
import today.starain.module.impl.client.HUD;
import today.starain.utils.render.ColorUtil;

import java.awt.*;

public final class Theme {

    public static final Color DEFAULT_PRIMARY = new Color(91, 207, 250);
    public static final Color DEFAULT_SECONDARY = new Color(245, 171, 185);

    private Theme() {
    }

    private static HUD cachedHud;

    private static HUD hud() {
        HUD cached = cachedHud;
        if (cached != null) {
            return cached;
        }
        Starain instance = Starain.getInstance();
        if (instance == null || instance.getModuleManager() == null) {
            return null;
        }
        cachedHud = instance.getModuleManager().getModule(HUD.class);
        return cachedHud;
    }

    public static Color primary() {
        HUD hud = hud();
        return hud == null ? DEFAULT_PRIMARY : hud.getThemePrimary();
    }

    public static Color secondary() {
        HUD hud = hud();
        return hud == null ? DEFAULT_SECONDARY : hud.getThemeSecondary();
    }

    public static Color accent(int index) {
        return accent((double) index);
    }

    public static Color accent(double index) {
        HUD hud = hud();
        if (hud == null) {
            return DEFAULT_PRIMARY;
        }
        int spread = hud.getThemeSpread();
        return switch (hud.getThemeMode()) {
            case "Solid" -> hud.getThemePrimary();
            case "Rainbow" -> ColorUtil.rainbow(hud.getThemeSpeed(), index * spread, 1.0F, 1.0F, 1.0F);
            default -> ColorUtil.interpolateColorsBackAndForth(
                    hud.getThemeSpeed(), index * spread, hud.getThemePrimary(), hud.getThemeSecondary(), true);
        };
    }

    public static Color accent() {
        return accent(0);
    }
}
