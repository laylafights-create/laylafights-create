package dev.lunarlite.module;

import dev.lunarlite.Compat;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Tự xoay camera (làm mượt SmoothDamp) về phía người chơi gần nhất. Gọi {@link #onRender} mỗi khung hình. */
public class AimAssistModule extends Module {
    private final SliderSetting range = add(new SliderSetting("aim_range", "Max Range", 6, 2, 12, 0.5, " khối"));
    private final SliderSetting fov = add(new SliderSetting("aim_fov", "Aim FOV", 120, 10, 360, 5, "°"));
    private final SliderSetting smoothing = add(new SliderSetting("aim_smooth", "Aim Smoothing", 0.25, 0.05, 1.0, 0.05, "x"));
    private final SliderSetting maxStep = add(new SliderSetting("aim_max_step", "Max Turn Speed", 12, 1, 60, 1, "°"));
    private final BoolSetting onlyClicking = add(new BoolSetting("aim_only_click", "Only While Attacking", false));
    private final BoolSetting ignoreTeam = add(new BoolSetting("aim_ignore_team", "Ignore Teammates", true));
    private final BoolSetting ignoreInvisible = add(new BoolSetting("aim_ignore_invisible", "Ignore Invisible", true));

    public AimAssistModule() {
        super("aimassist", "Aim Assist", "Tự động aim vào kẻ địch gần nhất (bỏ qua đồng đội).", Category.PVP, false);
    }

    private long lastNano, lastAim;
    private float yawVel, pitchVel;
    private Player current;

    public void onRender(float pt) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016F : Math.min(0.1F, (now - lastNano) / 1.0E9F);
        lastNano = now;
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (mc.level == null || me == null || Compat.screen() != null) return;
        if (onlyClicking.value && !mc.options.keyAttack.isDown()) return;
        if (me.isSpectator() || HypixelUtil.inLobby()) return;

        Player target = findTarget(mc, me);
        if (target == null) { yawVel = 0F; pitchVel = 0F; return; }
        if (now - lastAim > 150000000L) { yawVel = 0F; pitchVel = 0F; }
        lastAim = now;

        double ex = Mth.lerp(pt, me.xo, me.getX());
        double ey = Mth.lerp(pt, me.yo, me.getY()) + me.getEyeHeight();
        double ez = Mth.lerp(pt, me.zo, me.getZ());
        double tx = Mth.lerp(pt, target.xo, target.getX()) + (target.getX() - target.xo) * 0.8;
        double ty = Mth.lerp(pt, target.yo, target.getY());
        double tz = Mth.lerp(pt, target.zo, target.getZ()) + (target.getZ() - target.zo) * 0.8;

        double h = target.getBbHeight();
        double headY = ty + h * 0.9, chestY = ty + h * 0.6, feetY = ty + 0.25;
        double aimY = Math.max(ty + h * 0.35, Math.min(headY, ey));
        boolean upperVisible = canSee(mc, me, ex, ey, ez, tx, headY, tz) || canSee(mc, me, ex, ey, ez, tx, chestY, tz);
        if (!upperVisible && canSee(mc, me, ex, ey, ez, tx, feetY, tz)) aimY = feetY;

        double dx = tx - ex, dy = aimY - ey, dz = tz - ez;
        float wantYaw = yawTo(dx, dz);
        float wantPitch = (float) -(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0 / Math.PI);
        float curYaw = Mth.lerp(pt, me.yRotO, me.getYRot());
        float curPitch = Mth.lerp(pt, me.xRotO, me.getXRot());
        float yawDiff = Mth.wrapDegrees(wantYaw - curYaw);
        float pitchDiff = wantPitch - curPitch;

        float smoothTime = 0.04F + (1.0F - smoothing.getFloat()) * 0.40F;
        float maxSpeed = maxStep.getFloat() * 20.0F;
        float dyaw = damp(yawDiff, true, smoothTime, maxSpeed, dt);
        float dpitch = damp(pitchDiff, false, smoothTime, maxSpeed, dt);
        if (Math.abs(dyaw) < 0.002F && Math.abs(dpitch) < 0.002F) return;

        me.setYRot(me.getYRot() + dyaw);
        me.yRotO += dyaw;
        float newPitch = Mth.clamp(me.getXRot() + dpitch, -90F, 90F);
        me.xRotO += newPitch - me.getXRot();
        me.setXRot(newPitch);
    }

    private float damp(float diff, boolean isYaw, float smoothTime, float maxSpeed, float dt) {
        float omega = 2.0F / Math.max(0.0001F, smoothTime);
        float x = omega * dt;
        float exp = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        float vel = isYaw ? yawVel : pitchVel;
        float maxChange = maxSpeed * smoothTime;
        float change = Math.max(-maxChange, Math.min(maxChange, -diff));
        float temp = (vel + omega * change) * dt;
        vel = (vel - omega * temp) * exp;
        float step = -change + (change + temp) * exp;
        if ((diff > 0F && step > diff) || (diff < 0F && step < diff)) { step = diff; vel = 0F; }
        if (isYaw) yawVel = vel; else pitchVel = vel;
        return step;
    }

    private static boolean canSee(Minecraft mc, LocalPlayer me, double ex, double ey, double ez, double x, double y, double z) {
        return mc.level.clip(new ClipContext(new Vec3(ex, ey, ez), new Vec3(x, y, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me)).getType() == HitResult.Type.MISS;
    }

    private Player findTarget(Minecraft mc, LocalPlayer me) {
        Player best = null;
        double bestDist = range.get();
        for (Player p : mc.level.players()) {
            if (p == me || p.isRemoved() || p.getHealth() <= 0F || p.deathTime > 0) continue;
            if (ignoreInvisible.value && p.isInvisible()) continue;
            double dist = me.distanceTo(p) - (p == current ? 1.5 : 0.0);
            if (dist > bestDist) continue;
            float yaw = yawTo(p.getX() - me.getX(), p.getZ() - me.getZ());
            if (Math.abs(Mth.wrapDegrees(yaw - me.getYRot())) > fov.getFloat() / 2F) continue;
            if (p.isSpectator()) continue;
            if (!HypixelUtil.isRealPlayer(p)) continue;
            if (ignoreTeam.value && TeamUtil.isTeammate(me, p)) continue;
            bestDist = dist;
            best = p;
        }
        current = best;
        return best;
    }

    private static float yawTo(double dx, double dz) {
        return (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90F;
    }
}
