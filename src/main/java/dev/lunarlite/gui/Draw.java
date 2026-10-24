package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

/**
 * Các hàm vẽ dùng chung: bo góc, viền, scissor, màu, chữ.
 *
 * Ở 26.x không còn GL11 / Tessellator: mọi thứ phải đi qua {@link GuiGraphicsExtractor}. Để các module không phải
 * truyền {@code graphics} đi khắp nơi, {@link #begin(GuiGraphicsExtractor)} lưu graphics của frame hiện tại
 * và mọi hàm vẽ ở đây dùng nó. Hình tròn / đường chéo được raster bằng các dải chữ nhật 1px.
 */
public final class Draw {
    private Draw() {}

    /** Graphics của frame đang vẽ (đặt bởi {@link #begin}). */
    public static GuiGraphicsExtractor g;

    public static void begin(GuiGraphicsExtractor graphics) {
        g = graphics;
    }

    // ---- Bảng màu chung của giao diện (menu + HUD editor): nền tối trung tính + một màu nhấn.
    /** Màu nhấn chính: vạch chọn, vạch rê chuột, gạch chân, thanh cuộn. */
    public static int ACCENT = 0xFF35D9FF;
    /** Màu nhấn sáng hơn: chữ tiêu đề mục trên nền tối, thanh cuộn khi đang kéo. */
    public static int ACCENT_LIGHT = 0xFFB8F4FF;
    /** Đường kẻ mảnh / nền nút phụ khi rê chuột. */
    public static int ACCENT_DIM = 0x5535D9FF;
    /** Phần đã điền của thanh trượt. */
    public static int ACCENT_FILL = 0x6635D9FF;

    /** Đặt màu nhấn (RGB); các biến thể sáng / mờ được tính tự động từ màu này. */
    public static void setAccent(int rgb) {
        rgb &= 0xFFFFFF;
        ACCENT = 0xFF000000 | rgb;
        ACCENT_LIGHT = lerp(ACCENT, 0xFFFFFFFF, 0.45f);
        ACCENT_DIM = (0x55 << 24) | rgb;
        ACCENT_FILL = (0x66 << 24) | rgb;
    }

    public static int withAlpha(int rgb, int a) {
        return ((a & 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** Nhân alpha của màu ARGB với {@code f} (0..1). */
    public static int mulAlpha(int argb, float f) {
        int a0 = argb >>> 24;
        if (a0 == 0) return argb;
        int a = Math.round(a0 * Math.max(0f, Math.min(1f, f)));
        if (a < 5) a = 5;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ chữ

    public static int width(String s) {
        return Minecraft.getInstance().font.width(s);
    }

    /** Xoá mã màu § (ví dụ §a). */
    public static String strip(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Vẽ chữ. Màu luôn được ép alpha 0xFF nếu alpha = 0 (26.x bỏ qua chữ có alpha 0). */
    public static void text(String s, float x, float y, int color, boolean shadow) {
        if ((color >>> 24) == 0) color |= 0xFF000000;
        g.text(Minecraft.getInstance().font, s, Math.round(x), Math.round(y), color, shadow);
    }

    public static void textShadow(String s, float x, float y, int color) {
        text(s, x, y, color, true);
    }

    public static void centered(String s, float cx, float y, int color, boolean shadow) {
        text(s, cx - width(s) / 2f, y, color, shadow);
    }

    /** Cắt chuỗi cho vừa {@code maxW} pixel, thêm ".." nếu bị cắt. */
    public static String fit(String s, int maxW) {
        if (width(s) <= maxW) return s;
        int dots = width("..");
        String t = s;
        while (t.length() > 0 && width(t) + dots > maxW) t = t.substring(0, t.length() - 1);
        return t + "..";
    }

    public static void item(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        g.item(stack, x, y);
    }

    /** Số lượng + thanh độ bền của vật phẩm (phần "decorations" của ô vật phẩm). */
    public static void itemDecor(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        g.itemDecorations(Minecraft.getInstance().font, stack, x, y);
    }

    // ------------------------------------------------------------------ biến đổi (Matrix3x2fStack)

    public static Matrix3x2fStack pose() { return g.pose(); }

    public static void push() { g.pose().pushMatrix(); }

    public static void pop() { g.pose().popMatrix(); }

    public static void translate(float x, float y) { g.pose().translate(x, y); }

    public static void scale(float sx, float sy) { g.pose().scale(sx, sy); }

    public static void rotate(float radians) { g.pose().rotate(radians); }

    // ------------------------------------------------------------------ hình chữ nhật

    public static void fill(int x1, int y1, int x2, int y2, int color) {
        if ((color >>> 24) == 0 || x2 <= x1 || y2 <= y1) return;
        g.fill(x1, y1, x2, y2, color);
    }

    /** Hình chữ nhật bo góc (bán kính nhỏ, vẽ bằng các dải ngang). */
    public static void rrect(int x1, int y1, int x2, int y2, int r, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            fill(x1, y1, x2, y2, color);
            return;
        }
        fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** Viền 1px quanh hình chữ nhật (không chồng góc nên alpha đều). */
    public static void outline(int x1, int y1, int x2, int y2, int color) {
        fill(x1, y1, x2, y1 + 1, color);
        fill(x1, y2 - 1, x2, y2, color);
        fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** Viền 1px bo góc, khớp với {@link #rrect} bán kính 3 (bỏ 4 điểm ảnh ở góc). */
    public static void ring(int x1, int y1, int x2, int y2, int color) {
        if (x2 - x1 < 6 || y2 - y1 < 6) {
            outline(x1, y1, x2, y2, color);
            return;
        }
        fill(x1 + 1, y1, x2 - 1, y1 + 1, color);
        fill(x1 + 1, y2 - 1, x2 - 1, y2, color);
        fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** Đường kẻ ngang 1px mờ dần ở hai đầu ({@code maxA}: độ đậm ở giữa, 0..255). */
    public static void fadeLine(int x, int y, int w, int rgb, int maxA) {
        final int seg = 4;
        for (int i = 0; i < w; i += seg) {
            float t = (i + seg / 2f) / w;
            int a = Math.round(maxA * (float) Math.sin(Math.PI * t));
            if (a > 3) fill(x + i, y, Math.min(x + w, x + i + seg), y + 1, withAlpha(rgb, a));
        }
    }

    /** Thanh tiến độ bo tròn: nền {@code track}, phần đầy {@code fill} (ARGB), kèm vệt sáng mỏng ở mép trên. */
    public static void hbar(int x, int y, int w, int h, float frac, int fill, int track) {
        if (w <= 0 || h <= 0) return;
        int r = h >= 4 ? 2 : 1;
        rrect(x, y, x + w, y + h, r, track);
        int f = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (f <= 0) return;
        rrect(x, y, x + f, y + h, r, fill);
        if (f > 2 && h >= 3) fill(x + 1, y, x + f - 1, y + 1, 0x55FFFFFF);
    }

    // ------------------------------------------------------------------ scissor

    /** Giới hạn vùng vẽ (toạ độ GUI). Nhớ gọi {@link #endScissor()}. */
    public static void scissor(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h));
    }

    public static void endScissor() {
        g.disableScissor();
    }

    // ------------------------------------------------------------------ hình tròn / nét (raster bằng dải 1px)

    /** Hình tròn đầy, màu chuyển từ tâm (cIn) ra mép (cOut). */
    public static void disc(float cx, float cy, float r, int cIn, int cOut) {
        if (r <= 0f) return;
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        for (int y = y0; y < y1; y++) {
            float dy = (y + 0.5f) - cy;
            float hw2 = r * r - dy * dy;
            if (hw2 <= 0f) continue;
            float hw = (float) Math.sqrt(hw2);
            int c = cIn == cOut ? cIn : lerp(cIn, cOut, Math.abs(dy) / r);
            fill(Math.round(cx - hw), y, Math.round(cx + hw), y + 1, c);
        }
    }

    public static void disc(float cx, float cy, float r, int c) {
        disc(cx, cy, r, c, c);
    }

    /** Vòng tròn mảnh giữa hai bán kính r1 < r2. */
    public static void circleRing(float cx, float cy, float r1, float r2, int c) {
        if (r2 <= r1) return;
        int y0 = (int) Math.floor(cy - r2), y1 = (int) Math.ceil(cy + r2);
        for (int y = y0; y < y1; y++) {
            float dy = (y + 0.5f) - cy;
            float o2 = r2 * r2 - dy * dy;
            if (o2 <= 0f) continue;
            float ow = (float) Math.sqrt(o2);
            float i2 = r1 * r1 - dy * dy;
            if (i2 <= 0f) {
                fill(Math.round(cx - ow), y, Math.round(cx + ow), y + 1, c);
            } else {
                float iw = (float) Math.sqrt(i2);
                fill(Math.round(cx - ow), y, Math.round(cx - iw), y + 1, c);
                fill(Math.round(cx + iw), y, Math.round(cx + ow), y + 1, c);
            }
        }
    }

    /** Đoạn thẳng dày w (nét vuông, vẽ bằng các ô w×w nối tiếp). */
    public static void line(float x0, float y0, float x1, float y1, float w, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = Math.max(1, (int) Math.ceil(len));
        int s = Math.max(1, Math.round(w));
        for (int i = 0; i <= n; i++) {
            float t = (float) i / n;
            int px = Math.round(x0 + dx * t - s / 2f), py = Math.round(y0 + dy * t - s / 2f);
            fill(px, py, px + s, py + s, c);
        }
    }

    /** Giữ lại cho tương thích: đầu tròn được xấp xỉ bằng nét vuông. */
    public static void capsule(float x0, float y0, float x1, float y1, float w, int c) {
        line(x0, y0, x1, y1, w, c);
    }

    /** Tam giác đặc (quét từng dòng 1px). */
    public static void tri(float x0, float y0, float x1, float y1, float x2, float y2, int color) {
        if ((color >>> 24) == 0) return;
        float minY = Math.min(y0, Math.min(y1, y2)), maxY = Math.max(y0, Math.max(y1, y2));
        int ys = (int) Math.floor(minY), ye = (int) Math.ceil(maxY);
        if (ye - ys > 4000) return;
        float[] xs = {x0, x1, x2}, yv = {y0, y1, y2};
        for (int y = ys; y < ye; y++) {
            float yc = y + 0.5f, lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                int j = (i + 1) % 3;
                float ya = yv[i], yb = yv[j];
                if ((yc < Math.min(ya, yb)) || (yc >= Math.max(ya, yb)) || ya == yb) continue;
                float x = xs[i] + (xs[j] - xs[i]) * (yc - ya) / (yb - ya);
                lo = Math.min(lo, x); hi = Math.max(hi, x);
            }
            if (hi > lo) fill(Math.round(lo), y, Math.max(Math.round(lo) + 1, Math.round(hi)), y + 1, color);
        }
    }

    /** Cung tròn dày w từ góc a0 đến a1 (radian, trục y hướng xuống), nối bằng các đoạn ngắn. */
    public static void arc(float cx, float cy, float r, double a0, double a1, float w, int c) {
        int seg = Math.max(8, (int) Math.ceil(Math.abs(a1 - a0) * Math.max(r, 2f) * 1.5));
        float px = cx + (float) Math.cos(a0) * r, py = cy + (float) Math.sin(a0) * r;
        for (int i = 1; i <= seg; i++) {
            double a = a0 + (a1 - a0) * i / seg;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            line(px, py, x, y, w, c);
            px = x; py = y;
        }
    }

    /** Đa giác đặc quanh trọng tâm (lồi hoặc hình sao): p = x0,y0,x1,y1,... */
    public static void poly(float[] p, int c) {
        int n = p.length / 2;
        if (n < 3) return;
        float mx = 0f, my = 0f;
        for (int i = 0; i < n; i++) { mx += p[2 * i]; my += p[2 * i + 1]; }
        mx /= n; my /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            tri(mx, my, p[2 * i], p[2 * i + 1], p[2 * j], p[2 * j + 1], c);
        }
    }

    /** Tam giác mũi tên đặc. bx = cạnh đáy, cy = tâm dọc, w = dài từ đáy tới đỉnh, hh = nửa chiều cao đáy. */
    public static void arrow(float bx, float cy, float w, float hh, boolean left, int c) {
        if (w <= 0.05f || hh <= 0.05f) return;
        int cols = Math.max(1, Math.round(w));
        for (int i = 0; i < cols; i++) {
            float half = hh * (1f - (i + 0.5f) / cols);
            int x = Math.round(left ? bx - i - 1 : bx + i);
            fill(x, Math.round(cy - half), x + 1, Math.round(cy + half), c);
        }
    }

    // ------------------------------------------------------------------ đầu người chơi

    private static java.lang.reflect.Method faceMethod;
    private static boolean faceSearched;

    /**
     * Vẽ mặt (kèm lớp mũ) của người chơi. API vẽ mặt đổi tên giữa các bản 26.x (PlayerFaceRenderer / PlayerFaceExtractor)
     * nên tìm bằng reflection một lần: phương thức static (graphics, PlayerSkin, x, y, size). Trả về false nếu không vẽ được.
     */
    public static boolean head(net.minecraft.client.multiplayer.PlayerInfo info, int x, int y, int size) {
        if (info == null || g == null) return false;
        if (!faceSearched) {
            faceSearched = true;
            String[] names = {"net.minecraft.client.gui.components.PlayerFaceExtractor",
                    "net.minecraft.client.gui.components.PlayerFaceRenderer"};
            for (String cn : names) {
                try {
                    Class<?> c = Class.forName(cn);
                    for (java.lang.reflect.Method m : c.getMethods()) {
                        Class<?>[] pt = m.getParameterTypes();
                        if (java.lang.reflect.Modifier.isStatic(m.getModifiers()) && pt.length == 5
                                && pt[0].isAssignableFrom(g.getClass())
                                && pt[1] == net.minecraft.world.entity.player.PlayerSkin.class
                                && pt[2] == int.class && pt[3] == int.class && pt[4] == int.class) {
                            faceMethod = m;
                            break;
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError ignored) { }
                if (faceMethod != null) break;
            }
        }
        if (faceMethod == null) return false;
        try {
            faceMethod.invoke(null, g, info.getSkin(), x, y, size);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            faceMethod = null;
            return false;
        }
    }
}
