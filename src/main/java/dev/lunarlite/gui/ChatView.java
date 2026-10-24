package dev.lunarlite.gui;

import dev.lunarlite.module.AnimationsModule;
import dev.lunarlite.module.ChatBarModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Khung chat riêng của Chat Bar (port từ bản 1.8.9): khung kính bo góc, avatar trước tin, tin giết người có hai avatar,
 * viên bo góc ôm chữ khi đóng chat, trượt khi có tin mới, thanh cuộn mảnh, vị trí kéo thả.
 * Tin được lấy qua {@code ChatComponentMixin}; chat vanilla bị ẩn (ChatComponentMixin) khi {@link #takeover()} là true.
 */
public final class ChatView {
    private ChatView() {}

    public static final int MAXL = 20, CLOSED_LINES = 7;

    private static final class Line {
        final Component text;
        final long born = System.currentTimeMillis();
        final String name;                 // người gửi (avatar đứng trước), null nếu không có
        final String killer, victim;       // tin giết người (killer có thể null = tự chết)
        final boolean fin;
        final int rgb;                     // màu đội của người gửi, -1 nếu không rõ
        List<FormattedCharSequence> seq;
        int seqW = -1, seqFont;

        Line(Component text, String name, String killer, String victim, boolean fin, int rgb) {
            this.text = text; this.name = name; this.killer = killer; this.victim = victim; this.fin = fin; this.rgb = rgb;
        }
    }

    private static final List<Line> LINES = new ArrayList<>(); // mới nhất ở đầu
    private static Object lastRaw;
    private static boolean captured;
    private static long suppressedAt;

    private static int scroll, totalRows;
    private static float slideY, panelA;
    private static Line lastNewest;
    private static long lastFrame;

    /** true trong trình chỉnh vị trí: vẽ như đang mở chat. */
    public static boolean preview;

    // ------------------------------------------------------------------ nhận tin

    /** Gọi từ ChatComponentMixin cho mọi tin vào chat (đã qua bộ lọc của Chat Bar). */
    public static void capture(Object o) {
        if (!(o instanceof Component c)) return;
        ChatBarModule m = ChatBarModule.instance();
        if (m == null || !m.enabled) return;
        checkLevel();
        if (c == lastRaw) return; // addMessage(Component) gọi addMessage(Component, ..) cùng object
        lastRaw = c;
        captured = true;
        String plain = Draw.strip(c.getString());
        String[] pc = ChatBarModule.parsePlayerChat(plain);
        Line line;
        if (pc != null) {
            int rgb = ChatBarModule.teamRgb(pc[0], plain);
            Component shown = rgb < 0 ? c : c.copy().withStyle(s -> s.withColor(rgb));
            line = new Line(shown, pc[0], null, null, false, rgb);
        } else {
            String[] k = ChatBarModule.SHOW_KILLS.value ? ChatBarModule.parseKill(plain) : null;
            if (k != null && k[1] != null) {
                line = new Line(c, null, k[1], k[0], plain.contains("FINAL KILL"), -1);
            } else if (k != null) {
                line = new Line(c, k[0], null, null, false, -1); // tự chết: giữ tin, thêm avatar
            } else {
                line = new Line(c, null, null, null, false, -1);
            }
        }
        LINES.add(0, line);
        if (scroll > 0) scroll++;
        while (LINES.size() > 400) LINES.remove(LINES.size() - 1);
    }

    private static Object lastLevel;

    private static void checkLevel() {
        Object lv = Minecraft.getInstance().level;
        if (lv != lastLevel) {
            lastLevel = lv;
            clear(); // sang thế giới / ván mới
        }
    }

    public static void clear() {
        LINES.clear();
        scroll = 0;
        lastNewest = null;
    }

    /** ChatComponentMixin gọi khi huỷ vẽ chat vanilla. */
    public static void markSuppressed() { suppressedAt = System.currentTimeMillis(); }

    /** Có nên ẩn chat vanilla để dùng khung này không (chỉ khi đã bắt được tin, tức mixin nhận tin đã hoạt động). */
    public static boolean takeover() {
        ChatBarModule m = ChatBarModule.instance();
        return m != null && m.enabled && captured;
    }

    /** Chat vanilla đã bị ẩn thật (mixin vẽ đang chạy) — chỉ khi đó mới vẽ khung này để không bị trùng. */
    private static boolean vanillaHidden() { return System.currentTimeMillis() - suppressedAt < 1500L; }

    public static void scroll(double delta) {
        int max = Math.max(0, totalRows - MAXL);
        scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? 3 : -3)));
    }

    public static void resetScroll() { scroll = 0; }

    // ------------------------------------------------------------------ hình học

    public static float chatScale() {
        try {
            return (float) (double) Minecraft.getInstance().options.chatScale().get();
        } catch (Throwable t) {
            return 1f;
        }
    }

    private static float chatOpacity() {
        try {
            return (float) (double) Minecraft.getInstance().options.chatOpacity().get() * 0.9f + 0.1f;
        } catch (Throwable t) {
            return 1f;
        }
    }

    /** Khung tin nhắn {x1, y1, x2, y2} (px GUI), đã kẹp trong màn hình. */
    public static int[] windowRect(int sw, int sh) {
        float sc = chatScale();
        int w = (int) Math.ceil((ChatBarModule.CHAT_WIDTH.getInt() + 4) * sc);
        int h = (int) Math.ceil(MAXL * 9 * sc);
        int x1 = Math.max(0, Math.min(sw - w, 2 + ChatBarModule.windowX));
        int y2 = Math.max(Math.min(h, sh), Math.min(sh, sh - 28 + ChatBarModule.windowY));
        return new int[]{x1, y2 - h, x1 + w, y2};
    }

    // ------------------------------------------------------------------ vẽ

    private static List<FormattedCharSequence> split(Minecraft mc, Line l, int wrapW) {
        if (l.seq == null || l.seqW != wrapW) {
            l.seq = mc.font.split(l.text, Math.max(40, wrapW));
            l.seqW = wrapW;
        }
        return l.seq;
    }

    private static int pad() {
        return ChatBarModule.PLAYER_HEADS.value ? ChatBarModule.ICON_SIZE.getInt() + 3 : 0;
    }

    private static void updateSlide(float sc) {
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        slideY *= (float) Math.exp(-dt * 16f * AnimationsModule.speed());
        if (Math.abs(slideY) < 0.4f) slideY = 0f;
        Line newest = LINES.isEmpty() ? null : LINES.get(0);
        if (newest != lastNewest) {
            if (lastNewest != null && scroll == 0 && ChatBarModule.ANIM_MESSAGES.value && AnimationsModule.hud()) {
                slideY = Math.min(40f, slideY + 9f * sc);
            }
            lastNewest = newest;
        }
    }

    public static void render(int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !takeover()) return;
        checkLevel();
        if (!preview && !vanillaHidden()) return;
        boolean open = preview || mc.screen instanceof ChatScreen;
        boolean glass = ChatBarModule.CHAT_GLASS.value && !ChatBarModule.HIDE_CHAT_BACKGROUND.value
                && !ChatBarModule.FIT_CHAT_BACKGROUND.value;
        if (LINES.isEmpty() && !(glass && open)) return;

        float sc = chatScale();
        float opacity = chatOpacity();
        int lw = ChatBarModule.CHAT_WIDTH.getInt();
        int fullW = lw + 4;
        int pad = pad();
        int gap = ChatBarModule.CHAT_GAP.getInt();
        int maxRows = open ? MAXL : CLOSED_LINES;
        updateSlide(sc);

        int[] r = windowRect(sw, sh);
        long nowMs = System.currentTimeMillis();

        // khung kính hiện/ẩn mượt theo trạng thái mở chat
        long pn = System.nanoTime();
        float pTarget = open ? 1f : 0f;
        panelA = (AnimationsModule.hud() && ChatBarModule.ANIM_MESSAGES.value) ? panelA + (pTarget - panelA) * 0.25f : pTarget;
        if (Math.abs(pTarget - panelA) < 0.01f) panelA = pTarget;

        Draw.push();
        Draw.translate(r[0] + 2f, r[3] + slideY);
        Draw.scale(sc, sc);

        if (glass && panelA > 0.02f) glassPanel(-4, -MAXL * 9 - 4, fullW + 4, 3, 0.58f * opacity * panelA, panelA);

        // duyệt tin từ mới nhất, đếm hàng từ dưới lên
        int row = 0, drawnRows = 0;
        try {
            for (Line l : LINES) {
                List<FormattedCharSequence> seq = l.killer != null ? null : split(mc, l, lw - pad);
                int n = seq == null ? 1 : seq.size();
                long age = (nowMs - l.born) / 50L;
                boolean show = open || age < 200;
                double d = Math.max(0.0, Math.min(1.0, (1.0 - age / 200.0) * 10.0));
                int a = (int) ((open ? 255 : (int) (255 * d * d)) * opacity);
                for (int k = 0; k < n; k++) {
                    int rr = row + (n - 1 - k) - scroll; // hàng tính từ đáy
                    if (rr >= 0 && rr < maxRows && show && a > 3) {
                        drawnRows++;
                        drawRow(mc, l, seq, k, rr, a, glass, open, pad);
                    }
                }
                row += n + gap;
                if (row - scroll > maxRows + 40) { row = Math.max(row, totalRows); }
            }
        } catch (RuntimeException ignored) { /* lỗi vẽ không được làm sập game */ }
        totalRows = Math.max(row, 0);

        if (open && !ChatBarModule.HIDE_CHAT_SCROLLBAR.value && totalRows > MAXL) {
            int i3 = MAXL * 9;
            int thumb = Math.max(8, i3 * MAXL / totalRows);
            int pos = scroll * (i3 - thumb) / Math.max(1, totalRows - MAXL);
            int sx = fullW - 1;
            Draw.fill(sx, -i3, sx + 2, 0, 0x14FFFFFF);
            Draw.fill(sx, -pos - thumb, sx + 2, -pos, scroll > 0 ? (Draw.ACCENT & 0xFFFFFF) | 0xDD000000 : 0x66FFFFFF);
        }
        Draw.pop();
    }

    private static void drawRow(Minecraft mc, Line l, List<FormattedCharSequence> seq, int k, int rr, int a,
                                boolean glass, boolean open, int pad) {
        int top = -rr * 9 - 9;
        int color = (a << 24) | 0xFFFFFF;
        boolean first = k == 0;
        int sz = ChatBarModule.ICON_SIZE.getInt();

        if (l.killer != null) { // [avt] Killer giết [avt] Victim (FINAL KILL!)
            int x = 0;
            String kn = l.killer, vn = l.victim;
            int kc = 0xFF000000 | Math.max(0, ChatBarModule.teamRgb(kn, ""));
            int vc = 0xFF000000 | Math.max(0, ChatBarModule.teamRgb(vn, ""));
            if (ChatBarModule.teamRgb(kn, "") < 0) kc = 0xFFAAAAAA;
            if (ChatBarModule.teamRgb(vn, "") < 0) vc = 0xFFAAAAAA;
            String mid = " giết ";
            String fin = l.fin ? "  FINAL KILL!" : "";
            int w = (ChatBarModule.PLAYER_HEADS.value ? 2 * (sz + 3) : 0) + Draw.width(kn) + Draw.width(mid)
                    + Draw.width(vn) + Draw.width(fin);
            if (glass && !open) Draw.rrect(-3, top, w + 3, top + 10, 2, ((int) (a * 0.62f) << 24));
            else if (!glass && !ChatBarModule.HIDE_CHAT_BACKGROUND.value) Draw.fill(-1, top, w + 2, top + 9, (a / 2) << 24);
            if (ChatBarModule.PLAYER_HEADS.value) x += head(mc, kn, x, top, sz, a);
            Draw.text(kn, x, top + 1, Draw.mulAlpha(kc | 0xFF000000, a / 255f), true);
            x += Draw.width(kn);
            Draw.text(mid, x, top + 1, Draw.mulAlpha(0xFFFF5555, a / 255f), true);
            x += Draw.width(mid);
            if (ChatBarModule.PLAYER_HEADS.value) x += head(mc, vn, x, top, sz, a);
            Draw.text(vn, x, top + 1, Draw.mulAlpha(vc | 0xFF000000, a / 255f), true);
            x += Draw.width(vn);
            if (l.fin) Draw.text(fin, x, top + 1, Draw.mulAlpha(0xFFFFAA00, a / 255f), true);
            return;
        }

        FormattedCharSequence fs = seq.get(k);
        int tx = first ? pad : 0;
        int w = tx + mc.font.width(fs);
        if (glass) {
            if (!open && !isBlank(fs)) Draw.rrect(-3, top, w + 3, top + 10, 2, ((int) (a * 0.62f) << 24));
        } else if (!ChatBarModule.HIDE_CHAT_BACKGROUND.value && !isBlank(fs)) {
            if (ChatBarModule.FIT_CHAT_BACKGROUND.value) Draw.rrect(-3, top, w + 3, top + 10, 2, (a / 2) << 24);
            else Draw.fill(0, top, ChatBarModule.CHAT_WIDTH.getInt() + 4, top + 9, (a / 2) << 24);
        }
        if (first && l.name != null && ChatBarModule.PLAYER_HEADS.value) head(mc, l.name, 0, top, sz, a);
        Draw.g.text(mc.font, fs, tx, top + 1, color, true);
    }

    private static boolean isBlank(FormattedCharSequence fs) {
        StringBuilder sb = new StringBuilder();
        fs.accept((i, st, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString().isBlank();
    }

    /** Vẽ avatar người chơi, trả về độ rộng đã chiếm. */
    private static int head(Minecraft mc, String name, int x, int rowTop, int sz, int a) {
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(name);
        int hx = x + ChatBarModule.ICON_X.getInt();
        int hy = rowTop + (9 - sz) / 2 + 1 + ChatBarModule.ICON_Y.getInt();
        if (!Draw.head(info, hx, hy, sz)) {
            // người đã chết / thoát tab list hoặc không vẽ được mặt: ô chữ cái đầu
            Draw.fill(hx, hy, hx + sz, hy + sz, Draw.mulAlpha(0xFF3A3F45, a / 255f));
            String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            Draw.text(ch, hx + (sz - Draw.width(ch)) / 2f, hy + (sz - 8) / 2f, Draw.mulAlpha(0xFFFFFFFF, a / 255f), false);
        }
        return sz + 3;
    }

    // ------------------------------------------------------------------ khung kính

    static void glassPanel(int x1, int y1, int x2, int y2, float fillA, float fx) {
        Draw.ring(x1 - 2, y1 - 2, x2 + 2, y2 + 2, Draw.mulAlpha(0xFF000000, 0.10f * fx));
        Draw.ring(x1 - 1, y1 - 1, x2 + 1, y2 + 1, Draw.mulAlpha(0xFF000000, 0.22f * fx));
        Draw.rrect(x1, y1, x2, y2, 3, Draw.mulAlpha(0xFF0A0A0A, fillA));
        Draw.ring(x1, y1, x2, y2, Draw.mulAlpha(0xFFFFFFFF, 0.10f * fx));
        // vệt accent mảnh ở mép trên
        Draw.fill(x1 + 3, y1, x2 - 3, y1 + 1, Draw.mulAlpha(Draw.ACCENT | 0xFF000000, 0.55f * fx));
    }
}
