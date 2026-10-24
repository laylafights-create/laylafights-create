package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import dev.lunarlite.gui.AnimeHit;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Hiệu ứng anime khi đánh trúng kẻ địch (và khi hạ gục). Không có HUD. */
public class HitEffectModule extends Module {
    private final BoolSetting petals = add(new BoolSetting("hit_petals", "Cánh hoa anh đào", true));
    private final BoolSetting slash = add(new BoolSetting("hit_slash", "Vệt chém", true));
    private final BoolSetting stars = add(new BoolSetting("hit_stars", "Sao nhỏ", true));
    private final BoolSetting deathFx = add(new BoolSetting("hit_death", "Hiệu ứng khi hạ gục", true));
    private final SliderSetting amount = add(new SliderSetting("hit_amount", "Cường độ", 1.0, 0.3, 2.0, 0.1, "x"));

    public static HitEffectModule INSTANCE;
    private LivingEntity last;
    private long lastHit;
    private boolean deathDone;

    public HitEffectModule() {
        super("hiteffect", "Hit Effect", "Hiệu ứng anime khi đánh trúng: chớp sao, vệt chém, cánh hoa.", Category.EFFECT, true);
        INSTANCE = this;
    }

    public void onAttack(Entity e) {
        if (!enabled || !(e instanceof LivingEntity t)) return;
        AnimeHit.spawn(t, amount.getFloat(), petals.value, slash.value, stars.value);
        last = t;
        lastHit = System.currentTimeMillis();
        deathDone = false;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { AnimeHit.clear(); last = null; return; }
        if (!enabled) return;
        if (last != null && !deathDone && deathFx.value) {
            if (System.currentTimeMillis() - lastHit > 4000L) { last = null; return; }
            if (last.isDeadOrDying() || last.isRemoved() || last.getHealth() <= 0f) {
                AnimeHit.death(last, amount.getFloat());
                deathDone = true;
                last = null;
            }
        }
    }

    @Override
    public void onToggle() {
        if (!enabled) AnimeHit.clear();
    }
}
