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
import today.starain.ui.HudDrag;

/**
 * 提示日志：目前包含攻击日志和受伤日志，后续可通过 {@link #push} 接入回弹、矿物刷新等提示。
 */
public class AlertLog extends ClientModule {

    public enum Kind {
        ATTACK(Icon.SWORDS), HURT(Icon.FAVORITE), INFO(Icon.INFO);

        final String icon;

        Kind(String icon) {
            this.icon = icon;
        }
    }

    private final BooleanValue attackLog = new BooleanValue("Attack Log", true, this);
    private final BooleanValue hurtLog = new BooleanValue("Hurt Log", true, this);
    private final BooleanValue playersOnly = new BooleanValue("Players Only", true, this);
    private final NumberValue x = new NumberValue("X", 0, 1000, 10, 1, this, () -> false);
    private final NumberValue y = new NumberValue("Y", 0, 1000, 120, 1, this, () -> false);
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

    /** 一段带颜色的文字；color 为 null 表示跟随全局主题色（玩家名）。 */
    public record Seg(String text, Color color) {
    }

    private static final Color PLAIN = new Color(-1, true);
    private static final Color DAMAGE = new Color(255, 105, 105);

    private static final class Entry {
        final Kind kind;
        final List<Seg> segs;
        final float hold;
        float remaining;
        float show;

        Entry(Kind kind, List<Seg> segs, float hold) {
            this.kind = kind;
            this.segs = segs;
            this.hold = this.remaining = hold;
        }
    }

    private static Seg text(String text) {
        return new Seg(text, PLAIN);
    }

    private static final Color HEART = new Color(255, 85, 105);

    // 爱心图标代替 HP 文字，用图标字体绘制
    private static Seg heart() {
        return new Seg(Icon.FAVORITE + " ", HEART);
    }

    // 独立间隔段，绘制时按固定宽度前进，避免字体把尾随空格吞掉
    private static final Seg GAP_SEG = new Seg(" ", new Color(0, 0, 0, 0));

    private static Seg gap() {
        return GAP_SEG;
    }

    private static Seg name(String name) {
        return new Seg(name, null);
    }

    // 血量按比例着色：高绿、中黄、低红
    private static Seg hp(float hp, float max) {
        float ratio = max <= 0 ? 0 : hp / max;
        Color c = ratio >= 0.6f ? new Color(110, 230, 130) : ratio >= 0.3f ? new Color(255, 205, 90) : new Color(255, 95, 95);
        return new Seg(String.format(Locale.ROOT, "%.1f", hp), c);
    }

    // 距离着色：近绿、中黄、远红
    private static Seg dist(float d) {
        Color c = d <= 3.0f ? new Color(110, 230, 130) : d <= 4.5f ? new Color(255, 205, 90) : new Color(255, 95, 95);
        return new Seg(String.format(Locale.ROOT, "%.1fm", d), c);
    }

    private static Seg damage(float amount) {
        return new Seg(String.format(Locale.ROOT, "-%.1f", amount), DAMAGE);
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
        push(kind, List.of(text(text)));
    }

    public void push(Kind kind, List<Seg> segs) {
        entries.add(new Entry(kind, segs, duration.get().floatValue() * 1000.0f));
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
                push(Kind.ATTACK, List.of(text("Attack"), gap(), name(hurtMob.getName().getString()),
                        text("  "), dist(mc.player.distanceTo(hurtMob))));
            }
        }
        if (tracked != null) {
            if (!tracked.isAlive() || tracked.isRemoved()) {
                if (attackLog.get() && tracked.isDeadOrDying()) {
                    push(Kind.ATTACK, List.of(text("Killed "), name(tracked.getName().getString())));
                }
                tracked = null;
            } else {
                float hp = tracked.getHealth();
                if (attackLog.get() && hp < trackedHealth - 0.01f) {
                    push(Kind.ATTACK, List.of(text("Hit "), name(tracked.getName().getString()),
                            text("  "), damage(trackedHealth - hp), text("  "), heart(), hp(hp, tracked.getMaxHealth()),
                            text("  "), dist(mc.player.distanceTo(tracked))));
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
            List<Seg> segs = new ArrayList<>();
            segs.add(text("Hurt by "));
            if (attacker != null) {
                segs.add(name(attacker.getName().getString()));
            } else {
                segs.add(text(source != null ? source.getMsgId() : "Unknown"));
            }
            segs.add(text("  "));
            segs.add(damage(lost));
            segs.add(text("  "));
            segs.add(heart());
            segs.add(hp(selfHp, mc.player.getMaxHealth()));
            if (attacker != null) {
                segs.add(text("  "));
                segs.add(dist(mc.player.distanceTo(attacker)));
            }
            push(Kind.HURT, segs);
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
        if (entries.isEmpty() && HudDrag.isEditing()) {
            // 拖拽编辑时没有日志也给一条示例，方便定位
            Entry sample = new Entry(Kind.INFO, List.of(text("Hit "), name("Player"), text("  "), damage(2.5f), text("  "), heart(), hp(12.0f, 20.0f), text("  "), dist(3.2f)), 1.0f);
            sample.show = 1.0f;
            drawEntries(List.of(sample));
            return;
        }
        if (entries.isEmpty()) return;

        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            e.remaining -= delta;
            boolean expired = e.remaining <= 0.0f;
            e.show += ((expired ? 0.0f : 1.0f) - e.show) * Math.min(1.0f, delta / (expired ? 200.0f : 250.0f) * 4.0f);
            if (expired && e.show < 0.01f) it.remove();
        }
        if (entries.isEmpty()) return;
        drawEntries(entries);
    }

    private void drawEntries(List<Entry> entries) {
        float bx = x.get().floatValue(), by = y.get().floatValue();
        Font font = Fonts.getMedium(7.0f);
        Font icon = Fonts.getIconFill(8.0f);
        Skia.beginHudScale(bx, by, scale.get().floatValue());
        try {
            float cursor = by;
            float maxW = 0.0f;
            for (Entry e : entries) {
                float a = Math.max(0.0f, Math.min(1.0f, e.show));
                float textW = 0.0f;
                for (Seg seg : e.segs) textW += seg == GAP_SEG ? 3.0f : Skia.getTextWidth(seg.text(), seg.color() == HEART ? icon : font);
                float w = 24.0f + textW;
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
                float tx = ex + 18.0f;
                for (Seg seg : e.segs) {
                    Color c = seg.color() == null ? Theme.accent((int) (tx * 0.5f)) : seg.color() == PLAIN ? Design.TEXT_COLOR : seg.color();
                    if (seg == GAP_SEG) {
                        tx += 3.0f;
                        continue;
                    }
                    Font f = seg.color() == HEART ? icon : font;
                    Skia.drawText(seg.text(), tx, cursor + (f == icon ? 4.5f : 5.0f), c, f);
                    tx += Skia.getTextWidth(seg.text(), f);
                }
                Skia.restore();
                maxW = Math.max(maxW, w);
                cursor += (HEIGHT + GAP) * a;
            }
            HudDrag.report(this, x, y, bx, by, maxW, Math.max(cursor - by - GAP, HEIGHT));
        } finally {
            Skia.endHudScale();
        }
    }
}

