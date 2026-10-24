package dev.lunarlite.gui;

/** Hiệu ứng chết kiểu Sword Art Online: màn hình tối dần, chữ "YOU ARE DEAD" và hai nút giả lập màn Game Over. */
public final class SaoDeath {
    private SaoDeath() {}

    public static boolean enabled = true;

    private static boolean dead;
    private static long since;

    public static void update(boolean isDead) {
        boolean now = enabled && isDead;
        if (now && !dead) since = System.currentTimeMillis();
        dead = now;
    }

    public static boolean active() { return enabled && dead; }

    public static float progress() {
        float t = (System.currentTimeMillis() - since) / 1200f;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    /** Vẽ lớp phủ (Draw.begin phải được gọi trước). Hai nút chỉ vẽ khi {@code buttons} = true (màn hình chết riêng). */
    public static void render(int w, int h, int mx, int my, boolean buttons) {
        if (!active()) return;
        float p = progress();
        Draw.fill(0, 0, w, h, ((int) (170 * p) << 24) | 0x0A0306);
        Draw.fill(0, h / 2 - 22, w, h / 2 + 22, ((int) (110 * p) << 24));
        Draw.fadeLine(0, h / 2 - 22, w, 0xFF3355, (int) (200 * p));
        Draw.fadeLine(0, h / 2 + 22, w, 0xFF3355, (int) (200 * p));

        int ta = (int) (255 * p);
        if (ta > 4) {
            String title = "YOU ARE DEAD";
            Draw.push();
            Draw.translate(w / 2f, h / 2f - 8f);
            Draw.scale(2f, 2f);
            Draw.text(title, -Draw.width(title) / 2f, 0f, (ta << 24) | 0xFFFFFF, true);
            Draw.pop();
        }
        if (buttons && p > 0.6f) {
            int a = (int) (255 * ((p - 0.6f) / 0.4f));
            button(w / 2 - 100, h / 4 + 72, "Hồi sinh", mx, my, a);
            button(w / 2 - 100, h / 4 + 96, "Về màn hình chính", mx, my, a);
        }
    }

    public static boolean overRespawn(int w, int h, int mx, int my) { return progress() > 0.6f && Draw.in(mx, my, w / 2 - 100, h / 4 + 72, 200, 20); }

    public static boolean overTitle(int w, int h, int mx, int my) { return progress() > 0.6f && Draw.in(mx, my, w / 2 - 100, h / 4 + 96, 200, 20); }

    private static void button(int x, int y, String text, int mx, int my, int a) {
        boolean hv = Draw.in(mx, my, x, y, 200, 20);
        int bg = ((int) (a * (hv ? 0.75f : 0.5f)) << 24) | (hv ? 0x3A1620 : 0x15090D);
        Draw.rrect(x, y, x + 200, y + 20, 3, bg);
        Draw.outline(x, y, x + 200, y + 20, ((int) (a * 0.6f) << 24) | 0xFF3355);
        Draw.text(text, x + 100 - Draw.width(text) / 2f, y + 6, (a << 24) | 0xFFFFFF, true);
    }
}
