package today.starain.module.impl.client;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import io.github.humbleui.skija.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import today.starain.Starain;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Design;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.NumberValue;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ScoreboardModule extends ClientModule {

    private static Objective objective;
    private final NumberValue x = new NumberValue("X", 0, 5000, 10, 1, this);
    private final NumberValue y = new NumberValue("y", 0, 1000, 150, 1, this);
    private final BooleanValue renderScore = new BooleanValue("Render Score", false, this);
    private final BooleanValue replaceIP = new BooleanValue("Replace Server IP", true, this);

    public ScoreboardModule() {
        setName("ScoreboardModule");
        setCategory(Category.CLIENT);
    }

    public static void renderSB(GuiGraphics guiGraphics, Objective objective, CallbackInfo ci) {
        ScoreboardModule sbModule = Starain.getInstance().getModuleManager().getModule(ScoreboardModule.class);
        if (!sbModule.isEnabled()) {
            ScoreboardModule.objective = null;
            return;
        }
        ScoreboardModule.objective = objective;
        ci.cancel();
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        if (objective == null) return;

        try {
            Scoreboard scoreboard = objective.getScoreboard();
            Collection<Score> allScores = scoreboard.getPlayerScores(objective);

            List<Score> validScores = allScores.stream()
                    .filter(score -> score.getOwner() != null && !score.getOwner().startsWith("#"))
                    .collect(Collectors.toList());
            List<Score> visibleScores;
            if (validScores.size() > 15) {
                visibleScores = Lists.newArrayList(Iterables.skip(validScores, validScores.size() - 15));
            } else {
                visibleScores = validScores;
            }
            List<Pair<Score, Component>> scoreDisplayData = Lists.newArrayListWithCapacity(visibleScores.size());

            Component titleComponent = objective.getDisplayName();
            Font font = Fonts.getNormal(7);
            float titleWidth = Skia.getTextWidth(Skia.getLegacyString(titleComponent), font);
            float maxLineWidth = Math.max(replaceIP.get() ? Skia.getTextWidth("www.starain.today", font) : 0, titleWidth);
            float colonWidth = Skia.getTextWidth(": ", font);

            for (Score score : visibleScores) {
                PlayerTeam playerTeam = scoreboard.getPlayersTeam(score.getOwner());
                MutableComponent ownerName = PlayerTeam.formatNameForTeam(playerTeam, Component.literal(score.getOwner()));
                scoreDisplayData.add(Pair.of(score, ownerName));

                maxLineWidth = Math.max(maxLineWidth, Skia.getTextWidth(Skia.getLegacyString(ownerName), font) + colonWidth + Skia.getTextWidth(Integer.toString(score.getScore()), font));
            }

            float lineHeight = 9.5f;
            float titleGap = 2f;
            float bgWidth = 5f + maxLineWidth + 5f;
            float bgHeight = 5f + lineHeight + titleGap + lineHeight * scoreDisplayData.size() + 5f;

            Skia.drawBlur(x.get().floatValue(), y.get().floatValue(), bgWidth, bgHeight, 6f);
            Skia.drawShadow(x.get().floatValue(), y.get().floatValue(), bgWidth, bgHeight, 6f);
            Skia.drawRoundedRect(x.get().floatValue(), y.get().floatValue(), bgWidth, bgHeight, 6f, Design.BACKGROUND_COLOR);

            float titleX = x.get().floatValue() + (bgWidth - titleWidth) / 2f;
            float titleY = y.get().floatValue() + 5f;
            Skia.drawText(Skia.getLegacyString(titleComponent), titleX, titleY, Design.TEXT_COLOR, font);

            float startX = x.get().floatValue() + 5f;
            float startY = titleY + lineHeight + titleGap;

            List<Pair<Score, Component>> reverse = Lists.reverse(scoreDisplayData);
            for (Pair<Score, Component> pair : reverse) {
                if (reverse.get(reverse.size() - 1) == pair && replaceIP.get()) {
                    Skia.drawGradientText("www.starain.today", startX, startY, font);
                } else {
                    Skia.drawText(Skia.getLegacyString(pair.getSecond()), startX, startY, Design.TEXT_COLOR, font);
                }
                if (renderScore.get()) {
                    float scoreStartX = x.get().floatValue() + bgWidth - 5f - Skia.getTextWidth(String.valueOf(pair.getFirst().getScore()), font);
                    Skia.drawText("§c" + pair.getFirst().getScore(), scoreStartX, startY, Color.RED, font);
                }
                startY += lineHeight;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}