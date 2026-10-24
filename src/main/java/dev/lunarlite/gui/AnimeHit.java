package dev.lunarlite.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hiệu ứng anime khi đánh trúng / hạ gục: chớp sao lớn, vệt chém, sóng tròn, cánh hoa anh đào và sao nhỏ.
 * Bản 1.8.9 vẽ quad 3D; ở 26.x không còn Tessellator nên mỗi hạt được chiếu từ toạ độ thế giới ra màn hình
 * (theo vị trí / góc nhìn / FOV của camera) rồi vẽ bằng {@link Draw} trong lớp HUD. Giới hạn: camera góc nhìn thứ 3
 * lệch nhẹ vì chỉ tính theo mắt người chơi; hạt không bị block che.
 */
public final class AnimeHit {
    private AnimeHit() {}

    private static final int FLASH = 0, SLASH = 1, RING = 2, PETAL = 3, STAR = 4;
    private static final int MAX = 400;
    private static final Random R = new Random();

    private static final class P {
        int type;
        double x, y, z;
        float vx, vy, vz, size, rot, spin, life, delay, g;
        long born;
        int c1, c2;
    }

    private static final ArrayList<P> LIST = new ArrayList<>();

    public static void clear() { LIST.clear(); }

    private static P make(int type, double x, double y, double z, float size, float life, float delay) {
        P p = new P();
        p.type = type;
        p.x = x; p.y = y; p.z = z;
        p.size = size; p.life = life; p.delay = delay;
        p.rot = R.nextFloat() * 6.2832f;
        p.born = System.currentTimeMillis();
        p.c1 = 0xFFFFFF;
        p.c2 = 0xFF7FB2;
        p.g = type == PETAL ? 0.9f : 0f;
        LIST.add(p);
        return p;
    }

    private static void radial(P p, Entity t, double sx, double sz, float speed, float spread) {
        float ox = (float) (sx - t.getX()), oz = (float) (sz - t.getZ());
        float ol = (float) Math.sqrt(ox * ox + oz * oz);
        if (ol < 0.02f) {
            float a = R.nextFloat() * 6.2832f;
            ox = (float) Math.cos(a);
            oz = (float) Math.sin(a);
            ol = 1f;
        }
        p.vx = ox / ol * speed + (R.nextFloat() - 0.5f) * spread;
        p.vz = oz / ol * speed + (R.nextFloat() - 0.5f) * spread;
    }

    /** Tạo hiệu ứng tại thân mục tiêu vừa bị đánh. amount: cường độ (nhân số hạt). */
    public static void spawn(LivingEntity t, float amount, boolean petals, boolean slash, boolean stars) {
        Minecraft mc = Minecraft.getInstance();
        if (t == null || mc.player == null) return;
        double dx = mc.player.getX() - t.getX(), dz = mc.player.getZ() - t.getZ();
        double dl = Math.sqrt(dx * dx + dz * dz) + 1e-4;
        double off = t.getBbWidth() * 0.5 + 0.2;
        double x = t.getX() + dx / dl * off, y = t.getY() + t.getBbHeight() * 0.65, z = t.getZ() + dz / dl * off;
        float body = Math.max(0.8f, Math.min(1.6f, t.getBbHeight() * 0.55f));

        make(FLASH, x, y, z, 0.95f * body, 0.30f, 0f);
        make(RING, x, y, z, 0.95f * body, 0.42f, 0f);
        if (slash) {
            float base = (R.nextBoolean() ? 1f : -1f) * (0.5f + R.nextFloat() * 0.5f);
            for (int i = 0; i < 3; i++) {
                P s = make(SLASH, x, y, z, (1.1f + R.nextFloat() * 0.5f) * body, 0.28f, i * 0.045f);
                s.rot = base + (i - 1) * 0.42f;
            }
        }
        float w = Math.max(0.4f, t.getBbWidth()), h = Math.max(0.4f, t.getBbHeight());
        if (petals) {
            int n = Math.round(14f * amount);
            for (int i = 0; i < n; i++) {
                double sx = t.getX() + (R.nextFloat() - 0.5f) * w, sy = t.getY() + R.nextFloat() * h,
                        sz = t.getZ() + (R.nextFloat() - 0.5f) * w;
                P p = make(PETAL, sx, sy, sz, 0.07f + R.nextFloat() * 0.06f, 0.9f + R.nextFloat() * 0.6f, 0f);
                radial(p, t, sx, sz, 1.6f + R.nextFloat() * 2.2f, 0.8f);
                p.vy = 0.3f + R.nextFloat() * 1.5f;
                p.spin = (R.nextFloat() - 0.5f) * 14f;
                p.c1 = 0xFFE9F1;
                p.c2 = 0xFF8FBB;
            }
        }
        if (stars) {
            int n = Math.round(9f * amount);
            for (int i = 0; i < n; i++) {
                double sx = t.getX() + (R.nextFloat() - 0.5f) * w, sy = t.getY() + R.nextFloat() * h,
                        sz = t.getZ() + (R.nextFloat() - 0.5f) * w;
                P p = make(STAR, sx, sy, sz, 0.09f + R.nextFloat() * 0.11f, 0.45f + R.nextFloat() * 0.45f, 0f);
                radial(p, t, sx, sz, 1.2f + R.nextFloat() * 1.8f, 0f);
                p.vy = (R.nextFloat() - 0.3f) * 1.2f;
                p.spin = (R.nextFloat() - 0.5f) * 6f;
                p.c2 = R.nextBoolean() ? 0xFFC93C : 0xFF7FB2;
            }
        }
        while (LIST.size() > MAX) LIST.remove(0);
    }

    /** Hiệu ứng chết: chớp lớn, chém chéo chữ X, sóng tròn, cánh hoa xoáy và sao bay lên như linh hồn. */
    public static void death(LivingEntity t, float amount) {
        if (t == null) return;
        float w = Math.max(0.4f, t.getBbWidth()), h = Math.max(0.5f, t.getBbHeight());
        double x = t.getX(), y = t.getY() + h * 0.55, z = t.getZ();
        float body = Math.max(1f, Math.min(2.2f, h * 0.7f));

        make(FLASH, x, y, z, 1.7f * body, 0.55f, 0f);
        make(RING, x, y, z, 1.6f * body, 0.7f, 0f);
        make(RING, x, y, z, 2.4f * body, 0.9f, 0.14f);
        for (int i = 0; i < 4; i++) {
            P s = make(SLASH, x, y, z, 1.9f * body, 0.34f, 0.06f + (i / 2) * 0.1f);
            s.rot = (i % 2 == 0 ? 0.785f : -0.785f) + (R.nextFloat() - 0.5f) * 0.2f;
        }
        int np = Math.round(26f * amount);
        for (int i = 0; i < np; i++) {
            float a = R.nextFloat() * 6.2832f, rad = w * 0.5f + R.nextFloat() * 0.3f;
            P p = make(PETAL, x + Math.cos(a) * rad, t.getY() + R.nextFloat() * h, z + Math.sin(a) * rad,
                    0.08f + R.nextFloat() * 0.07f, 1.3f + R.nextFloat() * 1.0f, R.nextFloat() * 0.15f);
            float sp = 1.0f + R.nextFloat() * 1.0f;
            p.vx = (float) (-Math.sin(a)) * sp + (float) Math.cos(a) * 0.6f;
            p.vz = (float) Math.cos(a) * sp + (float) Math.sin(a) * 0.6f;
            p.vy = 2.5f + R.nextFloat() * 2.5f;
            p.g = -0.6f;
            p.spin = (R.nextFloat() - 0.5f) * 12f;
            p.c1 = 0xFFE9F1;
            p.c2 = 0xFF8FBB;
        }
        int ns = Math.round(16f * amount);
        for (int i = 0; i < ns; i++) {
            float a = R.nextFloat() * 6.2832f, rad = R.nextFloat() * w * 0.6f;
            P p = make(STAR, x + Math.cos(a) * rad, t.getY() + R.nextFloat() * h, z + Math.sin(a) * rad,
                    0.10f + R.nextFloat() * 0.14f, 0.9f + R.nextFloat() * 0.8f, R.nextFloat() * 0.25f);
            p.vx = (R.nextFloat() - 0.5f) * 1.2f;
            p.vz = (R.nextFloat() - 0.5f) * 1.2f;
            p.vy = 2.0f + R.nextFloat() * 2.2f;
            p.spin = (R.nextFloat() - 0.5f) * 5f;
            p.c2 = R.nextBoolean() ? 0xFFC93C : (R.nextBoolean() ? 0xFF7FB2 : 0x9FE4FF);
        }
        while (LIST.size() > MAX) LIST.remove(0);
    }

    // ---------------------------------------------------------------- chiếu + vẽ

    private static double camX, camY, camZ, fx, fy, fz, rx, rz, ux, uy, uz, tanHalf;
    private static float halfH, midX, midY;

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** Chiếu điểm thế giới ra màn hình GUI: {x, y, pixel-mỗi-đơn-vị-thế-giới} hoặc null nếu ở sau camera. */
    private static float[] project(double wx, double wy, double wz) {
        double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.15) return null;
        double x = dx * rx + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        float k = (float) (halfH / (z * tanHalf));
        return new float[]{midX + (float) x * k, midY - (float) y * k, k};
    }

    /** Gọi mỗi khung hình trong lớp HUD (sau Draw.begin). */
    public static void render(float pt, int sw, int sh) {
        if (LIST.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (cam == null) return;
        long now = System.currentTimeMillis();
        for (Iterator<P> it = LIST.iterator(); it.hasNext(); ) {
            P p = it.next();
            if ((now - p.born) / 1000f >= p.life + p.delay) it.remove();
        }
        if (LIST.isEmpty()) return;

        camX = Mth.lerp(pt, cam.xo, cam.getX());
        camY = Mth.lerp(pt, cam.yo, cam.getY()) + cam.getEyeHeight();
        camZ = Mth.lerp(pt, cam.zo, cam.getZ());
        double yaw = Math.toRadians(cam.getViewYRot(pt)), pit = Math.toRadians(cam.getViewXRot(pt));
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pit), sp = Math.sin(pit);
        fx = -sy * cp; fy = -sp; fz = cy * cp;       // hướng nhìn
        rx = -cy; rz = -sy;                           // trục phải (nằm ngang)
        // up = right × forward (right có thành phần y = 0)
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        halfH = sh / 2f;
        midX = sw / 2f;
        midY = sh / 2f;

        for (int pass = 0; pass < 2; pass++) { // cánh hoa trước, chớp / chém / sao đè lên
            for (P p : LIST) {
                if ((p.type == PETAL) != (pass == 0)) continue;
                float age = (now - p.born) / 1000f - p.delay;
                if (age < 0f || age >= p.life) continue;
                draw(p, age);
            }
        }
    }

    private static int argb(int rgb, float a) {
        int al = Math.round(255f * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    private static void draw(P p, float age) {
        float u = age / p.life, fade = 1f - u;
        double d = (1f - Math.exp(-3.0 * age)) / 3.0;
        double wx = p.x + p.vx * d, wz = p.z + p.vz * d, wy = p.y + p.vy * d - p.g * age * age;
        float[] s = project(wx, wy, wz);
        if (s == null) return;
        float sx = s[0], sy = s[1], k = s[2];
        switch (p.type) {
            case FLASH: {
                float r = p.size * (0.35f + 0.65f * ease(u * 3.2f)) * k;
                float a = (float) Math.pow(fade, 1.4);
                Draw.disc(sx, sy, r * 0.35f, argb(0xFFFFFF, a * 0.55f), argb(p.c2, 0f));
                star(sx, sy, r, p.rot + u * 0.6f, 0xFFFFFF, a);
                star(sx, sy, r * 0.6f, p.rot + 0.785f, 0xFFC4DE, a * 0.9f);
                break;
            }
            case SLASH: {
                float len = p.size * ease(age / 0.09f) * k;
                float cs = (float) Math.cos(p.rot), sn = (float) Math.sin(p.rot);
                taper(sx, sy, sx + cs * len, sy + sn * len, 2.2f, 0xFFFFFF, fade);
                taper(sx, sy, sx - cs * len, sy - sn * len, 2.2f, 0xFFFFFF, fade);
                taper(sx, sy, sx + cs * len, sy + sn * len, 4.5f, p.c2, fade * 0.35f);
                taper(sx, sy, sx - cs * len, sy - sn * len, 4.5f, p.c2, fade * 0.35f);
                break;
            }
            case RING: {
                float r1 = (p.size * 0.15f + p.size * 0.85f * ease(u)) * k;
                float r2 = r1 + Math.max(1f, (0.06f * (1f - u) + 0.01f) * k);
                Draw.circleRing(sx, sy, r1, r2, argb(p.c2, fade * 0.85f));
                break;
            }
            case PETAL: {
                float a = Math.min(1f, Math.min(u * 10f, fade * 3f)) * 0.95f;
                float flut = 0.35f + 0.65f * Math.abs((float) Math.cos(age * 7f + p.rot));
                float r = Math.max(1.2f, p.size * k * flut);
                Draw.disc(sx, sy, r, argb(p.c1, a), argb(p.c2, a));
                break;
            }
            case STAR: {
                float tw = 0.6f + 0.4f * (float) Math.sin(age * 22f + p.rot);
                float r = p.size * (0.5f + 0.5f * fade) * k;
                star(sx, sy, Math.max(2f, r), p.rot + p.spin * age, p.c2, fade * tw);
                break;
            }
            default:
                break;
        }
    }

    /** Sao 4 cánh: mỗi cánh là một nét thon dần ra đầu. */
    private static void star(float cx, float cy, float r, float rot, int rgb, float a) {
        for (int k = 0; k < 4; k++) {
            double ang = rot + k * Math.PI / 2.0;
            taper(cx, cy, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, Math.max(1.5f, r * 0.22f), rgb, a);
        }
    }

    /** Nét thon dần và mờ dần từ (x0,y0) tới (x1,y1). */
    private static void taper(float x0, float y0, float x1, float y1, float w0, int rgb, float a) {
        final int seg = 6;
        for (int i = 0; i < seg; i++) {
            float t0 = (float) i / seg, t1 = (float) (i + 1) / seg;
            float w = Math.max(1f, w0 * (1f - t0));
            Draw.line(x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0, x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1, w,
                    argb(rgb, a * (1f - t0 * 0.8f)));
        }
    }
}
