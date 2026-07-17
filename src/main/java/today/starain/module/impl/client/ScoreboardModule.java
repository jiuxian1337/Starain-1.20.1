package today.starain.module.impl.client;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import io.github.humbleui.skija.Font;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import today.starain.Starain;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.value.impl.NumberValue;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ScoreboardModule extends ClientModule {

    private final NumberValue x = new NumberValue("X", 0, 1000, 10, 1, this);
    private final NumberValue y = new NumberValue("y", 0, 1000, 10, 1, this);
    public ScoreboardModule() {
        setName("ScoreboardModule");
        setCategory(Category.CLIENT);
    }

    public static void renderSB(GuiGraphics guiGraphics, Objective objective, CallbackInfo ci) {
        ScoreboardModule scoreboardModule = Starain.getInstance().getModuleManager().getModule(ScoreboardModule.class);
        if (!scoreboardModule.isEnabled()) return;
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
        Font font = Fonts.getSfProNormal(7);
        float titleWidth = Skia.getTextWidth(titleComponent.getString(), font);
        float maxLineWidth = titleWidth;
        float colonWidth = Skia.getTextWidth(": ", font);

        for (Score score : visibleScores) {
            PlayerTeam playerTeam = scoreboard.getPlayersTeam(score.getOwner());
            MutableComponent ownerName = PlayerTeam.formatNameForTeam(playerTeam, Component.literal(score.getOwner()));
            scoreDisplayData.add(Pair.of(score, ownerName));

            maxLineWidth = Math.max(maxLineWidth, Skia.getTextWidth(ownerName.getString(), font) + colonWidth + Skia.getTextWidth(Integer.toString(score.getScore()), font));
        }

// 5. 修复：清理无意义的 decompiler 生成变量 (var10000, n = true 等)
        int scoreCount = visibleScores.size();
        int totalHeight = scoreCount * 9;
        int startY = 10;
        int startX = this.screenWidth - maxLineWidth - 3;
        int drawnCount = 0;
        int bgColorPrimary = this.minecraft.options.getBackgroundColor(0.3F);
        int bgColorSecondary = this.minecraft.options.getBackgroundColor(0.4F);

// 6. 修复：清理 Iterator 和无用的 Objects.requireNonNull
        for (Pair<Score, Component> pair : scoreDisplayData) {
            drawnCount++;
            Score score = pair.getFirst();
            Component ownerName = pair.getSecond();

            // 修复：处理 ChatFormatting 字符串拼接
            String scoreString = ChatFormatting.RED.toString() + score.getScore();

            int currentY = startY - drawnCount * 9;
            int rightBound = this.screenWidth - 3 + 2;
            int leftBound = startX - 2;

            // 渲染单行背景
            guiGraphics.fill(leftBound, currentY, rightBound, currentY + 9, bgColorPrimary);
            // 渲染玩家名称
            guiGraphics.drawString(font, ownerName, startX, currentY, -1, false);
            // 渲染分数（靠右对齐）
            guiGraphics.drawString(font, scoreString, rightBound - font.width(scoreString), currentY, -1, false);

            // 7. 修复：在最后一次循环时渲染顶部的标题
            if (drawnCount == scoreCount) {
                guiGraphics.fill(leftBound, currentY - 9 - 1, rightBound, currentY - 1, bgColorSecondary);
                guiGraphics.fill(leftBound, currentY - 1, rightBound, currentY, bgColorPrimary);

                int titleX = startX + maxLineWidth / 2 - titleWidth / 2;
                guiGraphics.drawString(font, titleComponent, titleX, currentY - 9, -1, false);
            }
        }
        ci.cancel();

    }
}
