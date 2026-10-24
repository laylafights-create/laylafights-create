package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Sáng tối đa. 26.x không cho gamma vượt 100% nếu không dùng mixin, nên module tự gắn hiệu ứng Night Vision
 * vô hạn phía client (ẩn icon/hạt). Tắt module thì gỡ hiệu ứng đó.
 */
public class FullbrightModule extends Module {
    private boolean applied;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Sáng tối đa, nhìn rõ trong hang.", Category.VISUAL, false);
    }

    public boolean active() { return enabled && applied; }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            applied = false;
            return;
        }
        if (enabled) {
            MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
            if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < 400)) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                        MobEffectInstance.INFINITE_DURATION, 0, false, false, false));
            }
            applied = true;
        } else if (applied) {
            MobEffectInstance cur = mc.player.getEffect(MobEffects.NIGHT_VISION);
            if (cur != null && cur.isInfiniteDuration()) mc.player.removeEffect(MobEffects.NIGHT_VISION);
            applied = false;
        }
    }
}
