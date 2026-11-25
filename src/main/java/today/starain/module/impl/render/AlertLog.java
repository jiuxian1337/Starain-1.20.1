package today.starain.module.impl.render;

import io.github.humbleui.skija.Font;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import today.starain.event.api.EventType;
import today.starain.event.api.annotations.EventTarget;
import today.starain.event.impl.GameTickEvent;
import today.starain.event.impl.RenderSkiaEvent;
import today.starain.module.Category;
import today.starain.module.ClientModule;
import today.starain.ui.Design;
import today.starain.ui.Theme;
import today.starain.utils.render.skia.Skia;
import today.starain.utils.render.skia.font.Fonts;
import today.starain.utils.render.skia.font.Icon;
import today.starain.value.impl.BooleanValue;
import today.starain.value.impl.NumberValue;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 提示日志：目前包含攻击日志和受伤日志，后续可通过 {@link #push} 接入回弹、矿物刷新等提示。
 */
public class AlertLog extends ClientModule {

    public enum Kind {
        ATTACK(Icon.SWORDS), HURT(Icon.HEART_BROKEN), INFO(Icon.INFO);

        final String icon;

        Kind(String icon) {
            this.icon = icon;
        }
    }

    private final BooleanValue attackLog = new BooleanValue("Attack Log", true, this);
    private final BooleanValue hurtLog = new BooleanValue("Hurt Log", true, this);
    private final BooleanValue playersOnly = new BooleanValue("Players Only", true, this);
    private final NumberValue x = new NumberValue("X", 0, 1000, 10, 1, this);
    private final NumberValue y = new NumberValue("Y", 0, 1000, 120, 1, this);
    private final NumberValue duration = new NumberValue("Duration", 1.0, 10.0, 4.0, 0.5, this);
    private final NumberValue maxLines = new NumberValue("Max Lines", 1, 15, 6, 1, this);
    private final NumberValue scale = new NumberValue("Scale", 0.5, 2.0, 1.0, 0.05, this);

    private static final float HEIGHT = 16.0f, GAP = 3.0f, RADIUS = 5.0f;

    private final List<Entry> entries = new ArrayList<>();
    private long lastFrame;

    private LivingEntity tracked;
    private float trackedHealth;
    private int lastAttackStamp = Integer.MIN_VALUE;
    private int lastHurtTime;
    private float lastSelfHealth = -1.0f;

    private static final class Entry {
        final Kind kind;
        final String text;
        final float hold;
        float remaining;
        float show;

        Entry(Kind kind, String text, float hold) {
            this.kind = kind;
            this.text = text;
            this.hold = this.remaining = hold;
        }
    }

    public AlertLog() {
        setName("AlertLog");
        setCategory(Category.RENDER);
    }

    @Override
    protected void onEnable() {
        tracked = null;
        lastSelfHealth = -1.0f;
        lastAttackStamp = mc.player == null ? Integer.MIN_VALUE : mc.player.getLastHurtMobTimestamp();
    }

    public void push(Kind kind, String text) {
        entries.add(new Entry(kind, text, duration.get().floatValue() * 1000.0f));
        while (entries.size() > Math.max(1, maxLines.get().intValue())) entries.remove(0);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (event.getType() != EventType.PRE || mc.player == null || mc.level == null) return;

        // 攻击：Player.attack 会更新 lastHurtMob 与时间戳
        LivingEntity hurtMob = mc.player.getLastHurtMob();
        int stamp = mc.player.getLastHurtMobTimestamp();
        if (hurtMob != null && stamp != lastAttackStamp && valid(hurtMob)) {
            lastAttackStamp = stamp;
            if (tracked != hurtMob) {
                tracked = hurtMob;
                trackedHealth = hurtMob.getHealth();
            }
            if (attackLog.get()) {
                push(Kind.ATTACK, String.format(Locale.ROOT, "Attacked %s  %.1fm",
                        hurtMob.getName().getString(), mc.player.distanceTo(hurtMob)));
            }
        }
        if (tracked != null) {
            if (!tracked.isAlive() || tracked.isRemoved()) {
                if (attackLog.get() && tracked.isDeadOrDying()) push(Kind.ATTACK, "Killed " + tracked.getName().getString());
                tracked = null;
            } else {
                float hp = tracked.getHealth();
                if (attackLog.get() && hp < trackedHealth - 0.01f) {
                    push(Kind.ATTACK, String.format(Locale.ROOT, "Hit %s  -%.1f  HP %.1f",
                            tracked.getName().getString(), trackedHealth - hp, hp));
                }
                trackedHealth = hp;
            }
        }

        // 受伤：hurtTime 从 0 跳到最大值的那一刻
        float selfHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (hurtLog.get() && mc.player.hurtTime > lastHurtTime && mc.player.hurtTime == mc.player.hurtDuration) {
            float lost = lastSelfHealth < 0 ? 0.0f : Math.max(0.0f, lastSelfHealth - selfHp);
            DamageSource source = mc.player.getLastDamageSource();
            Entity attacker = source == null ? null : source.getEntity();
            String who = attacker != null ? attacker.getName().getString()
                    : source != null ? source.getMsgId() : "Unknown";
            push(Kind.HURT, String.format(Locale.ROOT, "Hurt by %s  -%.1f  HP %.1f", who, lost, selfHp));
        }
        lastHurtTime = mc.player.hurtTime;
        lastSelfHealth = selfHp;
    }

    private boolean valid(LivingEntity entity) {
        return entity != mc.player && (!playersOnly.get() || entity instanceof Player);
    }

    @EventTarget
    public void onRender(RenderSkiaEvent event) {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0f : Math.min((now - lastFrame) / 1_000_000.0f, 100.0f);
        lastFrame = now;
        if (entries.isEmpty()) return;

        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            e.remaining -= delta;
            boolean expired = e.remaining <= 0.0f;
            e.show += ((expired ? 0.0f : 1.0f) - e.show) * Math.min(1.0f, delta / (expired ? 200.0f : 250.0f) * 4.0f);
            if (expired && e.show < 0.01f) it.remove();
        }
        if (entries.isEmpty()) return;

        float bx = x.get().floatValue(), by = y.get().floatValue();
        Font font = Fonts.getMedium(7.0f);
        Font icon = Fonts.getIconFill(8.0f);
        Skia.beginHudScale(bx, by, scale.get().floatValue());
        try {
            float cursor = by;
            for (Entry e : entries) {
                float a = Math.max(0.0f, Math.min(1.0f, e.show));
                float w = 24.0f + Skia.getTextWidth(e.text, font);
                float ex = bx - (1.0f - a) * 20.0f;
                Color accent = e.kind == Kind.HURT ? new Color(255, 90, 90) : Theme.accent((int) (-cursor * 0.1f));
                if (a > 0.3f) {
                    Skia.drawShadow(ex, cursor, w, HEIGHT, RADIUS);
                    Skia.drawBlur(ex, cursor, w, HEIGHT, RADIUS);
                }
                Skia.setAlpha((int) (a * 255));
                Skia.drawRoundedRect(ex, cursor, w, HEIGHT, RADIUS, Design.BACKGROUND_COLOR);
                Skia.drawRoundedRect(ex + 3.0f, cursor + 4.0f, 1.5f, HEIGHT - 8.0f, 0.75f, accent);
                Skia.drawText(e.kind.icon, ex + 7.0f, cursor + 4.5f, accent, icon);
                Skia.drawText(e.text, ex + 18.0f, cursor + 5.0f, Design.TEXT_COLOR, font);
                Skia.restore();
                cursor += (HEIGHT + GAP) * a;
            }
        } finally {
            Skia.endHudScale();
        }
    }
}

