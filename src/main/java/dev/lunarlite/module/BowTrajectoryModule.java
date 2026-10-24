package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.gui.Proj;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bow Trajectory: vẽ đường bay + điểm rơi dự đoán của mũi tên khi đang giương cung.
 * Chỉ HIỂN THỊ. Bản 26.2 vẽ 2D (chiếu từ thế giới) nên đường bay luôn hiện xuyên vật cản ở phía trước camera.
 */
public class BowTrajectoryModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFF));
    private final ColorSetting hitColor = add(new ColorSetting("hit_color", "Hit Color", 0xFF5555));
    private final SliderSetting width = add(new SliderSetting("width", "Line Width", 2.0, 1.0, 6.0, 0.5, "px"));
    private final SliderSetting maxTicks = add(new SliderSetting("max_ticks", "Max Flight Time", 120, 40, 200, 10, " tick"));
    private final BoolSetting onlyDrawing = add(new BoolSetting("only_drawing", "Only When Drawing", true));
    private final BoolSetting marker = add(new BoolSetting("marker", "Landing Marker", true));
    private final BoolSetting highlight = add(new BoolSetting("highlight", "Highlight Target", true));

    public BowTrajectoryModule() {
        super("bowtrajectory", "Bow Trajectory", "Hiện đường bay và điểm rơi của mũi tên khi giương cung.", Category.PVP, false);
        new RainbowSettings(this);
    }

    /** Gọi mỗi khung hình trong lớp HUD (sau {@link Proj#setup}). */
    public void overlay(float pt) {
        if (!enabled || !Proj.ready) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer pl = mc.player;
        Level world = mc.level;
        if (pl == null || world == null) return;
        ItemStack held = pl.isUsingItem() ? pl.getUseItem() : pl.getMainHandItem();
        if (!(held.getItem() instanceof BowItem) && !held.is(Items.BOW)) return;

        float power;
        if (pl.isUsingItem()) {
            int charge = held.getUseDuration(pl) - pl.getUseItemRemainingTicks();
            power = BowItem.getPowerForTime(charge);
            if (power < 0.1f) return;
        } else if (onlyDrawing.value) {
            return;
        } else {
            power = 1f;
        }

        double yr = Math.toRadians(Mth.lerp(pt, pl.yRotO, pl.getYRot()));
        double pr = Math.toRadians(Mth.lerp(pt, pl.xRotO, pl.getXRot()));
        double x = Mth.lerp(pt, pl.xo, pl.getX());
        double y = Mth.lerp(pt, pl.yo, pl.getY()) + pl.getEyeHeight() - 0.1;
        double z = Mth.lerp(pt, pl.zo, pl.getZ());
        double mx = -Math.sin(yr) * Math.cos(pr), my = -Math.sin(pr), mz = Math.cos(yr) * Math.cos(pr);
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        double speed = power * 3.0;
        Vec3 pv = pl.getDeltaMovement();
        mx = mx / len * speed + pv.x;
        my = my / len * speed + (pl.onGround() ? 0.0 : pv.y);
        mz = mz / len * speed + pv.z;

        List<Vec3> pts = new ArrayList<>();
        pts.add(new Vec3(x, y, z));
        Entity hitEntity = null;
        Vec3 landing = null;
        int ticks = maxTicks.getInt();
        for (int i = 0; i < ticks; i++) {
            Vec3 from = new Vec3(x, y, z), to = new Vec3(x + mx, y + my, z + mz);
            var blockHit = world.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
            Vec3 end = blockHit.getType() != HitResult.Type.MISS ? blockHit.getLocation() : to;

            double best = from.distanceTo(end);
            Entity found = null;
            Vec3 foundVec = null;
            AABB sweep = new AABB(x, y, z, x + mx, y + my, z + mz).inflate(1.0);
            for (Entity en : world.getEntities(pl, sweep, e -> e.isPickable() && !e.isSpectator())) {
                Optional<Vec3> ip = en.getBoundingBox().inflate(0.3).clip(from, end);
                if (ip.isPresent()) {
                    double d = from.distanceTo(ip.get());
                    if (d <= best) { best = d; found = en; foundVec = ip.get(); }
                }
            }
            if (found != null) { hitEntity = found; landing = foundVec; pts.add(foundVec); break; }
            if (blockHit.getType() != HitResult.Type.MISS) { landing = blockHit.getLocation(); pts.add(landing); break; }
            x += mx; y += my; z += mz;
            pts.add(new Vec3(x, y, z));
            mx *= 0.99; my *= 0.99; mz *= 0.99;
            my -= 0.05;
            if (y < world.getMinY() - 16) break;
        }

        boolean hitsTarget = hitEntity != null && highlight.value;
        int rgb = hitsTarget ? hitColor.rgb() : (rainbow != null ? rainbow.resolve(color.rgb(), 0f) & 0xFFFFFF : color.rgb());
        float w = width.getFloat();
        for (int i = 0; i + 1 < pts.size(); i++) {
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            int c = Draw.withAlpha(rgb, 0xCC);
            if (rainbow != null && rainbow.active() && !hitsTarget) c = rainbow.color(i * 6f);
            Proj.line(a.x, a.y, a.z, b.x, b.y, b.z, w, c);
        }
        if (landing != null && marker.value) drawMarker(landing, rgb);
        if (hitsTarget) {
            AABB bb = hitEntity.getBoundingBox();
            box(bb, Draw.withAlpha(hitColor.rgb(), 0xDD));
        }
    }

    private void drawMarker(Vec3 p, int rgb) {
        float[] s = Proj.project(p.x, p.y, p.z);
        if (s == null) return;
        float r = Math.max(2.5f, Math.min(10f, 0.25f * s[2]));
        int c = Draw.withAlpha(rgb, 0xDD);
        Draw.circleRing(s[0], s[1], r - 1f, r, c);
        Draw.disc(s[0], s[1], Math.max(1f, r * 0.25f), c);
    }

    static void box(AABB bb, int argb) {
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                int j = i | bit;
                Proj.line((i & 1) == 0 ? bb.minX : bb.maxX, ((i >> 1) & 1) == 0 ? bb.minY : bb.maxY, ((i >> 2) & 1) == 0 ? bb.minZ : bb.maxZ,
                        (j & 1) == 0 ? bb.minX : bb.maxX, ((j >> 1) & 1) == 0 ? bb.minY : bb.maxY, ((j >> 2) & 1) == 0 ? bb.minZ : bb.maxZ,
                        1f, argb);
            }
        }
    }
}
