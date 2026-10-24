package dev.lunarlite.module;

import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Giảm knockback: nhân vận tốc đúng một lần ngay tick bị trúng đòn. Không có HUD. */
public class VelocityModule extends Module {
    private final SliderSetting horizontal = add(new SliderSetting("vel_h", "Horizontal", 80, 0, 100, 1, "%"));
    private final SliderSetting vertical = add(new SliderSetting("vel_v", "Vertical", 100, 0, 100, 1, "%"));
    private int lastHurt;

    public VelocityModule() {
        super("velocity", "Velocity", "Giảm lực đẩy (knockback) khi bị đánh theo tỉ lệ ngang / dọc.", Category.PVP, false);
    }

    @Override
    public void onTick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        int ht = p.hurtTime;
        boolean fresh = ht > 0 && ht >= lastHurt && ht == p.hurtDuration;
        lastHurt = ht;
        if (!enabled || !fresh || HypixelUtil.inLobby()) return;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(v.x * horizontal.get() / 100.0, v.y * vertical.get() / 100.0, v.z * horizontal.get() / 100.0);
    }
}
