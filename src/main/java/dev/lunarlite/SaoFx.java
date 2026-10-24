package dev.lunarlite;

import dev.lunarlite.gui.SaoDeath;
import dev.lunarlite.gui.SaoDeathScreen;
import dev.lunarlite.gui.SaoShatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Âm thanh + hiệu ứng SAO: tiếng chết / cảnh báo máu lặp của bản thân, vỡ mảnh + tiếng chết của sinh vật khác,
 * thay màn hình chết bằng "YOU ARE DEAD". Port từ phần SAO trong LunarLite.java của bản 1.8.9.
 */
public final class SaoFx {
    private SaoFx() {}

    public static boolean deathFx = true, shatterFx = true;

    private static final Map<String, SoundEvent> EVENTS = new HashMap<>();
    private static boolean sfxDead;
    private static SoundInstance lowSound;
    private static final Map<Integer, LivingEntity> tracked = new HashMap<>();
    private static final Map<Integer, Integer> lastHurt = new HashMap<>();
    private static final Set<Integer> fired = new HashSet<>();
    private static int fxTick, playedThisTick;

    public static SoundEvent event(String name) {
        return EVENTS.computeIfAbsent(name, n -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(LunarLite.MODID, n)));
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("ZhwiiClient");
    private static final Set<String> warned = new HashSet<>();

    /** Âm UI của mod (menu.open, menu.click...). Nếu sounds.json không nạp được thì dùng âm vanilla để vẫn có tiếng. */
    public static void ui(String name, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        try {
            SimpleSoundInstance inst = SimpleSoundInstance.forUI(event(name), pitch);
            if (resolves(name, inst, mc)) {
                mc.getSoundManager().play(inst);
                return;
            }
            if (warned.add(name)) LOG.warn("[Zhwii] Không tìm thấy sound event zhwii:{} (sounds.json / .ogg chưa nạp?) - dùng âm vanilla", name);
        } catch (RuntimeException | LinkageError e) {
            if (warned.add(name)) LOG.warn("[Zhwii] Lỗi phát âm zhwii:" + name + " - dùng âm vanilla", e);
        }
        try {
            boolean chime = name.equals("menu.open") || name.equals("menu.select") || name.equals("menu.on") || name.equals("alert.enemy");
            net.minecraft.sounds.SoundEvent ev = chime
                    ? net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value()
                    : net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value();
            float p = switch (name) {
                case "menu.open" -> 1.6f;
                case "menu.close", "menu.off" -> 0.8f;
                case "menu.select" -> 1.3f;
                case "menu.on" -> 1.5f;
                case "menu.hover" -> 1.9f;
                case "alert.enemy" -> 1.5f;
                case "menu.back" -> 0.85f;
                default -> 1.2f;
            };
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, p * pitch));
        } catch (RuntimeException | LinkageError ignored) { }
    }

    private static java.lang.reflect.Method resolveM;
    private static boolean resolveTried;
    private static final Set<String> okSounds = new HashSet<>();

    /** Gọi {@code SoundInstance#resolve(SoundManager)} (tìm hàm một lần, nhớ tên âm đã nạp được): false = sound event chưa nạp. */
    private static boolean resolves(String name, SoundInstance inst, Minecraft mc) {
        if (okSounds.contains(name)) return true;
        try {
            if (!resolveTried) {
                resolveTried = true;
                Class<?> smc = mc.getSoundManager().getClass();
                for (java.lang.reflect.Method m : inst.getClass().getMethods()) {
                    if (m.getName().equals("resolve") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isAssignableFrom(smc)) {
                        resolveM = m;
                        break;
                    }
                }
            }
            if (resolveM == null) return true;
            if (resolveM.invoke(inst, mc.getSoundManager()) != null) {
                okSounds.add(name);
                return true;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        return true;
    }

    /** Âm lặp không suy giảm theo khoảng cách (cảnh báo máu thấp). */
    private static final class LoopSound extends AbstractSoundInstance {
        LoopSound(SoundEvent ev) {
            super(ev, SoundSource.MASTER, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }
    }

    public static void onTick(Minecraft mc) {
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null) {
            sfxDead = false;
            lowSound = null;
            SaoDeath.update(false);
            SaoShatter.clear();
            tracked.clear(); lastHurt.clear(); fired.clear();
            return;
        }
        SaoDeath.enabled = deathFx;
        SaoShatter.enabled = shatterFx;
        playHealthSounds(mc, me);
        playNearbyDeaths(mc, me);
        if (SaoDeath.active() && Compat.screen() instanceof DeathScreen) Compat.setScreen(new SaoDeathScreen());
    }

    private static void playHealthSounds(Minecraft mc, LocalPlayer me) {
        boolean dead = me.getHealth() <= 0f || me.isDeadOrDying();
        SaoDeath.update(dead);
        if (dead) {
            stopLow(mc);
            if (!sfxDead) { sfxDead = true; ui("sao.death", 1f); }
            return;
        }
        sfxDead = false;
        if (me.getHealth() < 10f) {
            if (lowSound == null || !mc.getSoundManager().isActive(lowSound)) {
                lowSound = new LoopSound(event("sao.critical_health"));
                mc.getSoundManager().play(lowSound);
            }
        } else {
            stopLow(mc);
        }
    }

    private static void stopLow(Minecraft mc) {
        if (lowSound != null) { mc.getSoundManager().stop(lowSound); lowSound = null; }
    }

    private static boolean isDying(LivingEntity e) { return e.deathTime > 0 || e.getHealth() <= 0f; }

    private static void triggerDeath(Minecraft mc, LocalPlayer me, LivingEntity e) {
        double d2 = e.distanceToSqr(me);
        if (d2 > 64.0 * 64.0) return;
        if (shatterFx) SaoShatter.burst(e);
        if (playedThisTick < 3 && d2 <= 48.0 * 48.0) {
            mc.getSoundManager().play(new SimpleSoundInstance(event("sao.death"), SoundSource.MASTER, 0.9f, 1f,
                    RandomSource.create(), e.getX(), e.getY(), e.getZ()));
            playedThisTick++;
        }
    }

    /** Nhận diện chết: máu về 0 / deathTime > 0, hoặc thực thể biến mất ngay sau khi bị đánh hay máu thấp (server như Hypixel). */
    private static void playNearbyDeaths(Minecraft mc, LocalPlayer me) {
        fxTick++;
        playedThisTick = 0;
        Set<Integer> present = new HashSet<>();
        for (Entity o : mc.level.entitiesForRendering()) {
            if (!(o instanceof LivingEntity e) || o == me || o instanceof ArmorStand) continue;
            int id = e.getId();
            present.add(id);
            tracked.put(id, e);
            if (e.hurtTime > 0) lastHurt.put(id, fxTick);
            if (!fired.contains(id) && (isDying(e) || e.isRemoved())) {
                fired.add(id);
                triggerDeath(mc, me, e);
            }
        }
        for (Iterator<Map.Entry<Integer, LivingEntity>> it = tracked.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, LivingEntity> en = it.next();
            int id = en.getKey();
            if (present.contains(id)) continue;
            LivingEntity e = en.getValue();
            it.remove();
            Integer lh = lastHurt.remove(id);
            boolean already = fired.remove(id);
            if (already || e == null) continue;
            boolean recentlyHit = lh != null && fxTick - lh <= 20;
            boolean near = e.distanceToSqr(me) <= 24.0 * 24.0;
            if (near && (recentlyHit || e.getHealth() <= 4f)) triggerDeath(mc, me, e);
        }
        if (fxTick % 200 == 0) fired.retainAll(present);
    }
}
