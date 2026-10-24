package dev.lunarlite.gui;

import dev.lunarlite.Compat;
import dev.lunarlite.Lang;
import dev.lunarlite.LunarLite;
import dev.lunarlite.module.AnimationsModule;
import dev.lunarlite.module.HudModule;
import dev.lunarlite.module.Module;
import dev.lunarlite.module.ThemeModule;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.Setting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Chỉnh vị trí HUD (port 1:1 từ bản 1.8.9):
 *  - Kéo thả để di chuyển (tự hít vào mép / giữa màn hình), lăn chuột để đổi cỡ, phím mũi tên để nhích.
 *  - Bấm vào một HUD: nó được chọn, bảng cài đặt của HUD đó hiện ở góc màn hình (kích cỡ, mọi tuỳ chỉnh,
 *    thao tác, đặt lại) và có dấu X cạnh khung để xoá (= tắt) HUD đó.
 *  - Tiêu đề bảng kéo được (chuột phải = về vị trí tự động); thanh cuộn kéo được.
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 5;
    private static final int ORANGE = 0xFFF0A30A;
    private static final int INK = 0xFF22262B;
    private static final int INK_SOFT = 0xFF2D5F7A;
    private static final int PW = 196, RH = 20, GAP = 4, HEAD = 26, XS = 11, BAR_W = 6;
    private static final int BH = 20, WIDE = 200;
    private static final int FOOT = 46;
    private static final int K_SCALE = 0, K_BOOL = 1, K_MODE = 2, K_COLOR = 3, K_SLIDER = 4, K_ACTION = 5, K_RESET = 6;
    private static final String[] COLOR_NAMES = {
        "White", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink", "Black"
    };

    private static final class PRow {
        int kind;
        Setting s;
        Module.Action a;
        int x, y, w;
    }

    private final Screen parent;
    private HudModule selected, dragging;
    private int offX, offY;
    private boolean guideV, guideH;

    private final int[] bBack = new int[4];
    private final int[] bX = new int[4];
    private final List<PRow> prows = new ArrayList<>();
    private boolean panelOn;
    private int px, py, ph, viewTop, viewH, contentH;
    private float panelScroll, panelTarget;
    private SliderSetting dragSlider;
    private int dragX, dragW;

    private static boolean panelCustom;
    private static int customX, customY;
    private boolean dragPanel;
    private int panOffX, panOffY;

    private final long openStart = System.currentTimeMillis();
    private long selAt = System.currentTimeMillis();
    private final Map<String, Float> hovAnim = new HashMap<>();
    private final Map<String, Float> swAnim = new HashMap<>();
    private float backHov, barAnim;
    private boolean dragBar;
    private int dragOff;
    private int curMx, curMy;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Sửa vị trí HUD"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ tiện ích

    private HudModule hudAt(int mx, int my) {
        List<Module> mods = LunarLite.MODULES;
        for (int i = mods.size() - 1; i >= 0; i--) {
            Module m = mods.get(i);
            if (!(m instanceof HudModule h) || !m.enabled) continue;
            if (Draw.in(mx, my, h.renderX(width, true), h.renderY(height, true), h.boxW(true), h.boxH(true))) return h;
        }
        return null;
    }

    private static boolean hit(int[] b, int mx, int my) {
        return Draw.in(mx, my, b[0], b[1], b[2], b[3]);
    }

    private static int presetIndex(int rgb) {
        for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
            if (ColorSetting.PRESETS[i] == (rgb & 0xFFFFFF)) return i;
        }
        return -1;
    }

    private static String colorName(int rgb) {
        int i = presetIndex(rgb);
        return Lang.vi(i < 0 || i >= COLOR_NAMES.length ? "Custom" : COLOR_NAMES[i]);
    }

    private static boolean overlap(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private void select(HudModule h) {
        if (h != selected) {
            panelScroll = panelTarget = 0f;
            selAt = System.currentTimeMillis();
            hovAnim.clear();
            swAnim.clear();
        }
        selected = h;
    }

    // ------------------------------------------------------------------ animation

    private static float smooth() { return AnimationsModule.menu() ? 0.3f : 1f; }

    private static float approach(Map<String, Float> m, String key, float init, float target, float k) {
        Float c = m.get(key);
        float v = c == null ? init : c;
        v += (target - v) * k;
        if (Math.abs(target - v) < 0.01f) v = target;
        m.put(key, v);
        return v;
    }

    private float stagger(int i) {
        if (!AnimationsModule.menu()) return 1f;
        float t = (System.currentTimeMillis() - selAt) * AnimationsModule.speed() / 240f - Math.min(i, 10) * 0.06f;
        return AnimationsModule.ease(t);
    }

    private float openProgress() {
        if (!AnimationsModule.menu()) return 1f;
        return AnimationsModule.ease((System.currentTimeMillis() - openStart) * AnimationsModule.speed() / 220f);
    }

    private static String rowKey(PRow r) {
        return r.kind + ":" + (r.s != null ? r.s.label : r.a != null ? r.a.label : "");
    }

    // ------------------------------------------------------------------ thành phần vẽ

    private static void ink(String t, float x, float y, int c) {
        Draw.text(t, x, y, c, false);
    }

    private static void pill(int x1, int y1, int x2, int y2, int c) {
        if (y2 - y1 < 3 || x2 - x1 < 3) { Draw.fill(x1, y1, x2, y2, c); return; }
        Draw.fill(x1 + 1, y1, x2 - 1, y1 + 1, c);
        Draw.fill(x1, y1 + 1, x2, y2 - 1, c);
        Draw.fill(x1 + 1, y2 - 1, x2 - 1, y2, c);
    }

    private static void drawSwitch(int tx, int ty, float a) {
        pill(tx - 2, ty - 2, tx + 20, ty + 10, Draw.lerp(0xFF8E949B, 0xFF3FBF7F, a));
        Draw.fill(tx, ty - 1, tx + 18, ty, Draw.lerp(0x44FFFFFF, 0x66FFFFFF, a));
        int kx = tx + 1 + Math.round(10 * a);
        Draw.fill(kx, ty, kx + 7, ty + 8, 0xFFFFFFFF);
    }

    private float drawFlatBtn(int[] b, int x, int y, int w, int h, String label, boolean over, float hov) {
        if (b != null) { b[0] = x; b[1] = y; b[2] = w; b[3] = h; }
        hov += ((over ? 1f : 0f) - hov) * smooth();
        if (Math.abs((over ? 1f : 0f) - hov) < 0.01f) hov = over ? 1f : 0f;
        Skin.saoRow(x, y, w, h, hov);
        Draw.fill(x, y, x + 3, y + h, Draw.lerp(0xFFB9770E, ORANGE, hov));
        String t = Draw.fit(label, w - 14);
        ink(t, x + (w - Draw.width(t)) / 2f + 1, y + (h - 8) / 2f, INK);
        return hov;
    }

    // ------------------------------------------------------------------ bảng cài đặt

    private void buildRows() {
        prows.clear();
        if (selected == null) return;
        PRow sc = new PRow();
        sc.kind = K_SCALE;
        prows.add(sc);
        for (Setting st : selected.settings) {
            if (!st.isVisible() || st == selected.scale) continue;
            PRow r = new PRow();
            r.s = st;
            if (st instanceof SliderSetting) r.kind = K_SLIDER;
            else if (st instanceof BoolSetting) r.kind = K_BOOL;
            else if (st instanceof ModeSetting) r.kind = K_MODE;
            else if (st instanceof ColorSetting) r.kind = K_COLOR;
            else continue;
            prows.add(r);
        }
        for (Module.Action a : selected.actions()) {
            PRow r = new PRow();
            r.kind = K_ACTION;
            r.a = a;
            prows.add(r);
        }
        PRow reset = new PRow();
        reset.kind = K_RESET;
        prows.add(reset);
    }

    private void layoutPanel() {
        panelOn = selected != null && selected.enabled;
        bX[0] = bX[1] = -100;
        bX[2] = bX[3] = 0;
        if (!panelOn) { prows.clear(); return; }
        int rx = selected.renderX(width, true), ry = selected.renderY(height, true);
        int bw = selected.boxW(true), bh = selected.boxH(true);

        int xx = rx + bw + 4;
        if (xx + XS > width - 2) xx = width - XS - 2;
        int xy = Math.max(2, ry - 2);
        bX[0] = xx; bX[1] = xy; bX[2] = XS; bX[3] = XS;

        buildRows();
        contentH = prows.size() * (RH + GAP) + 2;
        ph = Math.max(HEAD + 40, Math.min(HEAD + 8 + contentH, height - 36 - FOOT));
        viewH = ph - HEAD - 8;
        float maxScroll = Math.max(0, contentH - viewH);
        panelScroll = Math.max(0f, Math.min(maxScroll, panelScroll));
        panelTarget = Math.max(0f, Math.min(maxScroll, panelTarget));

        int bottomY = height - ph - FOOT;
        int[][] cand = {
            { width - PW - 8, bottomY }, { 8, bottomY },
            { width - PW - 8, 36 }, { 8, 36 }
        };
        int[] pick = cand[0];
        for (int[] c : cand) {
            if (!overlap(c[0], c[1], PW, ph, rx - 4, ry - 4, bw + 8 + XS + 10, bh + 8)) { pick = c; break; }
        }
        if (panelCustom) {
            px = Math.max(0, Math.min(width - PW, customX));
            py = Math.max(0, Math.min(height - ph, customY));
        } else {
            px = pick[0];
            py = pick[1];
        }
        viewTop = py + HEAD + 4;

        int rw = PW - 12 - (contentH > viewH ? BAR_W + 2 : 0);
        int sc = Math.round(panelScroll);
        for (int i = 0; i < prows.size(); i++) {
            PRow r = prows.get(i);
            r.x = px + 6;
            r.w = rw;
            r.y = viewTop + i * (RH + GAP) - sc;
        }
    }

    private String valueText(PRow r) {
        switch (r.kind) {
            case K_SCALE: return String.format(Locale.ROOT, "%.2fx", selected.getScale());
            case K_MODE: return Lang.vi(((ModeSetting) r.s).get());
            case K_COLOR: return colorName(((ColorSetting) r.s).value);
            case K_SLIDER: return ((SliderSetting) r.s).display();
            default: return "";
        }
    }

    private void drawRow(PRow r, int i, int mx, int my) {
        String key = rowKey(r);
        float k = smooth();
        float st = stagger(i);
        boolean inView = mx >= px && mx < px + PW && my >= viewTop && my < viewTop + viewH;
        boolean over = st > 0.7f && inView && Draw.in(mx, my, r.x, r.y, r.w, RH);
        float hov = approach(hovAnim, key, 0f, over && r.kind != K_SCALE ? 1f : 0f, k);

        int x = r.x, y = r.y, w = r.w, h = RH;
        int ty = y + (h - 8) / 2;

        Draw.push();
        Draw.translate((1f - st) * 22f, 0f);

        Skin.saoRow(x, y, w, h, hov);
        if (r.kind == K_SLIDER) {
            int fw = (int) Math.round(((SliderSetting) r.s).ratio() * w);
            Draw.fill(x, y, x + fw, y + h - 1, 0x66F0A30A);
            Draw.fill(Math.max(x, x + fw - 2), y, x + Math.max(2, fw), y + h - 1, ORANGE);
        }
        if (hov > 0.02f) Draw.fill(x, y, x + Math.round(3 * hov), y + h, ORANGE);

        switch (r.kind) {
            case K_SCALE: {
                ink("Kích cỡ", x + 8, ty, INK);
                int mxn = x + w - 2 * RH, pxn = x + w - RH;
                String val = valueText(r);
                ink(val, mxn - 6 - Draw.width(val), ty, INK_SOFT);
                boolean hm = st > 0.7f && inView && Draw.in(mx, my, mxn, y, RH - 1, RH);
                boolean hp = st > 0.7f && inView && Draw.in(mx, my, pxn, y, RH, RH);
                Draw.fill(mxn, y + 1, mxn + RH - 1, y + h - 1, hm ? 0xAAF0A30A : 0x44000000);
                Draw.fill(pxn, y + 1, pxn + RH, y + h - 1, hp ? 0xAAF0A30A : 0x44000000);
                ink("-", mxn + (RH - 1 - Draw.width("-")) / 2f, ty, hm ? 0xFFFFFFFF : INK);
                ink("+", pxn + (RH - Draw.width("+")) / 2f, ty, hp ? 0xFFFFFFFF : INK);
                break;
            }
            case K_BOOL: {
                float swT = ((BoolSetting) r.s).value ? 1f : 0f;
                float sw = approach(swAnim, key, swT, swT, k);
                ink(Draw.fit(Lang.vi(r.s.label), w - 40), x + 8, ty, INK);
                drawSwitch(x + w - 26, ty, sw);
                break;
            }
            case K_MODE:
            case K_COLOR:
            case K_SLIDER: {
                String right = valueText(r);
                int extra = r.kind == K_COLOR ? 18 : 0;
                int rr = x + w - 7 - extra;
                int rw = Draw.width(right);
                String left = Draw.fit(Lang.vi(r.s.label), Math.max(10, w - 14 - rw - 10 - extra));
                ink(left, x + 8, ty, INK);
                ink(right, rr - rw, ty, INK_SOFT);
                if (r.kind == K_COLOR) {
                    int sx = x + w - 17, sy = y + (h - 10) / 2;
                    Draw.fill(sx - 1, sy - 1, sx + 11, sy + 11, 0xFF000000);
                    Draw.fill(sx, sy, sx + 10, sy + 10, 0xFF000000 | ((ColorSetting) r.s).value);
                }
                break;
            }
            default: {
                String label = r.kind == K_ACTION ? r.a.label : "Đặt lại tuỳ chỉnh";
                String t = Draw.fit(label, w - 8);
                ink(t, x + (w - Draw.width(t)) / 2f, ty, INK);
                break;
            }
        }
        Draw.pop();
    }

    // ---- thanh cuộn

    private int barX() { return px + PW - BAR_W - 1; }

    private int thumbH() { return Math.max(20, viewH * viewH / Math.max(1, contentH)); }

    private int thumbY(float scroll) {
        return viewTop + Math.round((viewH - thumbH()) * (scroll / Math.max(1, contentH - viewH)));
    }

    private void scrollbar() {
        int x = barX(), y = viewTop;
        int bh = thumbH(), by = thumbY(panelScroll);
        boolean hover = curMx >= x - 3 && curMx < x + BAR_W + 2 && curMy >= y && curMy < y + viewH;
        float target = dragBar || hover ? 1f : 0f;
        barAnim += (target - barAnim) * smooth();
        if (Math.abs(target - barAnim) < 0.02f) barAnim = target;

        int tw = 3 + Math.round(2 * barAnim);
        int tx1 = x + 1 + Math.round((5 - tw) / 2f);
        pill(x + 1, y + 2, x + 6, y + viewH - 2, 0x33000000);
        int alpha = dragBar ? 0xFF : 0x99 + Math.round(0x33 * barAnim);
        pill(tx1, by + 2, tx1 + tw, Math.min(y + viewH - 2, by + bh - 2), (alpha << 24) | 0xF0A30A);
    }

    private boolean startBarDrag(int mx, int my) {
        if (contentH <= viewH) return false;
        int x = barX();
        if (mx < x - 3 || mx >= x + BAR_W + 2 || my < viewTop || my >= viewTop + viewH) return false;
        int by = thumbY(panelScroll), bh = thumbH();
        dragBar = true;
        dragOff = (my >= by && my < by + bh) ? my - by : bh / 2;
        dragBarTo(my);
        return true;
    }

    private void dragBarTo(int my) {
        int range = Math.max(1, viewH - thumbH());
        float v = (my - dragOff - viewTop) / (float) range * (contentH - viewH);
        v = Math.max(0f, Math.min(Math.max(0, contentH - viewH), v));
        panelScroll = panelTarget = v;
    }

    private void drawPanel(int mx, int my) {
        if (!panelOn) return;
        float e = AnimationsModule.menu()
                ? AnimationsModule.ease((System.currentTimeMillis() - selAt) * AnimationsModule.speed() / 200f) : 1f;

        Draw.fill(px - 1, py - 1, px + PW + 1, py + ph + 2, Draw.mulAlpha(0x55000000, e));
        Skin.vgrad(px, py, px + PW, py + ph, Draw.mulAlpha(0xFFFCFCFD, e), Draw.mulAlpha(0xFFEBECEE, e));
        Skin.vgrad(px, py, px + PW, py + HEAD - 1, Draw.mulAlpha(0xFFF3F4F5, e), Draw.mulAlpha(0xFFE0E2E4, e));
        Draw.fill(px, py + HEAD - 1, px + PW, py + HEAD, Draw.mulAlpha(0xFFD2D4D6, e));
        int uw = Math.round((PW - 12) * e);
        Draw.fill(px + 6, py + HEAD - 1, px + 6 + uw, py + HEAD, ORANGE);
        Skin.brackets(px - 3, py - 3, PW + 6, ph + 6, 7, (Math.round(150 * e) << 24) | (Draw.ACCENT & 0xFFFFFF));

        Draw.push();
        Draw.translate(px + 8, py + 7);
        Draw.scale(1.2f, 1.2f);
        Draw.text(Draw.fit(Lang.vi(selected.name), 100), 0, 0, 0xFF3A3F45, false);
        Draw.pop();
        String cat = selected.category.label;
        Draw.text(cat, px + PW - 8 - Draw.width(cat), py + 9, 0xFF7A8087, false);

        int gx = px + PW - 8 - Draw.width(cat) - 12 - 34, gy = py + 10;
        boolean gh = dragPanel || Draw.in(mx, my, px, py, PW, HEAD);
        pill(gx, gy, gx + 34, gy + 5, gh ? ORANGE : 0xFFA0A5AA);

        Draw.scissor(px, viewTop, PW, viewH);
        for (int i = 0; i < prows.size(); i++) {
            PRow r = prows.get(i);
            if (r.y + RH < viewTop || r.y > viewTop + viewH) continue;
            drawRow(r, i, mx, my);
        }
        Draw.endScissor();

        if (contentH > viewH) scrollbar();
    }

    // ------------------------------------------------------------------ vẽ

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        Draw.begin(graphics);
        ThemeModule.apply();
        curMx = mx;
        curMy = my;
        float op = openProgress();

        panelScroll += (panelTarget - panelScroll) * (AnimationsModule.menu() ? 0.35f : 1f);
        if (Math.abs(panelTarget - panelScroll) < 0.4f) panelScroll = panelTarget;

        Skin.vgrad(0, 0, width, height, Draw.mulAlpha(0x40101010, op), Draw.mulAlpha(0x58101010, op));
        layoutPanel();

        Draw.fill(width / 2, 0, width / 2 + 1, height, 0x14FFFFFF);
        Draw.fill(0, height / 2, width, height / 2 + 1, 0x14FFFFFF);
        if (guideV) Draw.fill(width / 2, 0, width / 2 + 1, height, Draw.withAlpha(Draw.ACCENT & 0xFFFFFF, 0xAA));
        if (guideH) Draw.fill(0, height / 2, width, height / 2 + 1, Draw.withAlpha(Draw.ACCENT & 0xFFFFFF, 0xAA));

        HudModule hover = hudAt(mx, my);
        for (Module m : LunarLite.MODULES) {
            if (!(m instanceof HudModule h) || !m.enabled) continue;
            h.render(width, height, true);
            int rx = h.renderX(width, true), ry = h.renderY(height, true);
            int w = h.boxW(true), hh = h.boxH(true);
            int col = h == dragging ? 0xFFFFD27A : (h == selected ? ORANGE : (h == hover ? 0xCCFFFFFF : 0x55FFFFFF));
            Draw.outline(rx - 2, ry - 2, rx + w + 2, ry + hh + 2, col);
            if (h == selected || h == dragging) {
                int ba = 170 + Math.round(70f * (float) Math.sin(System.currentTimeMillis() / 300.0));
                Skin.brackets(rx - 5, ry - 5, w + 10, hh + 10, 6, (Math.min(255, ba) << 24) | 0xF0A30A);
            }
            if (h == selected) {
                String tag = Lang.vi(h.name) + "  " + String.format(Locale.ROOT, "%.2fx", h.getScale());
                int tw = Draw.width(tag) + 10;
                int ty = ry - 15 < 2 ? ry + hh + 4 : ry - 15;
                Skin.vgrad(rx - 2, ty, rx - 2 + tw, ty + 12, 0xFFFFB02E, 0xFFE08F00);
                Draw.fill(rx - 2, ty, rx - 2 + tw, ty + 1, 0x88FFFFFF);
                Draw.text(tag, rx + 4, ty + 2, 0xFFFFFFFF, true);
            }
        }

        // dấu X: xoá (= tắt) HUD đang chọn
        if (panelOn) {
            boolean ox = hit(bX, mx, my);
            int x = bX[0], y = bX[1];
            int ccx = x + XS / 2, ccy = y + XS / 2, rr = XS / 2 + 4;
            int xb = ox ? 0xFFD94A3D : 0xFF39404C;
            Draw.disc(ccx + 0.5f, ccy + 0.5f, rr + 1.2f, 0xFFFFFFFF);
            Draw.disc(ccx + 0.5f, ccy + 0.5f, rr, Draw.lerp(xb, 0xFFFFFFFF, 0.22f), Draw.lerp(xb, 0xFF000000, 0.2f));
            for (int i = 2; i < XS - 2; i++) {
                Draw.fill(x + i, y + i, x + i + 1, y + i + 1, 0xFFFFFFFF);
                Draw.fill(x + XS - 1 - i, y + i, x + XS - i, y + i + 1, 0xFFFFFFFF);
            }
        }

        // tiêu đề
        int cx = width / 2;
        String title = "Zhwii Client  -  Sửa vị trí HUD";
        Draw.push();
        Draw.translate(0f, (1f - op) * 10f);
        int tpw = Draw.width(title) / 2 + 14;
        Skin.vgrad(cx - tpw, 5, cx + tpw, 24, Draw.mulAlpha(0xF0F6F7F8, op), Draw.mulAlpha(0xF0D9DCDF, op));
        Draw.ring(cx - tpw, 5, cx + tpw, 24, Draw.mulAlpha(0xFFB8BEC4, op));
        Draw.text(title, cx - Draw.width(title) / 2f, 11, 0xFF3A3F45, false);
        int uw = Math.round((tpw - 4) * op);
        Draw.fill(cx - uw, 23, cx + uw, 24, ORANGE);
        Draw.pop();

        int doneY = height - 28;
        String hint = Draw.fit(
                "Kéo để di chuyển  |  Lăn chuột để đổi cỡ  |  Phím mũi tên để nhích  |  Chuột phải để đặt lại vị trí",
                width - 20);
        Draw.centered(hint, cx, doneY - 13, 0xFFE0E0E0, true);

        int bx = cx - WIDE / 2;
        backHov = drawFlatBtn(bBack, bx, doneY, WIDE, BH, "Quay lại menu",
                Draw.in(mx, my, bx, doneY, WIDE, BH), backHov);

        drawPanel(mx, my);
    }

    // ------------------------------------------------------------------ chuột

    private void click() {
        try {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        } catch (Throwable ignored) { }
    }

    private static void resize(HudModule h, double delta) {
        h.scale.set(h.scale.value + delta);
    }

    private void setSlider(int mx) {
        if (dragSlider == null) return;
        float r = (mx - (dragX + 4)) / (float) (dragW - 8);
        dragSlider.setRatio(Math.max(0f, Math.min(1f, r)));
    }

    private void panelClick(int mx, int my, int btn) {
        if (my < viewTop || my >= viewTop + viewH) return;
        for (PRow r : prows) {
            if (!Draw.in(mx, my, r.x, r.y, r.w, RH)) continue;
            click();
            switch (r.kind) {
                case K_SCALE:
                    if (mx >= r.x + r.w - RH) resize(selected, 0.05);
                    else if (mx >= r.x + r.w - 2 * RH) resize(selected, -0.05);
                    break;
                case K_BOOL:
                    ((BoolSetting) r.s).toggle();
                    break;
                case K_MODE:
                    if (btn == 1) ((ModeSetting) r.s).prev();
                    else ((ModeSetting) r.s).next();
                    break;
                case K_COLOR: {
                    ColorSetting col = (ColorSetting) r.s;
                    int n = ColorSetting.PRESETS.length, i = presetIndex(col.value);
                    int next = btn != 1 ? (i + 1) % n : (i < 0 ? n - 1 : (i + n - 1) % n);
                    col.value = ColorSetting.PRESETS[next];
                    break;
                }
                case K_SLIDER:
                    if (btn == 0) {
                        dragSlider = (SliderSetting) r.s;
                        dragX = r.x;
                        dragW = r.w;
                        setSlider(mx);
                    }
                    break;
                case K_ACTION:
                    if (btn == 0) r.a.run.run();
                    break;
                default:
                    selected.resetSettings();
                    selected.resetPosition();
                    break;
            }
            return;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), btn = event.button();
        layoutPanel();

        if (btn == 0 && hit(bBack, mx, my)) {
            click();
            Compat.setScreen(new MenuScreen());
            return true;
        }

        if (panelOn && Draw.in(mx, my, px, py, PW, ph)) {
            if (my < py + HEAD) {
                if (btn == 0) {
                    dragPanel = true;
                    panOffX = mx - px;
                    panOffY = my - py;
                    panelCustom = true;
                    customX = px;
                    customY = py;
                } else if (btn == 1) {
                    panelCustom = false;
                }
                return true;
            }
            if (btn == 0 && startBarDrag(mx, my)) return true;
            if (btn == 0 || btn == 1) panelClick(mx, my, btn);
            return true;
        }

        if (panelOn && btn == 0 && hit(bX, mx, my)) {
            click();
            if (selected.enabled) selected.toggle();
            selected = null;
            dragging = null;
            LunarLite.saveConfig();
            return true;
        }

        HudModule h = hudAt(mx, my);
        if (h == null) {
            if (btn == 0) selected = null;
            return true;
        }
        select(h);
        if (btn == 1) {
            h.resetPosition();
        } else if (btn == 0) {
            dragging = h;
            offX = mx - h.renderX(width, true);
            offY = my - h.renderY(height, true);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double ddx, double ddy) {
        int mx = (int) event.x(), my = (int) event.y();
        if (dragBar && event.button() == 0) { dragBarTo(my); return true; }
        if (dragPanel) {
            customX = Math.max(0, Math.min(width - PW, mx - panOffX));
            customY = Math.max(0, Math.min(height - ph, my - panOffY));
            return true;
        }
        if (dragSlider != null) { setSlider(mx); return true; }
        if (dragging == null) return super.mouseDragged(event, ddx, ddy);

        int bw = dragging.boxW(true), bh = dragging.boxH(true);
        int nx = mx - offX, ny = my - offY;
        guideV = guideH = false;

        int cxp = (width - bw) / 2, cyp = (height - bh) / 2;
        if (Math.abs(nx - cxp) <= SNAP) { nx = cxp; guideV = true; }
        else if (Math.abs(nx) <= SNAP) nx = 0;
        else if (Math.abs(nx - (width - bw)) <= SNAP) nx = width - bw;

        if (Math.abs(ny - cyp) <= SNAP) { ny = cyp; guideH = true; }
        else if (Math.abs(ny) <= SNAP) ny = 0;
        else if (Math.abs(ny - (height - bh)) <= SNAP) ny = height - bh;

        dragging.x = Math.max(0, Math.min(width - bw, nx));
        dragging.y = Math.max(0, Math.min(height - bh, ny));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean was = dragging != null || dragSlider != null || dragPanel || dragBar;
        dragging = null;
        dragSlider = null;
        dragPanel = false;
        dragBar = false;
        guideV = guideH = false;
        if (was) LunarLite.saveConfig();
        return was || super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        int mx = (int) x, my = (int) y;
        layoutPanel();
        if (panelOn && Draw.in(mx, my, px, py, PW, ph)) {
            panelTarget += (scrollY > 0 ? -1 : 1) * 2f * (RH + GAP);
            panelTarget = Math.max(0f, Math.min(Math.max(0, contentH - viewH), panelTarget));
            return true;
        }
        HudModule h = hudAt(mx, my);
        if (h == null) h = selected;
        if (h != null) {
            resize(h, scrollY > 0 ? 0.05 : -0.05);
            select(h);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ phím

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            Compat.setScreen(new MenuScreen());
            return true;
        }
        if (LunarLite.keyMenu != null && LunarLite.keyMenu.matches(event)) {
            Compat.setScreen(null);
            return true;
        }
        if (selected != null) {
            boolean shift = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            int step = shift ? 5 : 1;
            if (key == GLFW.GLFW_KEY_LEFT) { selected.x = Math.max(0, selected.x - step); return true; }
            if (key == GLFW.GLFW_KEY_RIGHT) { selected.x += step; return true; }
            if (key == GLFW.GLFW_KEY_UP) { selected.y = Math.max(0, selected.y - step); return true; }
            if (key == GLFW.GLFW_KEY_DOWN) { selected.y += step; return true; }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        LunarLite.saveConfig();
        Compat.setScreen(parent);
    }
}
