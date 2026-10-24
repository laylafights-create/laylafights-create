package dev.lunarlite.module;

import dev.lunarlite.gui.Proj;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.RainbowSettings;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Viền block đang nhìn vào đổi màu cầu vồng: Solid / Gradient (mỗi góc một màu) / Pulse / Custom.
 * Bản 26.2 vẽ 2D trong lớp HUD (chiếu từ thế giới). "Through Walls" tắt thì các đoạn bị block khác che sẽ được ẩn.
 * Viền đen mặc định của game vẫn còn bên dưới (chưa có hook để huỷ).
 */
public class BlockOutlineModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", 1, "Solid", "Gradient", "Pulse", "Custom"));
    private final SliderSetting width = add(new SliderSetting("width", "Line Width", 3.0, 1.0, 10.0, 0.5, ""));
    private final SliderSetting brightness = add(new SliderSetting("brightness", "Brightness", 1.0, 0.2, 1.0, 0.05, ""));
    private final SliderSetting speed = add(new SliderSetting("speed", "Speed", 1.0, 0.1, 5.0, 0.1, "x"));
    private final SliderSetting opacity = add(new SliderSetting("opacity", "Opacity", 100, 10, 100, 5, "%"));
    private final BoolSetting throughWalls = add(new BoolSetting("through_walls", "Through Walls", false));
    private final SliderSetting red = add(new SliderSetting("custom_red", "Custom Red", 255, 0, 255, 5, ""));
    private final SliderSetting green = add(new SliderSetting("custom_green", "Custom Green", 255, 0, 255, 5, ""));
    private final SliderSetting blue = add(new SliderSetting("custom_blue", "Custom Blue", 255, 0, 255, 5, ""));

    public BlockOutlineModule() {
        super("blockoutline", "Block Outline", "Viền block đang nhìn vào đổi màu cầu vồng.", Category.VISUAL, true);
    }

    private int cornerColor(int corner) {
        double now = (System.currentTimeMillis() % 400000L) / 4000.0 * speed.value;
        float bri = brightness.getFloat();
        float alpha = opacity.getFloat() / 100f;
        int rgb;
        if (style.is("Custom")) {
            rgb = ((int) (red.value * bri) << 16) | ((int) (green.value * bri) << 8) | (int) (blue.value * bri);
        } else {
            double h = now;
            if (style.is("Gradient")) h -= ((corner & 1) + ((corner >> 1) & 1) + ((corner >> 2) & 1)) * 0.18;
            h -= Math.floor(h);
            rgb = RainbowSettings.hsb((float) h, 1f, bri) & 0xFFFFFF;
        }
        if (style.is("Pulse")) {
            double s = Math.sin(System.currentTimeMillis() / 1000.0 * speed.value * 4.0);
            alpha *= 0.35f + 0.65f * (0.5f + 0.5f * (float) s);
        }
        return (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f) << 24) | rgb;
    }

    /** Gọi mỗi khung hình trong lớp HUD (sau {@link Proj#setup}). */
    public void overlay(float pt) {
        if (!enabled || !Proj.ready) return;
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null || mc.player == null || !(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState st = level.getBlockState(pos);
        if (st.isAir() || !level.getWorldBorder().isWithinBounds(pos)) return;
        VoxelShape shape = st.getShape(level, pos, CollisionContext.of(mc.player));
        if (shape.isEmpty()) return;
        AABB bb = shape.bounds().move(pos).inflate(0.002);
        float w = width.getFloat();
        Vec3 cam = new Vec3(Proj.camX, Proj.camY, Proj.camZ);
        final int seg = throughWalls.value ? 1 : 8;
        for (int i = 0; i < 8; i++) {
            for (int bit = 1; bit <= 4; bit <<= 1) {
                if ((i & bit) != 0) continue;
                int j = i | bit;
                double ax = (i & 1) == 0 ? bb.minX : bb.maxX, ay = ((i >> 1) & 1) == 0 ? bb.minY : bb.maxY, az = ((i >> 2) & 1) == 0 ? bb.minZ : bb.maxZ;
                double bx = (j & 1) == 0 ? bb.minX : bb.maxX, by = ((j >> 1) & 1) == 0 ? bb.minY : bb.maxY, bz = ((j >> 2) & 1) == 0 ? bb.minZ : bb.maxZ;
                for (int s = 0; s < seg; s++) {
                    double t0 = (double) s / seg, t1 = (double) (s + 1) / seg, tm = (t0 + t1) / 2;
                    if (!throughWalls.value && hidden(level, mc, cam, pos, ax + (bx - ax) * tm, ay + (by - ay) * tm, az + (bz - az) * tm)) continue;
                    int c = Dummy.mix(cornerColor(i), cornerColor(j), (float) tm);
                    Proj.line(ax + (bx - ax) * t0, ay + (by - ay) * t0, az + (bz - az) * t0,
                            ax + (bx - ax) * t1, ay + (by - ay) * t1, az + (bz - az) * t1, w, c);
                }
            }
        }
    }

    private static boolean hidden(Level level, Minecraft mc, Vec3 cam, BlockPos target, double x, double y, double z) {
        Vec3 p = new Vec3(x, y, z);
        double dist = cam.distanceTo(p);
        if (dist < 0.05) return false;
        BlockHitResult r = level.clip(new ClipContext(cam, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (r.getType() == HitResult.Type.MISS) return false;
        if (r.getBlockPos().equals(target)) return false;
        return cam.distanceTo(r.getLocation()) < dist - 0.03;
    }

    private static final class Dummy {
        static int mix(int a, int b, float t) { return dev.lunarlite.gui.Draw.lerp(a, b, t); }
    }
}
