package today.starain.utils.render.skia;

import com.mojang.blaze3d.platform.Window;
import io.github.humbleui.skija.*;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.RRect;
import io.github.humbleui.types.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.lwjgl.opengl.GL11;
import today.starain.module.impl.client.HUD;
import today.starain.utils.render.shader.impl.KawaseBlur;
import today.starain.utils.render.skia.context.SkiaContext;
import today.starain.utils.render.skia.image.ImageHelper;
import today.starain.utils.wrapper.Wrapper;

import java.awt.Color;
import java.io.File;

public class Skia implements Wrapper {

    public static Path blurPath = new Path();
    public static Path shadowPath = new Path();

    private static final ImageHelper imageHelper = new ImageHelper();
    private static final int[] COLOR_CODES = new int[]{
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFAA5500, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
    };

    public static void drawRect(float x, float y, float width, float height, Color color) {
        getCanvas().drawRect(Rect.makeXYWH(x, y, width, height), getPaint(color));
    }

    public static void drawCircle(float x, float y, float radius, Color color) {
        Paint paint = getPaint(color);
        getCanvas().drawCircle(x, y, radius, paint);
    }

    public static void drawCircle(float x, float y, float radius, float strokeWidth, Color color) {
        Paint paint = getPaint(color);
        paint.setMode(PaintMode.STROKE);
        paint.setStrokeWidth(strokeWidth);
        getCanvas().drawCircle(x, y, radius, paint);
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), getPaint(color));
    }

    public static void drawRoundedRectVarying(float x, float y, float width, float height, float topLeft,
                                              float topRight, float bottomRight, float bottomLeft, Color color) {

        float[] corners = new float[]{topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft,
                bottomLeft};

        getCanvas().drawRRect(RRect.makeComplexXYWH(x, y, width, height, corners), getPaint(color));
    }

    public static void drawBlur(float x, float y, float width, float height, float radius) {
        if (HUD.isBlurEnabled()) {
            blurPath.addRRect(RRect.makeXYWH(x, y, width, height, radius));
        }
    }

    public static void drawShadow(float x, float y, float width, float height, float radius) {
        if (HUD.isShadowEnabled()) {
            shadowPath.addRRect(RRect.makeXYWH(x, y, width, height, radius));
        }
    }

    public static void drawShadow(float x, float y, float width, float height, float radius, Color color) {

        Paint paint = getPaint(color);

        paint.setImageFilter(ImageFilter.makeBlur(2.5F, 2.5F, FilterTileMode.DECAL));

        save();
        clip(x, y, width, height, radius, ClipMode.DIFFERENCE);
        getCanvas().drawRRect(RRect.makeXYWH(x, y, width, height, radius), paint);
        restore();
    }

    public static void drawOutline(float x, float y, float width, float height, float radius, float strokeWidth,
                                   Color color) {

        float halfStroke = strokeWidth / 2;

        Path path = new Path();
        path.addRRect(RRect.makeXYWH(x + halfStroke, y + halfStroke, width - strokeWidth, height - strokeWidth,
                radius - halfStroke));

        Paint paint = getPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);

        getCanvas().drawPath(path, paint);
    }

    public static void drawImage(String path, float x, float y, float width, float height) {

        path = "/assets/starain/" + path;

        if (imageHelper.load(path)) {
            getCanvas().drawImageRect(imageHelper.get(path), Rect.makeXYWH(x, y, width, height));
        }
    }

    public static void drawImage(int textureId, float x, float y, float width, float height, float alpha,
                                 SurfaceOrigin origin) {

        if (imageHelper.load(textureId, width, height, origin)) {
            Paint paint = new Paint();
            paint.setAlpha((int) (255 * alpha));
            getCanvas().drawImageRect(imageHelper.get(textureId), Rect.makeXYWH(x, y, width, height), paint);
        }
    }

    public static void drawImage(int textureId, float x, float y, float width, float height, float alpha) {
        drawImage(textureId, x, y, width, height, alpha, SurfaceOrigin.TOP_LEFT);
    }

    public static void drawImage(File file, float x, float y, float width, float height) {
        if (imageHelper.load(file)) {
            getCanvas().drawImageRect(imageHelper.get(file.getName()), Rect.makeXYWH(x, y, width, height));
        }
    }

    public static void drawImage(int textureId, float x, float y, float width, float height, SurfaceOrigin origin) {

        if (imageHelper.load(textureId, width, height, origin)) {
            getCanvas().drawImageRect(imageHelper.get(textureId), Rect.makeXYWH(x, y, width, height));
        }
    }

    public static void drawImage(int textureId, float x, float y, float width, float height) {
        drawImage(textureId, x, y, width, height, SurfaceOrigin.TOP_LEFT);
    }

    public static void drawRoundedImage(int textureId, float x, float y, float width, float height, float radius) {

        Path path = new Path();
        path.addRRect(RRect.makeXYWH(x, y, width, height, radius));

        save();
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
        drawImage(textureId, x, y, width, height);
        restore();
    }

    public static void drawRoundedImage(String filePath, float x, float y, float width, float height, float radius) {

        Path path = new Path();
        path.addRRect(RRect.makeXYWH(x, y, width, height, radius));

        save();
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
        drawImage(filePath, x, y, width, height);
        restore();
    }

    public static void drawRoundedImage(File file, float x, float y, float width, float height, float radius) {

        Path path = new Path();
        path.addRRect(RRect.makeXYWH(x, y, width, height, radius));

        save();
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
        drawImage(file, x, y, width, height);
        restore();
    }

    public static void drawRoundedImage(int textureId, float x, float y, float width, float height, float radius,
                                        float alpha, SurfaceOrigin origin) {
        Path path = new Path();
        path.addRRect(RRect.makeXYWH(x, y, width, height, radius));

        save();
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
        drawImage(textureId, x, y, width, height, alpha, origin);
        restore();
    }

    public static void drawRoundedImage(int textureId, float x, float y, float width, float height, float radius,
                                        float alpha) {
        drawRoundedImage(textureId, x, y, width, height, radius, alpha, SurfaceOrigin.TOP_LEFT);
    }

    public static void drawPlayerHead(AbstractClientPlayer player, float x, float y, float width, float height, float radius) {
        if (player == null) return;
        var skin = player.getSkinTextureLocation();
        AbstractTexture texture = mc.getTextureManager().getTexture(skin);
        int textureId = texture != null ? texture.getId() : 0;
        if (textureId == 0) return;

        int[] size = getGlTextureSize(textureId);
        if (size[0] <= 0 || size[1] <= 0) return;

        if (imageHelper.load(textureId, size[0], size[1], SurfaceOrigin.TOP_LEFT)) {
            var image = imageHelper.get(textureId);
            if (image == null) return;

            Path path = new Path();
            path.addRRect(RRect.makeXYWH(x, y, width, height, radius));

            Rect srcRect = Rect.makeXYWH(8, 8, 8, 8);
            Rect srcRect1 = Rect.makeXYWH(40, 8, 8, 8);
            Rect dstRect = Rect.makeXYWH(x, y, width, height);

            save();
            getCanvas().clipPath(path, ClipMode.INTERSECT, true);
            getCanvas().drawImageRect(image, srcRect, dstRect, null, false);
            getCanvas().drawImageRect(image, srcRect1, dstRect, null, false);
            restore();
        }
    }

    public static void drawSkin(AbstractClientPlayer player, float x, float y, float scale) {
        if (player == null) return;
        var skin = player.getSkinTextureLocation();
        AbstractTexture texture = mc.getTextureManager().getTexture(skin);
        int textureId = texture != null ? texture.getId() : 0;
        if (textureId == 0) return;

        int[] size = getGlTextureSize(textureId);
        if (size[0] <= 0 || size[1] <= 0) return;

        if (imageHelper.load(textureId, size[0], size[1], SurfaceOrigin.TOP_LEFT)) {
            var image = imageHelper.get(textureId);
            if (image == null) return;

            Rect head = Rect.makeXYWH(8, 8, 8, 8);
            Rect headLayer = Rect.makeXYWH(40, 8, 8, 8);
            Rect body = Rect.makeXYWH(20, 20, 8, 12);
            Rect bodyLayer = Rect.makeXYWH(20, 36, 8, 12);
            Rect leftArm = Rect.makeXYWH(36, 52, 4, 12);
            Rect leftArmLayer = Rect.makeXYWH(52, 52, 4, 12);
            Rect rightArm = Rect.makeXYWH(44, 20, 4, 12);
            Rect rightArmLayer = Rect.makeXYWH(44, 36, 4, 12);
            Rect leftLeg = Rect.makeXYWH(20, 52, 4, 12);
            Rect leftLegLayer = Rect.makeXYWH(4, 52, 4, 12);
            Rect rightLeg = Rect.makeXYWH(4, 20, 4, 12);
            Rect rightLegLayer = Rect.makeXYWH(4, 36, 4, 12);

            save();
            scale(x, y, scale);
            getCanvas().drawImageRect(image, head,
                    Rect.makeXYWH(x + leftArm.getWidth(), y, head.getWidth(), head.getHeight()), null, false);
            getCanvas().drawImageRect(image, headLayer,
                    Rect.makeXYWH(x + leftArm.getWidth(), y, headLayer.getWidth(), headLayer.getHeight()), null, false);
            getCanvas().drawImageRect(image, body,
                    Rect.makeXYWH(x + leftArm.getWidth(), y + head.getHeight(), body.getWidth(), body.getHeight()),
                    null, false);
            getCanvas().drawImageRect(image, bodyLayer, Rect.makeXYWH(x + leftArm.getWidth(),
                    y + headLayer.getHeight(), bodyLayer.getWidth(), bodyLayer.getHeight()), null, false);
            getCanvas().drawImageRect(image, leftArm,
                    Rect.makeXYWH(x, y + head.getHeight(), leftArm.getWidth(), leftArm.getHeight()), null, false);
            getCanvas().drawImageRect(image, leftArmLayer,
                    Rect.makeXYWH(x, y + headLayer.getHeight(), leftArmLayer.getWidth(), leftArmLayer.getHeight()),
                    null, false);
            getCanvas().drawImageRect(image, rightArm,
                    Rect.makeXYWH(x + leftArm.getWidth() + body.getWidth(), y + head.getHeight(), rightArm.getWidth(),
                            rightArm.getHeight()),
                    null, false);
            getCanvas().drawImageRect(image, rightArmLayer,
                    Rect.makeXYWH(x + leftArmLayer.getWidth() + bodyLayer.getWidth(), y + headLayer.getHeight(),
                            rightArmLayer.getWidth(), rightArmLayer.getHeight()),
                    null, false);
            getCanvas().drawImageRect(
                    image, leftLeg, Rect.makeXYWH(x + leftArm.getWidth(),
                            y + head.getHeight() + body.getHeight(), leftLeg.getWidth(), leftLeg.getHeight()),
                    null, false);
            getCanvas().drawImageRect(image, leftLegLayer,
                    Rect.makeXYWH(x + leftArmLayer.getWidth(), y + headLayer.getHeight() + bodyLayer.getHeight(),
                            leftLegLayer.getWidth(), leftLegLayer.getHeight()),
                    null, false);
            getCanvas()
                    .drawImageRect(image, rightLeg,
                            Rect.makeXYWH(x + leftArm.getWidth() + leftLeg.getWidth(),
                                    y + head.getHeight() + body.getHeight(), rightLeg.getWidth(), rightLeg.getHeight()),
                            null, false);
            getCanvas().drawImageRect(image, rightLegLayer,
                    Rect.makeXYWH(x + leftArmLayer.getWidth() + leftLegLayer.getWidth(),
                            y + headLayer.getHeight() + bodyLayer.getHeight(), rightLegLayer.getWidth(),
                            rightLegLayer.getHeight()),
                    null, false);

            restore();
        }
    }

    private static int[] getGlTextureSize(int textureId) {
        try {
            int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
            return new int[]{w, h};
        } catch (Throwable t) {
            return new int[]{0, 0};
        }
    }

    public static void drawArc(float x, float y, float radius, float startAngle, float endAngle, float strokeWidth,
                               Color color) {

        Paint paint = getPaint(color);
        paint.setStrokeWidth(strokeWidth);
        paint.setMode(PaintMode.STROKE);

        getCanvas().drawArc(x - radius, y - radius, x + radius, y + radius, startAngle - 90, endAngle, false, paint);
    }

    public static void drawLine(float x, float y, float endX, float endY, float width, Color color) {

        Paint paint = getPaint(color);

        paint.setStroke(true);
        paint.setStrokeWidth(width);
        paint.setAntiAlias(true);

        getCanvas().drawLine(x, y, endX, endY, paint);
    }

    public static void drawGradientRoundedRect(float x, float y, float width, float height, float radius, Color color1,
                                               Color color2) {

        long currentTime = System.nanoTime();
        double speed = 0.0000000006;
        double tick = (currentTime * speed) % (2 * Math.PI);
        float max = Math.max(width, height);

        Path path = new Path();

        path.addRRect(RRect.makeXYWH(x, y, width, height, radius));

        float startX = x + width / 2 - (max / 2) * (float) Math.cos(tick);
        float startY = y + height / 2 - (max / 2) * (float) Math.sin(tick);
        float endX = x + width / 2 + (max / 2) * (float) Math.cos(tick);
        float endY = y + height / 2 + (max / 2) * (float) Math.sin(tick);

        int skColor1 = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(), color1.getRed(), color1.getGreen(),
                color1.getBlue());
        int skColor2 = io.github.humbleui.skija.Color.makeARGB(color2.getAlpha(), color2.getRed(), color2.getGreen(),
                color2.getBlue());

        int skColorMid = io.github.humbleui.skija.Color.makeARGB(color1.getAlpha(),
                (color1.getRed() + color2.getRed()) / 2, (color1.getGreen() + color2.getGreen()) / 2,
                (color1.getBlue() + color2.getBlue()) / 2);

        Paint paint = new Paint();

        paint.setShader(Shader.makeLinearGradient(new Point(startX, startY), new Point(endX, endY),
                new int[]{skColor1, skColorMid, skColor2}, new float[]{0, 0.5f, 1}));

        getCanvas().drawPath(path, paint);
    }

    public static void clipPath(Path path, ClipMode mode, boolean arg) {
        getCanvas().clipPath(path, mode, arg);
    }

    public static void clipPath(Path path) {
        getCanvas().clipPath(path, ClipMode.INTERSECT, true);
    }

    public static void clip(float x, float y, float width, float height, float radius, ClipMode mode) {

        Path path = new Path();

        path.addRRect(RRect.makeXYWH(x, y, width, height, radius));
        clipPath(path, mode, true);
    }

    public static void clip(float x, float y, float width, float height, float topLeft, float topRight,
                            float bottomRight, float bottomLeft) {

        float[] corners = new float[]{topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft,
                bottomLeft};

        Path path = new Path();

        path.addRRect(RRect.makeComplexXYWH(x, y, width, height, corners));
        clipPath(path, ClipMode.INTERSECT, true);
    }

    public static void clip(float x, float y, float width, float height, float radius) {
        clip(x, y, width, height, radius, ClipMode.INTERSECT);
    }

    private static int getColorIndex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static void drawStyledText(Canvas canvas, String text, float x, float y, Font font, Paint basePaint) {
        if (text == null || text.isEmpty()) return;
        float currentX = x;
        int originalColor = basePaint.getColor();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '§' && i + 1 < text.length()) {
                char colorCode = text.charAt(i + 1);
                int colorIndex = getColorIndex(colorCode);

                if (colorIndex != -1) {
                    basePaint.setColor(COLOR_CODES[colorIndex]);
                } else if (colorCode == 'r') {
                    basePaint.setColor(originalColor);
                }

                i++;
                continue;
            }

            int nextSection = i;
            StringBuilder sb = new StringBuilder();
            while (nextSection < text.length()) {
                char nc = text.charAt(nextSection);
                if (nc == '§' && nextSection + 1 < text.length()) {
                    break;
                }
                sb.append(nc);
                nextSection++;
            }

            String segment = sb.toString();

            canvas.drawString(segment, currentX, y, font, basePaint);

            currentX += font.measureTextWidth(segment);

            i = nextSection - 1;
        }

        basePaint.setColor(originalColor);
    }

    public static void drawText(String text, float x, float y, Color color, Font font) {
        if (text == null || text.isEmpty()) return;
        Paint paint = getPaint(color);
        paint.setImageFilter(ImageFilter.makeBlur(1.0F, 1.0F, FilterTileMode.DECAL));
        Rect bounds = font.measureText(text);
        float bx = x - bounds.getLeft();
        float by = y - bounds.getTop();
        drawStyledText(getCanvas(), text, bx, by, font, paint);
        drawStyledText(getCanvas(), text, x - bounds.getLeft(), y - bounds.getTop(), font, getPaint(color));
    }

    public static void drawCenteredText(String text, float x, float y, Color color, Font font) {
        if (text == null || text.isEmpty()) return;
        Paint paint = getPaint(color);
        paint.setImageFilter(ImageFilter.makeBlur(1.0F, 1.0F, FilterTileMode.DECAL));
        Rect bounds = font.measureText(text);
        float bx = x - bounds.getLeft();
        float by = y - bounds.getTop();
        drawStyledText(getCanvas(), text, bx, by, font, paint);
        drawStyledText(getCanvas(), text, x - bounds.getLeft() - (bounds.getWidth() / 2), y - bounds.getTop(), font,
                getPaint(color));
    }

    public static void drawHeightCenteredText(String text, float x, float y, Color color, Font font) {
        if (text == null || text.isEmpty()) return;

        FontMetrics metrics = font.getMetrics();
        Paint paint = getPaint(color);
        paint.setImageFilter(ImageFilter.makeBlur(1.0F, 1.0F, FilterTileMode.DECAL));
        Rect bounds = font.measureText(text);
        float bx = x - bounds.getLeft();
        float by = y - bounds.getTop();
        drawStyledText(getCanvas(), text, bx, by, font, paint);

        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();

        drawStyledText(getCanvas(), text, x - bounds.getLeft(), textCenterY, font, getPaint(color));
    }

    public static void drawTextPlain(String text, float x, float y, Color color, Font font) {
        if (text == null || text.isEmpty()) return;
        Rect bounds = font.measureText(text);
        drawStyledText(getCanvas(), text, x - bounds.getLeft(), y - bounds.getTop(), font, getPaint(color));
    }

    public static void drawHeightCenteredTextPlain(String text, float x, float y, Color color, Font font) {
        if (text == null || text.isEmpty()) return;
        FontMetrics metrics = font.getMetrics();
        Rect bounds = font.measureText(text);
        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();
        drawStyledText(getCanvas(), text, x - bounds.getLeft(), textCenterY, font, getPaint(color));
    }

    public static void drawFullCenteredText(String text, float x, float y, Color color, Font font) {
        if (text == null || text.isEmpty()) return;

        Paint paint = getPaint(color);
        paint.setImageFilter(ImageFilter.makeBlur(1.0F, 1.0F, FilterTileMode.DECAL));
        Rect bounds = font.measureText(text);
        float bx = x - bounds.getLeft();
        float by = y - bounds.getTop();
        drawStyledText(getCanvas(), text, bx, by, font, paint);

        FontMetrics metrics = font.getMetrics();

        float textCenterX = x - bounds.getLeft() - (bounds.getWidth() / 2);
        float textCenterY = y + (metrics.getAscent() - metrics.getDescent()) / 2 - metrics.getAscent();

        drawStyledText(getCanvas(), text, textCenterX, textCenterY, font, getPaint(color));
    }

    public static Rect getTextBounds(String text, Font font) {
        return font.measureText(text);
    }

    public static void drawTextShadow(String text, float x, float y, Color color, Font font) {
        Paint paint = getPaint(color);
        paint.setImageFilter(ImageFilter.makeBlur(2.5F, 2.5F, FilterTileMode.DECAL));
        Rect bounds = font.measureText(text);
        float bx = x - bounds.getLeft();
        float by = y - bounds.getTop();
        getCanvas().drawString(text, bx, by, font, paint);
    }

    public static String getLimitText(String text, Font font, float width) {

        boolean isInRange = false;
        boolean isRemoved = false;

        while (!isInRange) {

            if (getTextBounds(text, font).getWidth() > width - getTextBounds("...", font).getWidth()) {
                text = text.substring(0, text.length() - 1);
                isRemoved = true;
            } else {
                isInRange = true;
            }
        }

        return text + (isRemoved ? "..." : "");
    }

    private static Paint getPaint(Color color) {
        Paint paint = new Paint();
        paint.setARGB(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
        return paint;
    }

    public static void save() {
        getCanvas().save();
    }

    public static void restore() {
        getCanvas().restore();
    }

    public static void scale(float scale) {
        getCanvas().scale(scale, scale);
    }

    public static void scale(float x, float y, float scale) {
        getCanvas().translate(x, y);
        getCanvas().scale(scale, scale);
        getCanvas().translate(-x, -y);
    }

    public static void scale(float x, float y, float width, float height, float scale) {

        float centerX = x + width / 2;
        float centerY = y + height / 2;

        getCanvas().translate(centerX, centerY);
        getCanvas().scale(scale, scale);
        getCanvas().translate(-centerX, -centerY);
    }

    public static void translate(float x, float y) {
        getCanvas().translate(x, y);
    }

    public static void rotate(float x, float y, float width, float height, float rotate) {

        float centerX = x + width / 2;
        float centerY = y + height / 2;

        getCanvas().translate(centerX, centerY);
        getCanvas().rotate(rotate);
        getCanvas().translate(-centerX, -centerY);
    }

    public static void setAlpha(int alpha) {

        Paint paint = new Paint();
        paint.setAlpha(alpha);

        getCanvas().saveLayer(null, paint);
    }

    public static Canvas getCanvas() {
        return SkiaContext.getCanvas();
    }

    public static ImageHelper getImageHelper() {
        return imageHelper;
    }


    public static float getTextWidth(String text, Font font) {
        if (text == null || text.isEmpty()) return 0;
        StringBuilder cleanText = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
            } else {
                cleanText.append(c);
            }
        }

        return font.measureTextWidth(cleanText.toString());
    }
}
