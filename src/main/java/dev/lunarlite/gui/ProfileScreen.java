package dev.lunarlite.gui;

import dev.lunarlite.Compat;
import dev.lunarlite.ProfileManager;
import dev.lunarlite.SaoFx;
import dev.lunarlite.module.ThemeModule;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Nhập / xuất cài đặt (bản 26.2 của ProfileScreen):
 * danh sách hồ sơ .cfg trong config/zhwii-profiles, chọn rồi bấm Nhập (hoặc bấm đúp / Enter),
 * dán từ clipboard, xuất file mới, sao chép mã, mở thư mục. Trước mỗi lần nhập, cài đặt hiện tại được
 * tự sao lưu vào before-import.cfg.
 */
public class ProfileScreen extends Screen {
    private static final int VW = 340, VH = 258, PAD = 14;
    private static final int LIST_Y = 62, ROW_H = 24, VISIBLE = 4, LIST_H = ROW_H * VISIBLE;
    private static final int ST_Y = 164, BH = 20;
    private static final int LIST_W = VW - 2 * PAD;

    private static final int A_IMPORT = 0, A_CLIP = 1, A_EXPORT = 2, A_COPY = 3, A_FOLDER = 4, A_BACK = 5;
    private static final String[] LABELS = {
        "Nhập file đã chọn", "Dán từ clipboard", "Xuất file mới", "Sao chép mã", "Mở thư mục", "« Quay lại"
    };
    private static final int[] BX = {14, 173, 14, 120, 226, 14};
    private static final int[] BY = {178, 178, 204, 204, 204, 230};
    private static final int[] BW = {153, 153, 100, 100, 100, 312};

    private final Screen parent;
    private final List<File> files = new ArrayList<>();
    private final float[] hov = new float[LABELS.length];
    private int sel = -1, first;
    private String status = "";
    private int statusCol = Skin.DIM;
    private long lastClick;
    private int lastClickIdx = -1;

    public ProfileScreen(Screen parent) {
        super(Component.literal("Nhập / Xuất cài đặt"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() { refresh(null); }

    // ------------------------------------------------------------------ danh sách

    private void refresh(File keep) {
        files.clear();
        files.addAll(ProfileManager.list());
        sel = -1;
        if (keep != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).equals(keep)) {
                    sel = i;
                    break;
                }
            }
        }
        clampFirst();
        if (sel >= 0) {
            if (sel < first) first = sel;
            if (sel >= first + VISIBLE) first = sel - VISIBLE + 1;
        }
    }

    private void clampFirst() {
        first = Math.max(0, Math.min(Math.max(0, files.size() - VISIBLE), first));
    }

    private static String shortName(File f) {
        String n = f.getName();
        return n.toLowerCase(Locale.ROOT).endsWith(".cfg") ? n.substring(0, n.length() - 4) : n;
    }

    private void ok(String s) { status = s; statusCol = Skin.GREEN; }

    private void err(String s) { status = s == null ? "Có lỗi xảy ra." : s; statusCol = Skin.RED; }

    // ------------------------------------------------------------------ hành động

    private void doImport() {
        if (sel < 0 || sel >= files.size()) {
            err("Hãy chọn một hồ sơ trong danh sách.");
            return;
        }
        File f = files.get(sel);
        try {
            int n = ProfileManager.importFile(f);
            ok("Đã nhập " + n + " tính năng từ " + shortName(f) + ".");
        } catch (IOException e) {
            err(e.getMessage());
        }
        refresh(f);
    }

    private void act(int id) {
        SaoFx.ui("menu.click", 1f);
        Minecraft mc = Minecraft.getInstance();
        switch (id) {
            case A_IMPORT:
                doImport();
                break;
            case A_CLIP:
                try {
                    int n = ProfileManager.importText(mc.keyboardHandler.getClipboard());
                    ok("Đã nhập " + n + " tính năng từ clipboard.");
                } catch (IOException e) {
                    err(e.getMessage());
                }
                refresh(null);
                break;
            case A_EXPORT:
                try {
                    File f = ProfileManager.exportNew();
                    refresh(f);
                    ok("Đã xuất: " + f.getName());
                } catch (IOException e) {
                    err(e.getMessage());
                }
                break;
            case A_COPY:
                try {
                    String t = ProfileManager.exportText();
                    mc.keyboardHandler.setClipboard(t);
                    ok("Đã sao chép cài đặt (" + ((t.length() + 512) / 1024) + " KB) vào clipboard.");
                } catch (IOException e) {
                    err(e.getMessage());
                }
                break;
            case A_FOLDER:
                try {
                    if (!java.awt.Desktop.isDesktopSupported()) throw new IOException("unsupported");
                    java.awt.Desktop.getDesktop().open(ProfileManager.dir());
                    ok("Đã mở thư mục hồ sơ.");
                } catch (Throwable t) {
                    err("Không mở được. Thư mục: config/zhwii-profiles");
                }
                break;
            default:
                onClose();
        }
    }

    // ------------------------------------------------------------------ khung ảo <-> màn hình

    private float ui() { return Math.min(1f, Math.min((width - 8f) / VW, (height - 8f) / VH)); }

    private int vx(double mx) { return Math.round((float) ((mx - width / 2f) / ui() + VW / 2f)); }

    private int vy(double my) { return Math.round((float) ((my - height / 2f) / ui() + VH / 2f)); }

    private int rowAt(int mx, int my) {
        for (int k = 0; k < VISIBLE; k++) {
            int i = first + k;
            if (i >= files.size()) break;
            if (Draw.in(mx, my, PAD + 3, LIST_Y + 2 + k * ROW_H, LIST_W - 14, ROW_H - 4)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ vẽ

    private void scaled(String s, float x, float y, float sc, int color) {
        Draw.push();
        Draw.translate(x, y);
        Draw.scale(sc, sc);
        Draw.text(s, 0, 0, color, true);
        Draw.pop();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int rawX, int rawY, float delta) {
        super.extractRenderState(graphics, rawX, rawY, delta);
        Draw.begin(graphics);
        ThemeModule.apply();
        Skin.vgrad(0, 0, width, height, 0xB00A0A12, 0xCC0A0A12);
        int mx = vx(rawX), my = vy(rawY);
        float u = ui();
        Draw.push();
        Draw.translate(width / 2f, height / 2f);
        Draw.scale(u, u);
        Draw.translate(-VW / 2f, -VH / 2f);
        drawPanel(mx, my);
        Draw.pop();
    }

    private void drawPanel(int mx, int my) {
        int acc = Draw.ACCENT;
        Skin.shadow(VW, VH);
        Skin.box(0, 0, VW, VH, 7, 0xFF2C2C3C, Skin.BG);
        Draw.fadeLine(18, 0, VW - 36, acc & 0xFFFFFF, 0xCC);
        Draw.fadeLine(18, 1, VW - 36, acc & 0xFFFFFF, 0x60);

        Skin.tile(PAD, 10, 30, "gear", acc);
        scaled("Nhập / Xuất cài đặt", PAD + 38, 13, 1.3f, Skin.TEXT);
        Draw.text("config/zhwii-profiles", PAD + 38, 28, Skin.DIM, true);
        String hint = "Chọn hồ sơ rồi bấm Nhập. Cài đặt hiện tại được tự sao lưu vào " + ProfileManager.BACKUP + ".";
        Draw.text(Draw.fit(hint, LIST_W), PAD, 48, Skin.DIM, true);

        Skin.box(PAD, LIST_Y, LIST_W, LIST_H, 5, 0xFF1C1C28, 0xFF0F0F15);
        if (files.isEmpty()) {
            Draw.centered("Chưa có hồ sơ nào.", VW / 2f, LIST_Y + 30, Skin.SOFT, true);
            Draw.centered(Draw.fit("Bấm 'Xuất file mới' hoặc chép file .cfg vào thư mục.", LIST_W - 16),
                    VW / 2f, LIST_Y + 44, Skin.DIM, true);
        }
        SimpleDateFormat df = new SimpleDateFormat("dd/MM HH:mm", Locale.ROOT);
        int hover = rowAt(mx, my);
        for (int k = 0; k < VISIBLE; k++) {
            int i = first + k;
            if (i >= files.size()) break;
            File f = files.get(i);
            int rx = PAD + 3, ry = LIST_Y + 2 + k * ROW_H, rw = LIST_W - 14, rh = ROW_H - 4;
            boolean on = i == sel;
            if (on) Skin.box(rx, ry, rw, rh, 4, acc, Draw.lerp(Skin.RAISED, acc, 0.2f));
            else Draw.rrect(rx, ry, rx + rw, ry + rh, 4, i == hover ? Skin.HOVER : Skin.RAISED);
            String right = ((f.length() + 512) / 1024) + " KB · " + df.format(new Date(f.lastModified()));
            int rwid = Draw.width(right);
            Draw.text(right, rx + rw - 8 - rwid, ry + (rh - 8) / 2f, Skin.DIM, true);
            String name = shortName(f);
            if (ProfileManager.BACKUP.equals(f.getName())) name = name + " (sao lưu)";
            Draw.text(Draw.fit(name, rw - rwid - 24), rx + 8, ry + (rh - 8) / 2f, on ? Skin.TEXT : Skin.SOFT, true);
        }
        if (files.size() > VISIBLE) {
            int tx = PAD + LIST_W - 8, ty0 = LIST_Y + 3, th = LIST_H - 6;
            Draw.rrect(tx, ty0, tx + 3, ty0 + th, 1, 0x55000000);
            int bh = Math.max(12, th * VISIBLE / files.size());
            int by = ty0 + Math.round((th - bh) * (first / (float) (files.size() - VISIBLE)));
            Draw.rrect(tx, by, tx + 3, by + bh, 1, Draw.ACCENT_LIGHT);
        }

        if (!status.isEmpty()) Draw.text(Draw.fit(status, LIST_W), PAD, ST_Y, statusCol, true);

        for (int id = 0; id < LABELS.length; id++) {
            boolean over = Draw.in(mx, my, BX[id], BY[id], BW[id], BH);
            hov[id] += ((over ? 1f : 0f) - hov[id]) * 0.35f;
            if (Math.abs((over ? 1f : 0f) - hov[id]) < 0.01f) hov[id] = over ? 1f : 0f;
            Skin.button(BX[id], BY[id], BW[id], BH, LABELS[id], hov[id], id == A_IMPORT ? Skin.PRIMARY : Skin.NORMAL);
        }
    }

    // ------------------------------------------------------------------ nhập liệu

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mx = vx(event.x()), my = vy(event.y());
        for (int id = 0; id < LABELS.length; id++) {
            if (Draw.in(mx, my, BX[id], BY[id], BW[id], BH)) {
                act(id);
                return true;
            }
        }
        int i = rowAt(mx, my);
        if (i >= 0) {
            long now = System.currentTimeMillis();
            boolean dbl = i == lastClickIdx && now - lastClick < 350L;
            sel = i;
            lastClickIdx = i;
            lastClick = now;
            SaoFx.ui("menu.click", 1f);
            if (dbl) doImport();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        first += scrollY > 0 ? -1 : 1;
        clampFirst();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            doImport();
            return true;
        }
        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && !files.isEmpty()) {
            int n = sel < 0 ? 0 : sel + (key == GLFW.GLFW_KEY_UP ? -1 : 1);
            sel = Math.max(0, Math.min(files.size() - 1, n));
            if (sel < first) first = sel;
            if (sel >= first + VISIBLE) first = sel - VISIBLE + 1;
            return true;
        }
        return super.keyPressed(event); // ESC -> onClose()
    }

    @Override
    public void onClose() {
        Compat.setScreen(parent != null ? parent : new MenuScreen());
    }
}
