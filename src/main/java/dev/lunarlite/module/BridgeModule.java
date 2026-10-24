package dev.lunarlite.module;

import dev.lunarlite.Compat;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Hỗ trợ bridge nhanh (bridge lùi): ngồi ở mép, chỉnh góc nhìn xuống mặt bên block, tự đặt block. Không có HUD. */
public class BridgeModule extends Module {
    private final BoolSetting edgeCrouch = add(new BoolSetting("bridge_crouch", "Edge Crouch", true));
    private final BoolSetting aim = add(new BoolSetting("bridge_aim", "Bridge Aim", true));
    private final BoolSetting autoPlace = add(new BoolSetting("bridge_place", "Auto Place", true));
    private final BoolSetting autoRefill = add(new BoolSetting("bridge_refill", "Auto Refill Blocks", true));
    private final BoolSetting onlyBlocks = add(new BoolSetting("bridge_blocks", "Only Holding Blocks", true));
    private final SliderSetting minPitch = add(new SliderSetting("bridge_min_pitch", "Min Pitch", 60, 40, 85, 1, "°"));
    private final SliderSetting smoothing = add(new SliderSetting("bridge_smooth", "Aim Smoothing", 0.5, 0.05, 1.0, 0.05, "x"));
    private final SliderSetting placeDelay = add(new SliderSetting("bridge_delay", "Place Delay", 1, 0, 4, 1, " tick"));
    private final BoolSetting climbUp = add(new BoolSetting("bridge_climb", "Jump Climb (đặt lên cao)", true));
    private final BoolSetting fallSafety = add(new BoolSetting("bridge_safety", "Fall Safety (bắt mép khi rơi)", true));

    private boolean forcingSneak;
    private int blockSlot = -1;
    private int cooldown;
    private long lastNano, lastAim;
    private float yawVel, pitchVel;

    public BridgeModule() {
        super("bridge", "Bridge Assist", "Hỗ trợ bridge nhanh: tự ngồi ở mép, chỉnh góc ngắm và đặt block.", Category.PVP, false);
    }

    private static final class Pose {
        BlockPos support;
        Direction dir;
        double beyond, dy, dyTop, dyBot;
        boolean climb;
        double climbH;
    }

    private boolean bridging(Minecraft mc) { return mc.options.keyDown.isDown(); }

    private boolean active(Minecraft mc, LocalPlayer p) {
        if (!enabled || mc.level == null || p == null || Compat.screen() != null || p.isSpectator()) return false;
        if (onlyBlocks.value && !(p.getMainHandItem().getItem() instanceof BlockItem)) return false;
        return true;
    }

    private boolean replaceable(Minecraft mc, BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced();
    }

    private Pose pose(Minecraft mc, LocalPlayer p, float pt) {
        Direction dir = p.getDirection().getOpposite();
        double px = Mth.lerp(pt, p.xo, p.getX()), py = Mth.lerp(pt, p.yo, p.getY()), pz = Mth.lerp(pt, p.zo, p.getZ());
        int fx = Mth.floor(px - dir.getStepX() * 0.3), fz = Mth.floor(pz - dir.getStepZ() * 0.3);
        int fy = Mth.floor(py - 0.01);
        int maxDrop = p.onGround() ? 0 : 3;
        BlockPos sup = null;
        for (int i = 0; i <= maxDrop && sup == null; i++) {
            BlockPos c = new BlockPos(fx, fy - i, fz);
            if (!replaceable(mc, c)) { sup = c; break; }
            if (!p.onGround()) {
                BlockPos side = c.relative(dir.getOpposite());
                if (!replaceable(mc, side)) { sup = side; break; }
            }
        }
        if (sup == null) return null;
        BlockPos back = sup.relative(dir);
        boolean climb = false;
        if (!mc.level.getBlockState(back).isAir()) {
            boolean solid = !replaceable(mc, back);
            if (!solid || !climbUp.value || p.onGround() || !mc.level.getBlockState(back.above()).isAir()) return null;
            climb = true;
        }
        double ex = px, ey = py + p.getEyeHeight(), ez = pz;
        double plane = dir.getStepX() != 0
                ? sup.getX() + (dir.getStepX() > 0 ? 1.0 : 0.0)
                : sup.getZ() + (dir.getStepZ() > 0 ? 1.0 : 0.0);
        double beyond = dir.getStepX() != 0 ? (ex - plane) * dir.getStepX() : (ez - plane) * dir.getStepZ();
        Pose o = new Pose();
        o.support = sup;
        o.dir = dir;
        o.beyond = beyond;
        o.dy = (sup.getY() + 0.75) - ey;
        o.dyTop = (sup.getY() + 0.95) - ey;
        o.dyBot = (sup.getY() + 0.25) - ey;
        o.climb = climb;
        o.climbH = (back.getY() + 1.0) - ey;
        return o;
    }

    private boolean usable(Minecraft mc, ItemStack s) {
        if (s.isEmpty() || !(s.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        boolean full = Block.isShapeFullBlock(b.defaultBlockState().getCollisionShape(mc.level, BlockPos.ZERO));
        return full && !(b instanceof FallingBlock) && !(b instanceof BaseEntityBlock) && !(b instanceof TntBlock);
    }

    /** Tay vừa hết block (đang giữ S) thì đổi sang ô hotbar có block. */
    private void tickRefill(Minecraft mc, LocalPlayer p) {
        if (!autoRefill.value || !enabled || mc.level == null || p == null || Compat.screen() != null || p.isSpectator()) return;
        ItemStack h = p.getMainHandItem();
        int cur = p.getInventory().getSelectedSlot();
        if (!h.isEmpty()) { blockSlot = h.getItem() instanceof BlockItem ? cur : -1; return; }
        if (blockSlot != cur || !bridging(mc)) return;
        int best = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (usable(mc, s) && (best < 0 || s.getCount() > p.getInventory().getItem(best).getCount())) best = i;
        }
        if (best >= 0) { p.getInventory().setSelectedSlot(best); blockSlot = best; } else blockSlot = -1;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (cooldown > 0) cooldown--;
        tickRefill(mc, p);
        if (!active(mc, p)) { releaseSneak(mc); return; }

        if (edgeCrouch.value && bridging(mc) && p.onGround() && !p.getAbilities().flying) {
            var mv = p.getDeltaMovement();
            AABB box = p.getBoundingBox().move(mv.x * 3.0, -0.5, mv.z * 3.0);
            boolean edge = mc.level.noCollision(p, box);
            if (edge) { mc.options.keyShift.setDown(true); forcingSneak = true; }
            else releaseSneak(mc);
        } else releaseSneak(mc);

        if (!autoPlace.value || !bridging(mc) || cooldown > 0 || p.getMainHandItem().isEmpty()) return;
        float pitchTolerance = p.onGround() ? 5.0F : 10.0F;
        if (p.getXRot() < minPitch.getFloat() - pitchTolerance) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos hp = hit.getBlockPos();
        Direction side = hit.getDirection();
        if (side.getAxis().isVertical()) {
            if (side != Direction.UP || p.onGround() || !climbUp.value) return;
        }
        if (hp.getY() >= Mth.floor(p.getY())) return;
        BlockPos target = hp.relative(side);
        if (!mc.level.getBlockState(target).isAir()) return;
        AABB cell = new AABB(target.getX(), target.getY(), target.getZ(), target.getX() + 1.0, target.getY() + 1.0, target.getZ() + 1.0);
        if (p.getBoundingBox().intersects(cell)) return;
        InteractionResult r = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hit);
        if (r.consumesAction()) {
            p.swing(InteractionHand.MAIN_HAND);
            cooldown = p.onGround() ? placeDelay.getInt() : 0;
        }
    }

    /** Mỗi khung hình: chỉnh pitch (và yaw về trục gần nhất) cho tâm ngắm trúng mặt bên của block. */
    public void onRender(float pt) {
        long now = System.nanoTime();
        float dt = lastNano == 0L ? 0.016F : Math.min(0.1F, (now - lastNano) / 1.0E9F);
        lastNano = now;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!aim.value || !active(mc, p)) return;
        if (p.getAbilities().flying || p.isInWater() || p.onClimbable()) return;
        boolean air = !p.onGround();
        if (!bridging(mc)) { yawVel = 0F; pitchVel = 0F; return; }
        if (p.getXRot() < minPitch.getFloat() - (air ? 8.0F : 3.0F)) return;

        Pose o = pose(mc, p, pt);
        double maxBeyond = o != null && o.climb ? 1.2 : (air ? 0.55 : 0.4);
        if (o == null || o.beyond < 0.02 || o.beyond > maxBeyond) { yawVel = 0F; pitchVel = 0F; return; }
        double urgency = air ? Mth.clamp(o.beyond / maxBeyond, 0.0, 1.0) : 0.0;
        if (now - lastAim > 150000000L) { yawVel = 0F; pitchVel = 0F; }
        lastAim = now;

        float curPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        float wantPitch;
        if (o.climb) {
            double h = -o.climbH;
            double tFar = Math.min(0.95, o.beyond * 0.9);
            float pLow = (float) (Math.atan2(h, Math.max(0.02, o.beyond - 0.05)) * 180.0 / Math.PI);
            float pHigh = (float) (Math.atan2(h, Math.max(0.02, o.beyond - tFar)) * 180.0 / Math.PI);
            wantPitch = Mth.clamp(curPitch, pLow, pHigh);
        } else if (air) {
            float pTop = (float) (Math.atan2(-o.dyTop, o.beyond) * 180.0 / Math.PI);
            float pBot = (float) (Math.atan2(-o.dyBot, o.beyond) * 180.0 / Math.PI);
            wantPitch = Mth.clamp(curPitch, pTop, pBot);
        } else {
            wantPitch = (float) (Math.atan2(-o.dy, o.beyond) * 180.0 / Math.PI);
        }
        wantPitch = Mth.clamp(wantPitch, minPitch.getFloat(), 89.0F);
        float curYaw = Mth.lerp(pt, p.yRotO, p.getYRot());
        float wantYaw = o.dir.getOpposite().get2DDataValue() * 90.0F;
        float yawDiff = Mth.wrapDegrees(wantYaw - curYaw);
        if (Math.abs(yawDiff) > 25.0F) { yawDiff = 0F; yawVel = 0F; }

        float smoothTime = 0.04F + (1.0F - smoothing.getFloat()) * 0.40F;
        if (air) smoothTime *= 0.45F;
        if (fallSafety.value && urgency > 0.5) {
            float urgentScale = 1.0F - (float) (urgency - 0.5) * 1.5F;
            smoothTime *= Mth.clamp(urgentScale, 0.25F, 1.0F);
        }
        float dyaw = damp(yawDiff, true, smoothTime, 600.0F, dt);
        float dpitch = damp(wantPitch - curPitch, false, smoothTime, 600.0F, dt);
        if (Math.abs(dyaw) < 0.002F && Math.abs(dpitch) < 0.002F) return;

        p.setYRot(p.getYRot() + dyaw);
        p.yRotO += dyaw;
        float newPitch = Mth.clamp(p.getXRot() + dpitch, -90F, 90F);
        p.xRotO += newPitch - p.getXRot();
        p.setXRot(newPitch);
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

    private void releaseSneak(Minecraft mc) {
        if (!forcingSneak) return;
        forcingSneak = false;
        mc.options.keyShift.setDown(false);
    }

    @Override
    public void onToggle() {
        if (!enabled) releaseSneak(Minecraft.getInstance());
        yawVel = 0F;
        pitchVel = 0F;
        blockSlot = -1;
    }
}
