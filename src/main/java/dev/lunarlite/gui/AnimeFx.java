package dev.lunarlite.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hiệu ứng kiểu anime cho menu (bản 26.2): cánh hoa anh đào rơi, sao lấp lánh 4 cánh, tia sáng chéo,
 * vạch tốc độ khi mở menu, vệt sáng quét qua thẻ khi rê chuột, nổ sao + sóng tròn khi bấm.
 * Vẽ bằng {@link Draw} (tam giác / đĩa / vòng quét dòng), toạ độ màn hình hoặc khung ảo đều được.
 * Khác bản 1.8.9: không có trộn cộng sáng (additive) và không có màu chuyển theo từng đỉnh, nên
 * dải màu / quầng sáng được xấp xỉ bằng các lớp alpha.
 */
public final class AnimeFx {
    private AnimeFx() {}

    private static final int PETAL_IN = 0xFFFFE9F1;
    private static final int PETAL_OUT = 0xFFFF9EC4;

    private static int al(int rgb, float a) {
        int i = Math.round(255f * Math.max(0f, Math.min(1f, a)));
        return (i << 24) | (rgb & 0xFFFFFF);
    }

    private static float hash(int i, int salt) {
        long h = (i * 73856093L) ^ (salt * 19349663L) ^ 0x9E3779B9L;
        h *= 2654435761L;
        return ((h >>> 8) & 0xFFFF) / 65535f;
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ---------------------------------------------------------------- nền

    /** Toàn bộ lớp nền: tia sáng, hoa rơi, sao lấp lánh. {@code op} = độ mở (0..1). */
    public static void backdrop(int w, int h, float op, int accentRgb) {
        long t = System.currentTimeMillis();
        rays(w, h, op, t);
        petals(w, h, op, t);
        sparkles(w, h, op, t, accentRgb);
    }

    /** Vài dải sáng chéo từ góc trên phải; mờ dần xuống dưới bằng các lát ngang. */
    private static void rays(int w, int h, float op, long t) {
        final int slices = 10;
        for (int i = 0; i < 4; i++) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(t / 2600.0 + i * 1.7);
            float x = w * (0.35f + 0.17f * i);
            float bw = w * (0.05f + 0.025f * (i % 2));
            float slant = h * 0.55f;
            int rgb = i % 2 == 0 ? 0xFFE0EC : 0xDFF6FF;
            for (int k = 0; k < slices; k++) {
                float y0 = h * k / (float) slices, y1 = h * (k + 1) / (float) slices;
                float a = 0.13f * pulse * op * (1f - (k + 0.5f) / slices);
                float o0 = -slant * (y0 / h), o1 = -slant * (y1 / h);
                int c = al(rgb, a);
                Draw.tri(x + o0, y0, x + bw + o0, y0, x + bw + o1, y1, c);
                Draw.tri(x + o0, y0, x + bw + o1, y1, x + o1, y1, c);
            }
        }
    }

    /** Cánh hoa anh đào: xoay, lật, đung đưa, rơi chéo từ phải trên xuống trái dưới. */
    private static void petals(int w, int h, float op, long t) {
        final int n = 26;
        for (int i = 0; i < n; i++) {
            int layer = i % 3;
            float per = (11000f + hash(i, 1) * 9000f) / (0.7f + 0.45f * layer);
            float life = (t / per + hash(i, 2)) % 1f;
            float fx = hash(i, 3);
            float gust = (float) Math.sin(t / 3200.0 + life * 5.0) * (6f + 8f * layer);
            float px = fx * (w + w * 0.35f) - life * w * 0.35f + (float) Math.sin(t / 1300.0 + i * 2.1) * 16f + gust;
            float py = -12f + life * (h + 24f);
            float fade = Math.min(1f, Math.min(life * 7f, (1f - life) * 7f));
            float a = fade * (0.55f + 0.2f * layer) * op;
            if (a < 0.03f) continue;
            float size = (3.2f + hash(i, 4) * 3.6f) * (0.7f + 0.4f * layer);
            float rot = (float) (t / (700.0 + hash(i, 5) * 700.0) + i);
            float flut = 0.35f + 0.65f * Math.abs((float) Math.cos(t / (900.0 + hash(i, 6) * 600.0) + i));
            float cr = (float) Math.cos(rot), sr = (float) Math.sin(rot);
            final int seg = 9;
            float[] pts = new float[seg * 2];
            for (int k = 0; k < seg; k++) {
                double ang = Math.PI * 2.0 * k / seg;
                float lx = (float) Math.cos(ang) * size;
                float ly = (float) Math.sin(ang) * size * 0.62f * flut;
                if (Math.cos(ang) > 0.85) lx -= size * 0.28f;
                pts[2 * k] = px + lx * cr - ly * sr;
                pts[2 * k + 1] = py + lx * sr + ly * cr;
            }
            Draw.poly(pts, al(Draw.lerp(PETAL_OUT, PETAL_IN, 0.35f), a));
        }
    }

    /** Sao 4 cánh nhấp nháy rải khắp màn hình. */
    private static void sparkles(int w, int h, float op, long t, int accentRgb) {
        for (int i = 0; i < 16; i++) {
            float per = 2200f + hash(i, 11) * 2600f;
            float ph = (t / per + hash(i, 12)) % 1f;
            float tw = (float) Math.sin(Math.PI * ph);
            tw *= tw;
            float a = tw * op;
            if (a < 0.05f) continue;
            int cycle = (int) (t / per + hash(i, 12));
            float x = hash(i * 31 + cycle, 13) * w;
            float y = hash(i * 17 + cycle, 14) * h;
            float r = 3f + hash(i, 15) * 5f;
            star(x, y, r * (0.5f + 0.5f * tw), i % 3 == 0 ? accentRgb : 0xFFFFFF, a);
        }
    }

    // ---------------------------------------------------------------- hình cơ bản

    public static void star(float cx, float cy, float r, int rgb, float a) {
        star(cx, cy, r, rgb, a, true);
    }

    /** Sao bốn cánh (hai hình thoi mảnh) kèm quầng sáng nhỏ ở tâm. {@code additive} giữ lại cho tương thích. */
    public static void star(float cx, float cy, float r, int rgb, float a, boolean additive) {
        if (r <= 0.3f || a <= 0.01f) return;
        float th = r * 0.16f;
        int c = al(rgb, a * 0.8f);
        // dọc
        Draw.tri(cx, cy - r, cx + th, cy, cx - th, cy, c);
        Draw.tri(cx, cy + r, cx + th, cy, cx - th, cy, c);
        // ngang
        Draw.tri(cx - r, cy, cx, cy - th, cx, cy + th, c);
        Draw.tri(cx + r, cy, cx, cy - th, cx, cy + th, c);
        Draw.disc(cx, cy, r * 0.55f, al(additive ? 0xFFFFFF : rgb, a * 0.45f), al(rgb, 0f));
    }

    // ---------------------------------------------------------------- mở menu

    /** Vạch tốc độ toả ra từ giữa màn hình trong ~0.45s đầu khi mở menu. */
    public static void speedLines(int w, int h, long sinceOpenMs) {
        float p = sinceOpenMs / 450f;
        if (p <= 0f || p >= 1f) return;
        float cx = w / 2f, cy = h / 2f;
        float diag = (float) Math.sqrt(cx * cx + cy * cy);
        float e = 1f - (1f - p) * (1f - p);
        for (int i = 0; i < 44; i++) {
            float ang = (float) (Math.PI * 2.0 * i / 44.0) + (hash(i, 21) - 0.5f) * 0.12f;
            float len = diag * (0.10f + 0.22f * hash(i, 22));
            float r0 = diag * (0.18f + 0.62f * e) * (0.75f + 0.5f * hash(i, 23));
            float r1 = r0 + len * (1f - p * 0.5f);
            float cs = (float) Math.cos(ang), sn = (float) Math.sin(ang);
            float wd = 0.9f + hash(i, 24) * 1.1f;
            float a = (1f - p) * 0.55f;
            float ox = -sn * wd, oy = cs * wd;
            Draw.tri(cx + cs * r0 + ox, cy + sn * r0 + oy, cx + cs * r0 - ox, cy + sn * r0 - oy,
                    cx + cs * r1, cy + sn * r1, al(i % 3 == 0 ? 0xFFC4DE : 0xFFFFFF, a * 0.6f));
        }
    }

    // ---------------------------------------------------------------- rê chuột

    /**
     * Vệt sáng chéo quét qua thẻ một lần khi rê chuột vào ({@code hv} chạy 0 -> 1 nên không cần lưu trạng thái),
     * bị kẹp trong khung chữ nhật của thẻ. Vẽ theo từng cặp dòng, mỗi dòng 4 dải alpha tăng dần ra giữa.
     */
    public static void shine(int x, int y, int w, int h, float hv) {
        if (hv <= 0.03f || hv >= 0.97f || w < 12 || h < 8) return;
        float a = (float) Math.sin(Math.PI * hv) * 0.62f;
        float cxm = x + (hv * 1.5f - 0.25f) * w;
        float slant = h * 0.55f;
        float bw = w * 0.2f;
        float lo = x + 1, hi = x + w - 1;
        for (int yy = y + 1; yy < y + h - 1; yy += 2) {
            float centre = cxm + slant * (0.5f - (yy - y) / (float) h);
            for (int b = 0; b < 4; b++) {
                float f0 = b / 4f, f1 = (b + 1) / 4f;
                float al = a * (b + 1) / 4f * 0.55f;
                int c = al(0xFF7FB2, al);
                int l0 = Math.round(clamp(centre - bw * (1f - f0), lo, hi)), l1 = Math.round(clamp(centre - bw * (1f - f1), lo, hi));
                int r0 = Math.round(clamp(centre + bw * (1f - f1), lo, hi)), r1 = Math.round(clamp(centre + bw * (1f - f0), lo, hi));
                if (l1 > l0) Draw.fill(l0, yy, l1, yy + 2, c);
                if (r1 > r0) Draw.fill(r0, yy, r1, yy + 2, c);
            }
            int m0 = Math.round(clamp(centre - bw * 0.25f, lo, hi)), m1 = Math.round(clamp(centre + bw * 0.25f, lo, hi));
            if (m1 > m0) Draw.fill(m0, yy, m1, yy + 2, al(0xFF7FB2, a));
        }
    }

    // ---------------------------------------------------------------- bấm chuột

    private static final class Burst {
        final float x, y;
        final int rgb, seed;
        final long start = System.currentTimeMillis();

        Burst(float x, float y, int rgb) {
            this.x = x;
            this.y = y;
            this.rgb = rgb;
            this.seed = (int) (start & 0xFFFFFF);
        }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();

    /** Tạo vụ nổ sao + sóng tròn tại (x, y) theo toạ độ màn hình. */
    public static void burst(float x, float y, int rgb) {
        if (BURSTS.size() > 12) BURSTS.remove(0);
        BURSTS.add(new Burst(x, y, rgb & 0xFFFFFF));
    }

    /** Vẽ các vụ nổ đang chạy (gọi ở toạ độ màn hình, sau khi đã pop ma trận menu). */
    public static void drawBursts() {
        if (BURSTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) {
            Burst b = it.next();
            float age = (now - b.start) / 700f;
            if (age >= 1f) {
                it.remove();
                continue;
            }
            if (age < 0f) age = 0f;
            float e = 1f - (1f - age) * (1f - age) * (1f - age);
            float fade = 1f - age;
            Draw.circleRing(b.x, b.y, e * 26f, e * 26f + 2.2f * fade + 0.6f, al(0xFF6FA8, fade * 0.9f));
            Draw.circleRing(b.x, b.y, e * 15f, e * 15f + 1.6f * fade, al(b.rgb, fade * 0.9f));
            for (int i = 0; i < 8; i++) {
                float ang = (float) (Math.PI * 2.0 * i / 8.0) + hash(b.seed, i) * 0.5f;
                float dist = 6f + e * (28f + 12f * hash(b.seed, i + 8));
                float r = (4f + 3f * hash(b.seed, i + 16)) * (0.4f + 0.6f * fade);
                star(b.x + (float) Math.cos(ang) * dist, b.y + (float) Math.sin(ang) * dist,
                        r, i % 2 == 0 ? 0xFF6FA8 : b.rgb, fade, false);
            }
        }
    }
}
