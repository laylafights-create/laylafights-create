package dev.lunarlite.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hiệu ứng vỡ vụn kiểu SAO khi sinh vật chết: các mảnh đa giác xanh băng bay toả ra, xoay, bay lên rồi tan.
 * Bản 1.8.9 vẽ tam giác 3D và ẩn thân xác; bản 26.2 chiếu từng mảnh ra màn hình ({@link Proj}) và vẽ tam giác 2D
 * trong lớp HUD, thân xác vanilla vẫn còn (chưa có hook để ẩn).
 */
public final class SaoShatter {
    private SaoShatter() {}

    public static boolean enabled = true;

    private static final int MAX = 700;
    private static final int[] PALETTE = {0xFFFFFF, 0xDDF6FF, 0xA8E6FF, 0x7FD3FF, 0xBFD9FF};
    private static final float[] OFF = {0f, 2.4f, 4.2f};
    private static final float[] RAD = {1f, 0.7f, 0.55f};
    private static final Random R = new Random();

    private static final class Shard {
        double x, y, z;
        float vx, vy, vz;
        float size, rot, spin, life;
        float ux, uy, uz, wx, wy, wz; // hai trục của mặt phẳng mảnh
        long born;
        int color;
    }

    private static final ArrayList<Shard> LIST = new ArrayList<>();

    public static void clear() { LIST.clear(); }

    /** Bung mảnh vỡ quanh thân sinh vật vừa bắt đầu chết; số mảnh theo cỡ cơ thể. */
    public static void burst(LivingEntity e) {
        if (!enabled || e == null) return;
        float w = Math.max(0.3f, e.getBbWidth()), h = Math.max(0.3f, e.getBbHeight());
        int n = Math.round(Math.min(48f, 14f + w * h * 14f));
        float body = 0.6f + 0.4f * Math.max(w, h);
        long now = System.currentTimeMillis();
        for (int i = 0; i < n; i++) {
            Shard s = new Shard();
            s.x = e.getX() + (R.nextFloat() - 0.5f) * w;
            s.y = e.getY() + R.nextFloat() * h;
            s.z = e.getZ() + (R.nextFloat() - 0.5f) * w;
            float dx = (float) (s.x - e.getX()), dz = (float) (s.z - e.getZ());
            float len = (float) Math.sqrt(dx * dx + dz * dz) + 1e-3f;
            float sp = 0.5f + R.nextFloat() * 1.6f;
            s.vx = dx / len * sp * 0.6f + (R.nextFloat() - 0.5f) * 0.6f;
            s.vz = dz / len * sp * 0.6f + (R.nextFloat() - 0.5f) * 0.6f;
            s.vy = 0.4f + R.nextFloat() * 1.4f;
            s.size = (0.05f + R.nextFloat() * R.nextFloat() * 0.22f) * body;
            s.rot = R.nextFloat() * 6.28f;
            s.spin = (R.nextFloat() - 0.5f) * 10f;
            s.life = 0.9f + R.nextFloat() * 1.1f;
            s.born = now;
            s.color = PALETTE[R.nextInt(PALETTE.length)];
            // mặt phẳng ngẫu nhiên: pháp tuyến n -> u = n x a, w = n x u
            float nx = R.nextFloat() - 0.5f, ny = R.nextFloat() - 0.5f, nz = R.nextFloat() - 0.5f;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-4f;
            nx /= nl; ny /= nl; nz /= nl;
            float ax = 0f, ay = 1f, az = 0f;
            if (Math.abs(ny) > 0.9f) { ax = 1f; ay = 0f; }
            float ux = ny * az - nz * ay, uy = nz * ax - nx * az, uz = nx * ay - ny * ax;
            float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz) + 1e-4f;
            ux /= ul; uy /= ul; uz /= ul;
            s.ux = ux; s.uy = uy; s.uz = uz;
            s.wx = ny * uz - nz * uy;
            s.wy = nz * ux - nx * uz;
            s.wz = nx * uy - ny * ux;
            LIST.add(s);
        }
        while (LIST.size() > MAX) LIST.remove(0);
    }

    /** Gọi mỗi khung hình trong lớp HUD, sau {@link Proj#setup}. */
    public static void render() {
        if (LIST.isEmpty() || !Proj.ready) return;
        long now = System.currentTimeMillis();
        for (Iterator<Shard> it = LIST.iterator(); it.hasNext(); ) {
            Shard s = it.next();
            float age = (now - s.born) / 1000f;
            if (age >= s.life) { it.remove(); continue; }
            float u = age / s.life;
            float k = 2.4f;
            float d = (1f - (float) Math.exp(-k * age)) / k;
            double px = s.x + s.vx * d, py = s.y + s.vy * d, pz = s.z + s.vz * d;
            int a = Math.round((float) Math.pow(1f - u, 1.2) * 0.95f * 255f);
            if (a < 6) continue;
            float rr0 = s.size * (1f - 0.3f * u);
            float[][] v = new float[3][];
            boolean ok = true;
            for (int j = 0; j < 3 && ok; j++) {
                float ang = s.rot + s.spin * age + OFF[j];
                float c = (float) Math.cos(ang) * rr0 * RAD[j], sn = (float) Math.sin(ang) * rr0 * RAD[j];
                v[j] = Proj.project(px + c * s.ux + sn * s.wx, py + c * s.uy + sn * s.wy, pz + c * s.uz + sn * s.wz);
                if (v[j] == null) ok = false;
            }
            if (!ok) continue;
            Draw.tri(v[0][0], v[0][1], v[1][0], v[1][1], v[2][0], v[2][1], Draw.withAlpha(s.color, a));
        }
    }
}
