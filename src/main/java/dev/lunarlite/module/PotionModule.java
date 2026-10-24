package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Hiệu ứng thuốc dạng gọn: mỗi hiệu ứng một hàng gồm icon, thời gian còn lại mm:ss và một đường kẻ mảnh
 * phía dưới. Sắp hết (&lt; 30s chữ vàng, &lt; 10s đỏ + nhấp nháy).
 */
public class PotionModule extends HudModule {
    private static final int PAD = 5, MIN_W = 50, ICON = 16, GAP = 5, LINE_H = 2;
    private static final int C_WARN = 0xFFFFC857, C_DANGER = 0xFFFF5C5C;
    private static final int LOW_TICKS = 200, MID_TICKS = 600;

    private final BoolSetting showDuration = add(new BoolSetting("duration", "Show Duration", true));
    private final BoolSetting effectColors = add(new BoolSetting("effect_colors", "Use Effect Colors", true));
    private final BoolSetting showIcon = add(new BoolSetting("icon", "Show Icon", true));
    private final BoolSetting showBar = add(new BoolSetting("bar", "Duration Bar", true));
    private final BoolSetting warnLow = add(new BoolSetting("warn_low", "Expiry Warning", true));
    private final BoolSetting sortTime = add(new BoolSetting("sort_time", "Sort By Time Left", false));

    private static final class Row {
        final Holder<MobEffect> effect;
        final int dur, amp, rgb;
        final boolean infinite;
        final String time;

        Row(Holder<MobEffect> effect, int dur, int amp, int rgb, boolean infinite) {
            this.effect = effect;
            this.dur = dur;
            this.amp = amp;
            this.rgb = rgb;
            this.infinite = infinite;
            this.time = infinite ? "**:**" : fmt(dur);
        }
    }

    public PotionModule() {
        super("potions", "Potion Status", "Hiệu ứng thuốc đang có, thời gian còn lại và cảnh báo sắp hết.",
                Category.PVP, true, 400, 60);
    }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    private static String fmt(int ticks) {
        int s = Math.max(0, ticks) / 20;
        int h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0 ? h + ":" + two(m) + ":" + two(sec) : two(m) + ":" + two(sec);
    }

    private List<Row> rows(boolean preview) {
        List<Row> list = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (preview || mc.player == null) {
            list.add(new Row(MobEffects.SPEED, 20 * 88, 1, 0x7CAFC6, false));
            list.add(new Row(MobEffects.STRENGTH, 20 * 25, 0, 0x932423, false));
            return list;
        }
        boolean hideNv = LunarLite.FULLBRIGHT != null && LunarLite.FULLBRIGHT.active();
        for (MobEffectInstance inst : mc.player.getActiveEffects()) {
            Holder<MobEffect> eff = inst.getEffect();
            if (hideNv && eff.is(MobEffects.NIGHT_VISION) && inst.isInfiniteDuration()) continue;
            list.add(new Row(eff, inst.getDuration(), inst.getAmplifier(), eff.value().getColor() & 0xFFFFFF,
                    inst.isInfiniteDuration()));
        }
        if (sortTime.value) {
            list.sort(Comparator.comparingInt(r -> r.infinite ? Integer.MAX_VALUE : r.dur));
        }
        return list;
    }

    private int rowH() { return Math.max(ICON, 10) + (showBar.value ? LINE_H + 2 : 0) + 2; }

    @Override
    public int getWidth(boolean preview) {
        List<Row> r = rows(preview);
        if (r.isEmpty()) return 0;
        int w = MIN_W;
        for (Row e : r) {
            int rw = PAD * 2 + (showIcon.value ? ICON + GAP : 0) + (showDuration.value ? Draw.width(e.time) : 0);
            w = Math.max(w, rw);
        }
        return w;
    }

    @Override
    public int getHeight(boolean preview) {
        List<Row> r = rows(preview);
        return r.isEmpty() ? 0 : PAD * 2 - 2 + r.size() * rowH();
    }

    /** Sprite icon hiệu ứng: textures/gui/sprites/mob_effect/&lt;tên&gt;.png */
    private static Identifier sprite(Holder<MobEffect> effect) {
        Identifier id = Identifier.tryParse(effect.getRegisteredName());
        if (id == null) return null;
        return Identifier.fromNamespaceAndPath(id.getNamespace(), "mob_effect/" + id.getPath());
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        List<Row> list = rows(preview);
        if (list.isEmpty()) return;
        int w = getWidth(preview), h = getHeight(preview);
        drawBox(x, y, w, h);
        int ry = y + PAD - 1;
        long t = System.currentTimeMillis();
        for (Row r : list) {
            int tx = x + PAD;
            if (showIcon.value) {
                Identifier sp = sprite(r.effect);
                if (sp != null) Draw.g.blitSprite(RenderPipelines.GUI_TEXTURED, sp, tx, ry, ICON, ICON);
                tx += ICON + GAP;
            }
            int col = effectColors.value ? (0xFF000000 | r.rgb) : (0xFF000000 | color.rgb());
            boolean low = !r.infinite && warnLow.value && r.dur < LOW_TICKS;
            boolean mid = !r.infinite && warnLow.value && r.dur < MID_TICKS;
            if (low) col = (t / 250 % 2 == 0) ? C_DANGER : Draw.mulAlpha(C_DANGER, 0.45f);
            else if (mid) col = C_WARN;
            if (showDuration.value) {
                if (rainbow.active() && !low && !mid) rainbow.drawString(r.time, tx, ry + (ICON - 8) / 2f, color.rgb(), shadow.value, ry);
                else Draw.text(r.time, tx, ry + (ICON - 8) / 2f, col, shadow.value);
            }
            if (showBar.value) {
                float frac = r.infinite ? 1f : Math.min(1f, r.dur / (20f * 60f * 8f));
                Draw.hbar(x + PAD, ry + ICON + 1, w - PAD * 2, LINE_H, frac, col, 0x44000000);
            }
            ry += rowH();
        }
    }
}
