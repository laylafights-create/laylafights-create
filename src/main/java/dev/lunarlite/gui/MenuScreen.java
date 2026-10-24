package dev.lunarlite.gui;

import dev.lunarlite.Compat;
import dev.lunarlite.Lang;
import dev.lunarlite.LunarLite;
import dev.lunarlite.SaoFx;
import dev.lunarlite.module.AnimationsModule;
import dev.lunarlite.module.Category;
import dev.lunarlite.module.HudModule;
import dev.lunarlite.module.Module;
import dev.lunarlite.module.ThemeModule;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.Setting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Menu kiểu Sword Art Online (bản 26.2 của menu 3 cột trong bản Forge 1.8.9):
 * thẻ nhân vật bên trái -> cột nút tròn (nhóm) -> danh sách tính năng -> bảng tuỳ chỉnh.
 * Vẽ hoàn toàn bằng {@link Draw}/{@link Skin} trong khung ảo VW x VH, tự co cho vừa màn hình.
 * Khác bản gốc: thẻ nhân vật vẽ mặt (không có mô hình 3D xoay), không có hạt nổ AnimeFx, nền mờ do game tự vẽ.
 */
public class MenuScreen extends Screen {
    // ---- khung ảo
    private static final int VW = 520, VH = 254, PAD = 14;
    private static final int BH = 26, ROW = 30, BAR_W = 6;
    private static final float MENU_SCALE = 0.95f;

    // ---- bố cục kiểu menu SAO
    private static final int CX = 232, CR = 14;
    private static final int LPX0 = 268, LPW0 = 238, LPY = 14, LPH = 226, LRH = 26, LSTEP = 30;
    private static final int DET_LPX = 158, DET_LPW = 120;
    private static final int CPX = 16, CPY = 14, CPW = 160, CPH = 226;
    private static final int DETAIL_X = 302, HOME_OFF = 122, GROUP_DX = 126;
    private static final float DET_CENTER_OFF = (VW - ((CPX - HOME_OFF) + (VW - PAD - 10))) / 2f;
    private static final int SAO_ORANGE = 0xFFF0A30A;
    /** y chân nhân vật (như bản 1.8.9): thân cao ~137 nên đầu sát dưới thanh tiêu đề. */
    private static final int ARM_FEET = CPY + CPH - 46;
    private static final int[] ICON_COLORS = {
        0xFF3FB86F, 0xFFE8505B, 0xFFF0A30A, 0xFF3F8EF0, 0xFF9B59D6, 0xFF1FB5A8, 0xFFE0609E, 0xFFD9A400
    };
    private static final String[] ACT_LABEL = {"Nhập / Xuất", "Sửa vị trí HUD", "Đăng xuất", "Xong"};
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    // ---- kiểu hàng trong bảng tuỳ chỉnh
    private static final int K_HEADER = 0, K_ENABLE = 1, K_BOOL = 2, K_MODE = 3, K_COLOR = 4, K_SLIDER = 5,
            K_ACTION = 6, K_RESET = 7;

    private static final class DRow {
        int kind, y, h = ROW, id;
        String header;
        Setting s;
        Module m;
        Module.Action a;
        float hov, sw = -1f;
    }

    /** Tính năng yêu thích (id) hiện lên đầu danh sách; chỉ nhớ trong phiên chơi. */
    private static final Set<String> FAVS = new HashSet<>();
    private static final Comparator<Module> BY_NAME = (a, b) -> {
        boolean fa = FAVS.contains(a.id), fb = FAVS.contains(b.id);
        if (fa != fb) return fa ? -1 : 1;
        return Lang.vi(a.name).compareToIgnoreCase(Lang.vi(b.name));
    };

    private static Module lastModule;
    private static Category lastTab;
    static long animStart;

    // ---- trạng thái
    private Module module;
    private boolean detail;
    private Category tabCat;
    private final List<Category> tabCats = new ArrayList<>();
    private final List<Module> shown = new ArrayList<>();
    private final Map<Object, Float> hovAnim = new HashMap<>();
    private final List<DRow> rows = new ArrayList<>();
    private boolean expanded, actionsOpen;
    private float shift, dshift, ctrCur, listYCur = -1f, listScroll, listTarget, setScroll, setTarget, scrollK = 1f, barAnim;
    private int contentH, curMx, curMy;
    private long lastFrameNs;
    private boolean dragBar;
    private int dragOff;
    private DRow dragSlider;
    private int pressCircle = -1, lastHoverSfx = -1;
    private long pressAt;
    private boolean soundOpened;
    private float txK = 1f, txX0, txY0; // phép biến đổi khung ảo -> toạ độ GUI (để vẽ mô hình 3D đúng chỗ)

    private final long openStart;
    private long closeStart;
    private boolean closing;

    // hộp xác nhận bật / tắt / đăng xuất
    private static final int DW = 214, DH = 122, DX = (VW - DW) / 2, DY = (VH - DH) / 2, DR = 13;
    private Module confirmMod;
    private boolean confirmQuit;
    private long confirmAt;
    private float okHov, noHov;

    public MenuScreen() {
        super(Component.literal("Zhwii Client"));
        openStart = animStart = System.currentTimeMillis();
        module = lastModule;
        if (module == null || !LunarLite.MODULES.contains(module)) {
            module = LunarLite.MODULES.isEmpty() ? null : LunarLite.MODULES.get(0);
        }
        tabCat = null;
        buildShown();
        focusModule();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ âm thanh

    private void play(String ev) { SaoFx.ui(ev, 1f); }

    // ------------------------------------------------------------------ khung ảo <-> màn hình

    private float ui() {
        float vw = VW + HOME_OFF * dshift;
        return Math.min(MENU_SCALE, Math.min((width - 8f) / vw, (height - 8f) / VH));
    }

    private int vx(double mx) { return Math.round((float) ((mx - width / 2f) / ui() + VW / 2f - ctrCur)); }

    private int vy(double my) { return Math.round((float) ((my - height / 2f) / ui() + VH / 2f)); }

    /**
     * Ở 26.x {@code enableScissor} đã được nhân với ma trận pose hiện tại, nên truyền thẳng toạ độ khung ảo
     * (không tự đổi sang toạ độ màn hình nữa, nếu không sẽ bị biến đổi 2 lần => vùng cắt lệch phải, mất mép trái hàng).
     */
    private void clip(int x, int y, int w, int h) {
        Draw.scissor(x, y, w, h);
    }

    private int paneX() { return DETAIL_X; }
    private int paneW() { return VW - DETAIL_X - PAD - 10; }
    private int paneTop() { return LPY; }
    private int paneBottom() { return LPY + LPH; }
    private int setViewH() { return paneBottom() - paneTop(); }

    // ------------------------------------------------------------------ nhóm + danh sách

    private void buildShown() {
        shown.clear();
        tabCats.clear();
        for (Category c : Category.values()) {
            List<Module> inCat = new ArrayList<>();
            for (Module m : LunarLite.MODULES) if (m.category == c) inCat.add(m);
            if (inCat.isEmpty()) continue;
            tabCats.add(c);
            if (tabCat != null && c != tabCat) continue;
            Collections.sort(inCat, BY_NAME);
            shown.addAll(inCat);
        }
        if (shown.isEmpty() && tabCat != null) {
            tabCat = lastTab = null;
            buildShown();
        }
    }

    private int tabCount() { return 1 + tabCats.size(); }
    private int tabIndex() { return tabCat == null ? 0 : tabCats.indexOf(tabCat) + 1; }
    private String tabLabel(int i) { return i == 0 ? "Tất cả" : tabCats.get(i - 1).label; }

    private void setTab(int i) {
        Category c = i <= 0 || i > tabCats.size() ? null : tabCats.get(i - 1);
        tabCat = c;
        lastTab = c;
        listScroll = listTarget = 0f;
        animStart = System.currentTimeMillis();
        buildShown();
    }

    private void cycleTab(int d) {
        int n = tabCount();
        setTab((tabIndex() + d + n) % n);
    }

    private int circleCount() { return tabCount() + 1; }

    private int circleStep() {
        int n = circleCount();
        return n <= 1 ? 42 : Math.min(42, (LPH + 24 - 2 * (CR + 3)) / (n - 1));
    }

    private int circleY(int i) {
        int total = (circleCount() - 1) * circleStep();
        return LPY + (LPH - total) / 2 + i * circleStep();
    }

    private int gdx() { return Math.round(GROUP_DX * (1f - shift) - dshift * HOME_OFF); }
    private int lpx() { return Math.round(LPX0 + (DET_LPX - LPX0) * dshift); }
    private int lpw() { return Math.round(LPW0 + (DET_LPW - LPW0) * dshift); }

    private int selCircleRaw() { return actionsOpen ? tabCount() : tabIndex(); }
    private int selCircle() { return expanded ? selCircleRaw() : -1; }
    private int listCount() { return actionsOpen ? ACT_LABEL.length : shown.size(); }
    private int listContentH() { return Math.max(0, listCount() * LSTEP - (LSTEP - LRH)); }
    private int listH() { return Math.min(7 * LSTEP - (LSTEP - LRH), listContentH()); }
    private int listMax() { return Math.max(0, listContentH() - listH()); }

    private int listYTarget() {
        int h = listH();
        return Math.max(LPY, Math.min(LPY + LPH - h, circleY(selCircleRaw()) - h / 2));
    }

    private int listY() { return Math.round(listYCur < 0f ? listYTarget() : listYCur); }

    private void clampList() {
        int max = listMax();
        listTarget = Math.max(0f, Math.min(max, listTarget));
        listScroll = Math.max(0f, Math.min(max, listScroll));
    }

    private void focusModule() {
        listScroll = listTarget = 0f;
        int i = shown.indexOf(module);
        if (i >= 0) listTarget = listScroll = Math.round((i * LSTEP - (listH() - LRH) / 2f) / LSTEP) * (float) LSTEP;
        clampList();
    }

    private int circleAt(int mx, int my) {
        for (int i = 0; i < circleCount(); i++) {
            int dx = mx - gdx() - CX, dy = my - circleY(i);
            if (dx * dx + dy * dy <= (CR + 3) * (CR + 3)) return i;
        }
        return -1;
    }

    private int rowAt(int mx, int my) {
        if (!Draw.in(mx, my, lpx(), listY(), lpw(), listH())) return -1;
        int rel = my - listY() + Math.round(listScroll);
        int i = rel / LSTEP;
        if (rel % LSTEP >= LRH || i < 0 || i >= listCount()) return -1;
        return i;
    }

    private float hover(Object key, boolean over, float k) {
        Float cur = hovAnim.get(key);
        float hv = cur == null ? 0f : cur;
        hv += ((over ? 1f : 0f) - hv) * k;
        if (Math.abs((over ? 1f : 0f) - hv) < 0.01f) hv = over ? 1f : 0f;
        hovAnim.put(key, hv);
        return hv;
    }

    // ------------------------------------------------------------------ mở / đóng / chuyển trang

    private float openProgress() {
        if (!AnimationsModule.menu()) return closing ? 0f : 1f;
        float sp = AnimationsModule.speed();
        long n = System.currentTimeMillis();
        if (closing) return 1f - AnimationsModule.ease((n - closeStart) * sp / 150f);
        return AnimationsModule.ease((n - openStart) * sp / 220f);
    }

    private void requestClose() {
        if (closing) return;
        play("menu.close");
        if (!AnimationsModule.menu()) {
            onClose();
            return;
        }
        closing = true;
        closeStart = System.currentTimeMillis();
    }

    @Override
    public void tick() {
        super.tick();
        if (closing && openProgress() <= 0.001f) onClose();
    }

    private void openModule(Module m) {
        if (m == null) return;
        play("menu.select");
        module = m;
        lastModule = m;
        int mi = shown.indexOf(m);
        if (mi >= 0) {
            float rt = mi * LSTEP;
            if (rt < listTarget) listTarget = rt;
            else if (rt + LRH > listTarget + listH()) listTarget = rt + LRH - listH();
            clampList();
        }
        detail = true;
        setScroll = setTarget = 0f;
        animStart = System.currentTimeMillis();
        rebuild();
    }

    private void goBack() {
        play("menu.back");
        detail = false;
        dragSlider = null;
        dragBar = false;
        animStart = System.currentTimeMillis();
        rebuild();
        focusModule();
    }

    private void moveModule(int d) {
        int i = shown.indexOf(module);
        int j = i + d;
        if (i < 0 || j < 0 || j >= shown.size()) return;
        openModule(shown.get(j));
    }

    private void rebuild() {
        buildShown();
        if (detail) buildRows();
        else {
            rows.clear();
            contentH = 0;
        }
    }

    private void toggleModule(Module m) {
        m.toggle();
        play(m.enabled ? "menu.on" : "menu.off");
        LunarLite.saveConfig();
    }

    // ------------------------------------------------------------------ hộp xác nhận

    private void askToggle(Module m) {
        if (m == null) return;
        play("menu.select");
        confirmMod = m;
        confirmAt = System.currentTimeMillis();
        okHov = noHov = 0f;
    }

    private void askQuit() {
        if (LunarLite.MODULES.isEmpty()) return;
        play("menu.select");
        confirmQuit = true;
        confirmMod = LunarLite.MODULES.get(0);
        confirmAt = System.currentTimeMillis();
        okHov = noHov = 0f;
    }

    private void closeConfirm(boolean ok) {
        Module m = confirmMod;
        boolean quit = confirmQuit;
        confirmMod = null;
        confirmQuit = false;
        if (ok && quit) {
            LunarLite.saveConfig();
            if (!Compat.disconnect()) requestClose();
        } else if (ok && m != null) toggleModule(m);
        else play("menu.back");
    }

    private boolean onOk(int mx, int my) {
        int cx = DX + DW / 2 - 36, cy = DY + DH - 19;
        return (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= (DR + 3) * (DR + 3);
    }

    private boolean onNo(int mx, int my) {
        int cx = DX + DW / 2 + 36, cy = DY + DH - 19;
        return (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= (DR + 3) * (DR + 3);
    }

    private void confirmClick(int mx, int my) {
        if (onOk(mx, my)) closeConfirm(true);
        else if (onNo(mx, my) || !Draw.in(mx, my, DX, DY, DW, DH)) closeConfirm(false);
    }

    private void drawConfirm(int mx, int my) {
        float k = AnimationsModule.menu() ? 0.3f : 1f;
        okHov += ((onOk(mx, my) ? 1f : 0f) - okHov) * k;
        noHov += ((onNo(mx, my) ? 1f : 0f) - noHov) * k;
        float pop = AnimationsModule.menu()
                ? Math.max(0f, AnimationsModule.easeOutBack((System.currentTimeMillis() - confirmAt) * AnimationsModule.speed() / 220f)) : 1f;
        Draw.fill(-VW, -VH, 2 * VW, 2 * VH, Math.round(0x70 * Math.min(1f, pop)) << 24);
        Draw.push();
        Draw.translate(VW / 2f, VH / 2f);
        Draw.scale(pop, pop);
        Draw.translate(-VW / 2f, -VH / 2f);

        boolean on = confirmMod.enabled;
        Draw.fill(DX - 1, DY - 1, DX + DW + 1, DY + DH + 2, 0x66000000);
        Skin.vgrad(DX, DY, DX + DW, DY + 22, 0xFFF6F7F8, 0xFFDEE0E2);
        Draw.fill(DX, DY + 22, DX + DW, DY + 23, 0xFFB9BCBF);
        String title = confirmQuit ? "Đăng xuất" : on ? "Tắt tính năng" : "Bật tính năng";
        Draw.text(title, DX + (DW - Draw.width(title)) / 2f, DY + 7, 0xFF3A3F45, false);
        Draw.fill(DX, DY + 23, DX + DW, DY + DH - 38, 0xF28E9296);
        Draw.fill(DX, DY + 23, DX + DW, DY + 24, 0x22000000);
        String l1 = confirmQuit ? "Bạn có muốn thoát khỏi map" : on ? "Bạn có muốn tắt" : "Bạn có muốn bật";
        String l2 = confirmQuit ? "và về danh sách server?" : Draw.fit(Lang.vi(confirmMod.name), DW - 24) + "?";
        Draw.text(l1, DX + (DW - Draw.width(l1)) / 2f, DY + 40, 0xFFFFFFFF, true);
        Draw.text(l2, DX + (DW - Draw.width(l2)) / 2f, DY + 54, 0xFFFFE2A8, true);
        Skin.vgrad(DX, DY + DH - 38, DX + DW, DY + DH, 0xFFF1F2F3, 0xFFDEE0E2);
        Draw.fill(DX, DY + DH - 38, DX + DW, DY + DH - 37, 0xFFFFFFFF);
        Draw.fadeLine(DX + 12, DY + DH - 37, DW - 24, Draw.ACCENT & 0xFFFFFF, 0x80);
        int cy = DY + DH - 19;
        int okx = DX + DW / 2 - 36, nox = DX + DW / 2 + 36;
        Draw.disc(okx, cy + 1.5f, DR + 2f, 0x30000000, 0x00000000);
        if (okHov > 0.02f) Draw.disc(okx, cy, DR + 6f, Math.round(okHov * 80) << 24 | 0x2F7CF0, 0x002F7CF0);
        Draw.disc(okx, cy, DR + 1.5f, 0xFFFFFFFF);
        Draw.disc(okx, cy, DR, Draw.lerp(0xFF63A4FF, 0xFF8FC0FF, okHov), Draw.lerp(0xFF1F63D8, 0xFF2F7CF0, okHov));
        Draw.circleRing(okx, cy, 4.2f, 6.2f, 0xFFFFFFFF);
        Draw.disc(nox, cy + 1.5f, DR + 2f, 0x30000000, 0x00000000);
        if (noHov > 0.02f) Draw.disc(nox, cy, DR + 6f, Math.round(noHov * 80) << 24 | 0xE8506E, 0x00E8506E);
        Draw.disc(nox, cy, DR + 1.5f, 0xFFFFFFFF);
        Draw.disc(nox, cy, DR, Draw.lerp(0xFFFF8CA3, 0xFFFFA9BB, noHov), Draw.lerp(0xFFD93A5E, 0xFFE8506E, noHov));
        Draw.line(nox - 4.5f, cy - 4.5f, nox + 4.5f, cy + 4.5f, 2.4f, 0xFFFFFFFF);
        Draw.line(nox + 4.5f, cy - 4.5f, nox - 4.5f, cy + 4.5f, 2.4f, 0xFFFFFFFF);
        Skin.brackets(DX - 3, DY - 3, DW + 6, DH + 6, 8, (0xB0 << 24) | (Draw.ACCENT & 0xFFFFFF));
        Draw.pop();
    }

    // ------------------------------------------------------------------ bảng tuỳ chỉnh: dựng hàng

    private static int rank(Setting st) {
        if (st instanceof BoolSetting) return 0;
        if (st instanceof ModeSetting) return 1;
        if (st instanceof ColorSetting) return 2;
        return 3;
    }

    private DRow row(int kind, Setting s, String header) {
        DRow r = new DRow();
        r.kind = kind;
        r.s = s;
        r.header = header;
        r.m = module;
        rows.add(r);
        return r;
    }

    private static boolean narrow(int w) { return w < 200; }

    private void buildRows() {
        rows.clear();
        contentH = 0;
        Module m = module;
        if (m == null) return;
        row(K_ENABLE, null, null);

        List<Setting> vis = new ArrayList<>();
        for (Setting st : m.settings) if (st.isVisible()) vis.add(st);
        List<String> found = new ArrayList<>();
        for (Setting st : vis) if (!found.contains(st.section)) found.add(st.section);
        List<String> sections = new ArrayList<>();
        for (String sec : found) if (!"Appearance".equals(sec) && !"Rainbow".equals(sec)) sections.add(sec);
        if (found.contains("Appearance")) sections.add("Appearance");
        if (found.contains("Rainbow")) sections.add("Rainbow");

        List<Module.Action> acts = m.actions();
        boolean heads = sections.size() > 1 || (sections.size() == 1 && !acts.isEmpty());
        for (String sec : sections) {
            if (heads) row(K_HEADER, null, Lang.vi(sec));
            List<Setting> inSec = new ArrayList<>();
            for (Setting st : vis) if (sec.equals(st.section)) inSec.add(st);
            inSec.sort((x, y) -> rank(x) - rank(y));
            for (Setting st : inSec) {
                int kind = st instanceof SliderSetting ? K_SLIDER : st instanceof BoolSetting ? K_BOOL
                        : st instanceof ModeSetting ? K_MODE : st instanceof ColorSetting ? K_COLOR : -1;
                if (kind < 0) continue;
                DRow r = row(kind, st, null);
                if (kind == K_SLIDER && narrow(paneW())) r.h = BH + 14 + (ROW - BH);
            }
        }
        if (!acts.isEmpty()) {
            if (heads) row(K_HEADER, null, "Thao tác");
            for (Module.Action a : acts) {
                DRow r = row(K_ACTION, null, null);
                r.a = a;
            }
        }
        row(K_HEADER, null, "");
        row(K_RESET, null, null);

        int y = 4, bid = 0;
        for (DRow r : rows) {
            r.y = y;
            r.id = r.kind == K_HEADER ? 0 : bid++;
            y += r.h;
        }
        contentH = y + 4;
    }

    private void clampScroll() {
        float max = Math.max(0, contentH - setViewH());
        setTarget = Math.max(0f, Math.min(max, setTarget));
        setScroll = Math.max(0f, Math.min(max, setScroll));
    }

    private static long visSig(Module m) {
        long sig = 0;
        int i = 0;
        for (Setting st : m.settings) {
            if (st.isVisible() && i < 63) sig |= 1L << i;
            i++;
        }
        return sig;
    }

    private static int presetIndex(int rgb) {
        for (int i = 0; i < ColorSetting.PRESETS.length; i++) if (ColorSetting.PRESETS[i] == (rgb & 0xFFFFFF)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ vẽ

    @Override
    public void init() {
        if (!soundOpened) {
            soundOpened = true;
            play("menu.open");
        }
        rebuild();
    }

    private void text(String s, float x, float y, float sc, int color, boolean shadow) {
        Draw.push();
        Draw.translate(x, y);
        Draw.scale(sc, sc);
        Draw.text(s, 0, 0, color, shadow);
        Draw.pop();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Draw.begin(graphics);
        ThemeModule.apply();
        if (detail && module != null && module.consumeUiDirty()) rebuild();
        curMx = vx(mouseX);
        curMy = vy(mouseY);
        float op = openProgress();
        drawBackdrop(op);

        long nowNs = System.nanoTime();
        float dt = lastFrameNs == 0L ? 0.016f : Math.min(0.1f, (nowNs - lastFrameNs) / 1e9f);
        lastFrameNs = nowNs;
        scrollK = AnimationsModule.menu() ? 1f - (float) Math.exp(-dt * 14f) : 1f;
        float k = scrollK;
        setScroll += (setTarget - setScroll) * k;
        if (Math.abs(setTarget - setScroll) < 0.4f) setScroll = setTarget;
        clampScroll();

        float pop = 0.88f + 0.12f * op;
        if (!closing && AnimationsModule.menu()) {
            pop = 0.80f + 0.20f * AnimationsModule.easeOutBack((System.currentTimeMillis() - openStart) * AnimationsModule.speed() / 420f);
        }
        float kk = pop * ui();
        float ctrT = confirmMod != null ? 0f : DET_CENTER_OFF * dshift;
        ctrCur += (ctrT - ctrCur) * k;
        if (Math.abs(ctrT - ctrCur) < 0.2f) ctrCur = ctrT;

        txK = kk;
        txX0 = width / 2f + (-VW / 2f + ctrCur) * kk;
        txY0 = height / 2f + (1f - op) * 10f - VH / 2f * kk;
        Draw.push();
        Draw.translate(width / 2f, height / 2f + (1f - op) * 10f);
        Draw.scale(kk, kk);
        Draw.translate(-VW / 2f + ctrCur, -VH / 2f);
        drawPanel(confirmMod != null ? -9999 : curMx, confirmMod != null ? -9999 : curMy);
        if (confirmMod != null) drawConfirm(curMx, curMy);
        Draw.pop();
        AnimeFx.drawBursts();
    }

    /** Nền Aincrad: tối viền, hạt sáng bay chậm lên trên, tia sáng ngang quét ra khi mở menu. */
    private void drawBackdrop(float op) {
        long t = System.currentTimeMillis();
        int va = Math.round(0x50 * Math.max(0f, Math.min(1f, op)));
        Skin.vgrad(0, 0, width, height / 3, va << 24, 0);
        Skin.vgrad(0, height * 2 / 3, width, height, 0, Math.min(255, va + 0x10) << 24);
        int acc = Draw.ACCENT & 0xFFFFFF;
        for (int i = 0; i < 40; i++) {
            long h = (i + 1) * 2654435761L;
            float fx = ((h >>> 4) & 1023) / 1023f;
            float ph = ((h >>> 14) & 1023) / 1023f;
            float per = 16000f + ((h >>> 24) & 7) * 2600f;
            float life = (t / per + ph) % 1f;
            float px = fx * width + (float) Math.sin(t / 2100.0 + i) * 7f;
            float py = height * (1.04f - life * 1.08f);
            int a = Math.round((float) Math.sin(Math.PI * life) * 0x78 * op);
            if (a < 6) continue;
            int sz = (i % 4 == 0) ? 2 : 1;
            Draw.fill(Math.round(px), Math.round(py), Math.round(px) + sz, Math.round(py) + sz,
                    (a << 24) | (i % 5 == 0 ? acc : 0xFFFFFF));
        }
        if (op < 0.98f) {
            float e = closing ? op : 1f - op;
            int bw = Math.round(width * (closing ? 0.5f + 0.4f * op : 0.9f * op + 0.05f));
            int cy = height / 2;
            int a = Math.round(0xE0 * (closing ? 1f - op : e)) & 0xFF;
            Draw.fadeLine(width / 2 - bw / 2, cy - 1, bw, 0xFFFFFF, a / 3);
            Draw.fadeLine(width / 2 - bw / 2, cy, bw, 0xFFFFFF, a);
            Draw.fadeLine(width / 2 - bw / 2, cy + 1, bw, acc, a / 3);
        }
    }

    private void drawPanel(int mx, int my) {
        float ts = detail && module != null ? 1f : 0f;
        dshift += (ts - dshift) * scrollK;
        if (Math.abs(ts - dshift) < 0.004f) dshift = ts;
        drawHome(mx, my);
        if (!detail || module == null) return;
        Draw.push();
        Draw.translate((1f - AnimationsModule.easeOutBack(dshift)) * 90f, 0f);
        drawDetail(mx, my);
        Draw.pop();
    }

    // ---- thẻ nhân vật (port 1:1 từ drawHome của bản 1.8.9)

    private void drawCharacterCard(float pa, long now) {
        Minecraft mc = Minecraft.getInstance();
        Draw.push();
        Draw.translate(-(1f - pa) * 36f, 0f);
        // nghiêng nhẹ rồi dựng thẳng khi vào
        Draw.translate(CPX + CPW / 2f, CPY + CPH / 2f);
        Draw.rotate((float) Math.toRadians(-(1f - Math.min(1f, pa)) * 5f));
        Draw.translate(-(CPX + CPW / 2f), -(CPY + CPH / 2f));
        Draw.fill(CPX - 1, CPY - 1, CPX + CPW + 1, CPY + CPH + 2, 0x55000000);
        Skin.vgrad(CPX, CPY, CPX + CPW, CPY + CPH, 0xFFFCFCFD, 0xFFEBECEE);
        Skin.vgrad(CPX, CPY, CPX + CPW, CPY + 22, 0xFFF3F4F5, 0xFFE0E2E4);
        Draw.fill(CPX, CPY + 22, CPX + CPW, CPY + 23, 0xFFD2D4D6);
        Draw.fadeLine(CPX + 10, CPY + 23, CPW - 20, Draw.ACCENT & 0xFFFFFF, 0x90); // vạch màu nhấn dưới tiêu đề
        Draw.fill(CPX, CPY + 24, CPX + CPW, CPY + 40, 0x0AFFFFFF);                  // ánh sáng nhẹ đầu thẻ

        // tên người chơi: phóng to, canh giữa tiêu đề, xám than + bóng nhấn nhẹ
        String pname = mc.player != null ? mc.player.getName().getString() : "Zhwii";
        int nw = Math.max(1, Draw.width(pname));
        float ns = Math.min(2.0f, (CPW - 20f) / nw);
        float nx = CPX + (CPW - nw * ns) / 2f;
        float ny = CPY + (22f - 8f * ns) / 2f + 0.5f;
        Draw.push();
        Draw.translate(nx, ny);
        Draw.scale(ns, ns);
        Draw.text(pname, 1, 1, Draw.withAlpha(Draw.ACCENT & 0xFFFFFF, 0x55), false);
        Draw.text(pname, 0, 0, 0xFF26303B, false);
        Draw.pop();

        // dấu ngoặc ở góc thẻ, nhấp nháy nhẹ như cửa sổ hologram
        int brA = 120 + Math.round(70f * (float) Math.sin(now / 520.0));
        Skin.brackets(CPX - 3, CPY - 3, CPW + 6, CPH + 8, 7, (brA << 24) | (Draw.ACCENT & 0xFFFFFF));

        // 4 ô giáp quanh nhân vật, xen kẽ trái / phải, mỗi ô nối tới đúng bộ phận (mũ, giáp, quần, giày)
        int ex0 = CPX + CPW / 2, fy = ARM_FEET;
        int[] aOff = {-120, -77, -38, -8};  // tâm ô so với chân nhân vật
        int[] aTgt = {-17, 34, -17, 17};    // điểm nối so với trục nhân vật (âm = bên trái)
        for (int a2 = 0; a2 < 4; a2++) {
            boolean left = (a2 & 1) == 0;
            int sx = left ? CPX + 9 : CPX + CPW - 9 - 22;
            int cyS = fy + aOff[a2], sy = cyS - 11;
            int tx = ex0 + aTgt[a2];
            int lx1 = left ? sx + 22 : tx, lx2 = left ? tx : sx;
            Draw.fill(lx1, cyS, lx2, cyS + 1, 0xFFB4B9BE);
            Draw.disc(tx + 0.5f, cyS + 0.5f, 2.2f, 0xFFF0A30A);
            Draw.fill(sx, sy, sx + 22, sy + 22, 0xFFEDEEEF);
            Draw.fill(sx + 1, sy + 1, sx + 21, sy + 21, 0xFF7C848B);
            Draw.fill(sx + 1, sy + 1, sx + 21, sy + 3, 0x55000000);   // bóng lõm phía trên ô
            Draw.fill(sx + 1, sy + 1, sx + 3, sy + 21, 0x33000000);
            Draw.fill(sx + 1, sy + 19, sx + 21, sy + 21, 0x22FFFFFF); // ánh sáng mép dưới
            ItemStack ar = mc.player != null ? mc.player.getItemBySlot(ARMOR[a2]) : ItemStack.EMPTY;
            if (!ar.isEmpty()) Draw.item(ar, sx + 3, sy + 3);
        }
        Draw.pop();
    }

    /**
     * Nhân vật vẽ sau cùng (như bản 1.8.9) để không bị danh sách đè: bóng mềm dưới chân + mô hình 3D khoá tư thế
     * (không đi bộ, không cúi, không vung tay, không giật khi bị đánh), đứng thẳng nhìn thẳng ra trước.
     */
    private void drawHero(float pa, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || pa <= 0.5f) return;
        float offX = gdx() - (1f - pa) * 36f;
        int ex = CPX + CPW / 2, ey = ARM_FEET;
        Draw.push();
        Draw.translate(offX, 0f);
        Draw.rrect(ex - 32, ey - 1, ex + 32, ey + 5, 3, 0x14000000);
        Draw.rrect(ex - 24, ey, ex + 24, ey + 4, 2, 0x1E000000);
        Draw.rrect(ex - 15, ey + 1, ex + 15, ey + 3, 1, 0x2A000000);
        Draw.pop();

        // GuiEntityRenderState đặt tâm hộp ở giữa thân: chân = tâm + (cao/2 + 0.0625) * scale, scale = 72 như 1.8.9
        final float S = 72f;
        float cyBox = ey - (0.9f + 0.0625f) * S;
        float sx1 = txX0 + (ex - 60 + offX) * txK, sx2 = txX0 + (ex + 60 + offX) * txK;
        float sy1 = txY0 + (cyBox - 75f) * txK, sy2 = txY0 + (cyBox + 75f) * txK;
        boolean drew;
        Runnable restore = lockPose(mc.player);
        try {
            // chuột đặt đúng tâm hộp => yaw = pitch = 0, nhân vật nhìn thẳng
            drew = drawModel(Draw.g, Math.round(sx1), Math.round(sy1), Math.round(sx2), Math.round(sy2),
                    S * txK, (sx1 + sx2) / 2f, (sy1 + sy2) / 2f, mc.player);
        } finally {
            restore.run();
        }
        if (drew) return;

        // không gọi được mô hình 3D: vẽ khung mặt
        Draw.push();
        Draw.translate(offX, 0f);
        int hs = 64, hx = ex - hs / 2, hy = CPY + 70;
        Draw.rrect(hx - 4, hy - 4, hx + hs + 4, hy + hs + 4, 6, 0xFFB4B9BE);
        Draw.rrect(hx - 3, hy - 3, hx + hs + 3, hy + hs + 3, 5, 0xFFEDEEEF);
        boolean ok = false;
        if (mc.getConnection() != null) ok = Draw.head(mc.getConnection().getPlayerInfo(mc.player.getUUID()), hx, hy, hs);
        if (!ok) {
            Draw.rrect(hx, hy, hx + hs, hy + hs, 3, 0xFF9AA0A6);
            String nm = mc.player.getName().getString();
            Draw.centered(nm.isEmpty() ? "?" : nm.substring(0, 1).toUpperCase(), ex, hy + hs / 2f - 4, 0xFFFFFFFF, true);
        }
        Draw.pop();
    }

    // ---- khoá tư thế bằng reflection (tên field đổi giữa các bản 26.x; sai tên thì bỏ qua, không crash)

    private static final java.util.Map<String, java.lang.reflect.Field> FIELDS = new java.util.HashMap<>();

    /** Tìm field một lần rồi nhớ (kể cả khi không có) - tránh ném NoSuchFieldException mỗi khung hình. */
    private static java.lang.reflect.Field field(Class<?> cls, String n) {
        String key = cls.getName() + "#" + n;
        if (FIELDS.containsKey(key)) return FIELDS.get(key);
        java.lang.reflect.Field found = null;
        for (Class<?> c = cls; c != null && c != Object.class && found == null; c = c.getSuperclass()) {
            try {
                found = c.getDeclaredField(n);
                found.setAccessible(true);
            } catch (NoSuchFieldException ignored) {
            } catch (RuntimeException e) {
                found = null;
                break;
            }
        }
        FIELDS.put(key, found);
        return found;
    }

    private static Object getF(Object o, String n) {
        java.lang.reflect.Field f = field(o.getClass(), n);
        if (f == null) return null;
        try {
            return f.get(o);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean setF(Object o, String n, Object v) {
        java.lang.reflect.Field f = field(o.getClass(), n);
        if (f == null) return false;
        try {
            f.set(o, v);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Runnable lockPose(Object p) {
        final java.util.List<Object[]> saved = new java.util.ArrayList<>();
        final Object[][] zero = {
            {p, "hurtTime", 0}, {p, "swingTime", 0}, {p, "swinging", false},
            {p, "attackAnim", 0f}, {p, "oAttackAnim", 0f}
        };
        for (Object[] z : zero) {
            Object old = getF(z[0], (String) z[1]);
            if (old != null && setF(z[0], (String) z[1], z[2])) saved.add(new Object[] {z[0], z[1], old});
        }
        Object wa = getF(p, "walkAnimation");
        if (wa != null) {
            for (String n : new String[] {"speedOld", "speed", "position"}) {
                Object old = getF(wa, n);
                if (old != null && setF(wa, n, 0f)) saved.add(new Object[] {wa, n, old});
            }
        }
        // đang cúi (shift) thì đứng thẳng khi vẽ
        for (String n : new String[] {"shiftKeyDown", "crouching"}) {
            Object old = getF(p, n);
            if (old instanceof Boolean && ((Boolean) old) && setF(p, n, false)) saved.add(new Object[] {p, n, old});
        }
        return () -> {
            for (Object[] r : saved) setF(r[0], (String) r[1], r[2]);
        };
    }

    private static java.lang.reflect.Method modelM;
    private static boolean modelTried;

    /** Vẽ mô hình 3D người chơi như màn hình kho đồ. Tìm bằng reflection vì tên hàm đổi giữa các bản 26.x. */
    private static boolean drawModel(Object g, int x1, int y1, int x2, int y2, float scale, float mx, float my, Object entity) {
        if (g == null || x2 <= x1 || y2 <= y1) return false;
        if (!modelTried) {
            modelTried = true;
            java.lang.reflect.Method best = null;
            for (java.lang.reflect.Method m : net.minecraft.client.gui.screens.inventory.InventoryScreen.class.getMethods()) {
                Class<?>[] pt = m.getParameterTypes();
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers()) || !m.getName().contains("ntityInInventory")) continue;
                if (pt.length != 10 || !pt[0].isAssignableFrom(g.getClass()) || !pt[9].isAssignableFrom(entity.getClass())) continue;
                if (best == null || m.getName().contains("FollowsMouse")) best = m;
            }
            modelM = best;
        }
        if (modelM == null) return false;
        try {
            Class<?>[] pt = modelM.getParameterTypes();
            double[] v = {x1, y1, x2, y2, scale, 0.0625, mx, my};
            Object[] a = new Object[10];
            a[0] = g;
            for (int i = 0; i < 8; i++) a[i + 1] = num(pt[i + 1], v[i]);
            a[9] = entity;
            modelM.invoke(null, a);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            modelM = null;
            return false;
        }
    }

    private static Object num(Class<?> t, double v) {
        if (t == int.class) return (int) Math.round(v);
        if (t == float.class) return (float) v;
        return v;
    }

    // ---- trang chủ

    private void drawHome(int mx, int my) {
        float k = AnimationsModule.menu() ? 0.3f : 1f;
        long now = System.currentTimeMillis();
        int yt = listYTarget();
        listYCur = listYCur < 0f ? yt : listYCur + (yt - listYCur) * k;
        if (Math.abs(yt - listYCur) < 0.4f) listYCur = yt;
        listScroll += (listTarget - listScroll) * scrollK;
        if (Math.abs(listTarget - listScroll) < 0.4f) listScroll = listTarget;
        clampList();
        float sk = AnimationsModule.menu() ? 0.22f : 1f;
        float shT = expanded ? 1f : 0f;
        shift += (shT - shift) * sk;
        if (Math.abs(shT - shift) < 0.005f) shift = shT;

        int hc = circleAt(mx, my);
        int hr = hc >= 0 || !expanded ? -1 : rowAt(mx, my);
        int hkey = hc >= 0 ? hc : hr >= 0 ? 1000 + hr : -1;
        if (hkey != lastHoverSfx) {
            lastHoverSfx = hkey;
            if (hkey >= 0 && !closing) play("menu.hover");
        }

        float pa = AnimationsModule.menu()
                ? AnimationsModule.easeOutBack((now - openStart) * AnimationsModule.speed() / 480f) : 1f;

        // thẻ nhân vật + mũi tên sang cột nút tròn
        Draw.push();
        Draw.translate(gdx(), 0f);
        drawCharacterCard(pa, now);
        int midY = (circleY(0) + circleY(circleCount() - 1)) / 2;
        int wax = CPX + CPW + 8 + Math.round((float) Math.sin(now / 380.0) * 2f);
        Draw.arrow(wax, midY + 0.5f, 14f, 13.5f, false, 0xFFE6E8EA);
        Draw.pop();

        drawCircles(hc, k, now);
        drawList(hr, k);
        drawHero(pa, now);
    }

    private void drawCircles(int hc, float k, long now) {
        int n = circleCount();
        int selTab = selCircle();
        for (int i = 0; i < n; i++) {
            boolean act = i == tabCount();
            Object key = act ? (Object) "ACT" : i == 0 ? (Object) "ALL" : (Object) tabCats.get(i - 1);
            float hv = hover(key, i == hc, k);
            float sv = hover("sel" + i, i == selTab, k);
            int cy = circleY(i);
            float pop = 1f;
            if (AnimationsModule.menu()) {
                pop = AnimationsModule.easeOutBack((now - openStart) * AnimationsModule.speed() / 300f - i * 0.12f);
                if (pop <= 0.01f) continue;
            }
            float pp = (now - pressAt) / 260f;
            float sq = 0f;
            float p2 = (now - pressAt) / 560f;
            if (i == pressCircle && p2 >= 0f && p2 < 1f) sq = -0.2f * (float) (Math.exp(-5.0 * p2) * Math.cos(p2 * 20.0));
            float scl = pop * (1f + 0.05f * hv + 0.08f * sv);
            float pulse = 0.5f + 0.5f * (float) Math.sin(now / 280.0);

            Draw.push();
            Draw.translate(gdx(), 0f);
            Draw.translate(CX, cy);
            Draw.scale(scl * (1f - sq), scl * (1f + sq));
            Draw.translate(-CX, -cy);
            if (i == pressCircle && pp >= 0f && pp < 1.7f) {
                float rp = Math.min(1f, pp / 1.7f);
                float rr = CR + 3 + rp * 16f;
                int ra = Math.round((1f - rp) * (1f - rp) * 200f);
                if (ra > 4) Draw.circleRing(CX, cy, rr - 1.4f, rr, (ra << 24) | 0xFFD27A);
            }
            if (sv > 0.02f) {
                int ga = Math.round(sv * (60f + pulse * 40f));
                if (ga > 3) Draw.disc(CX, cy, CR + 11f + pulse * 2f, (ga << 24) | 0xF0A30A, 0x00F0A30A);
            } else if (hv > 0.02f) {
                int ga = Math.round(hv * 70f);
                if (ga > 3) Draw.disc(CX, cy, CR + 9f, (ga << 24) | 0xB8EFFF, 0x00B8EFFF);
            }
            Draw.disc(CX + 0.5f, cy + 3f, CR + 5f, 0x40000000, 0x00000000);
            int rim = Draw.lerp(Draw.lerp(0xCCB8EFFF, 0xFFFFFFFF, hv), 0xFFF0A30A, sv);
            Draw.disc(CX, cy, CR + 3f, Draw.lerp(rim, 0xFFFFFFFF, 0.25f), rim);
            Draw.disc(CX, cy, CR + 1f, 0xFF0B1420);
            int body = Draw.lerp(Draw.lerp(0xFFF2F4F6, 0xFFFFE2A8, hv * 0.6f), SAO_ORANGE, sv);
            Draw.disc(CX, cy, CR - 1f, Draw.lerp(body, 0xFFFFFFFF, 0.38f), Draw.lerp(body, 0xFF000000, 0.16f));
            Draw.circleRing(CX, cy, CR - 4.6f, CR - 3.8f, 0x1E000000);
            Draw.disc(CX, cy - CR * 0.5f, CR * 0.5f, 0x60FFFFFF, 0x00FFFFFF);
            if (sv > 0.5f) {
                float sw = (float) ((now % 1800L) / 1800.0);
                if (sw < 0.45f) {
                    int sx = CX - CR + 2 + Math.round(sw / 0.45f * (2 * CR - 8));
                    Draw.fill(sx, cy - CR + 6, sx + 2, cy + CR - 6, 0x30FFFFFF);
                }
            }
            int ic = Draw.lerp(Draw.lerp(0xFFC4C8CD, 0xFF8A9096, hv), 0xFFFFFFFF, sv);
            SaoIcons.draw(act ? "gear" : SaoIcons.cat(i == 0 ? null : tabCats.get(i - 1)), CX - 8, cy - 8, ic);
            if (sv > 0.3f) {
                double ang = now / 700.0;
                for (int tr = 0; tr < 5; tr++) {
                    double aa = ang - tr * 0.16;
                    float ox = CX + (float) Math.cos(aa) * (CR + 7f);
                    float oy = cy + (float) Math.sin(aa) * (CR + 7f);
                    int oa = Math.round((200f - tr * 38f) * sv);
                    Draw.disc(ox, oy, 1.7f - tr * 0.22f, (oa << 24) | 0xFFD27A);
                }
            }
            if (sv > 0.05f) {
                int ta = Math.round(255f * Math.min(1f, sv));
                float len = 8f * sv;
                float ax = CX + CR + 5f + (float) Math.sin(now / 260.0) * 1.1f * sv;
                Draw.arrow(ax, cy, len, len, false, (ta << 24) | 0xF0A30A);
            }
            Draw.pop();

            float tipA = hv * (1f - Math.min(1f, shift * 2.5f));
            if (tipA > 0.05f) {
                String lb = act ? "Thao tác" : Lang.vi(tabLabel(i));
                int tw = Draw.width(lb);
                int tx = gdx() + CX + CR + 10 + Math.round((1f - tipA) * -8f);
                int al = Math.round(255f * tipA);
                Draw.rrect(tx - 5, cy - 9, tx + tw + 5, cy + 9, 4, (Math.round(0xE6 * tipA) << 24) | 0x0F1A27);
                Draw.ring(tx - 5, cy - 9, tx + tw + 5, cy + 9, (Math.round(0xB0 * tipA) << 24) | 0xB8EFFF);
                Draw.text(lb, tx, cy - 4, (Math.max(al, 8) << 24) | 0xF4F5F7, true);
            }
        }
    }

    private void drawList(int hr, float k) {
        int ly = listY(), lh = listH();
        clip(lpx() - 12, ly, lpw() + 30, lh);
        int sc = Math.round(listScroll);
        int fv = Math.max(0, sc / LSTEP);
        boolean showList = expanded || shift > 0.03f;
        for (int i = 0; showList && i < listCount(); i++) {
            int y = ly + i * LSTEP - sc;
            if (y + LRH < ly || y > ly + lh) continue;
            float sp = 1f;
            if (AnimationsModule.menu()) {
                float t = (System.currentTimeMillis() - animStart) * AnimationsModule.speed() / 320f
                        - Math.min(Math.max(0, i - fv), 10) * 0.08f - 0.15f;
                sp = AnimationsModule.easeOutBack(t);
            }
            if (!expanded) sp = Math.min(sp, shift);
            if (detail) sp = 1f;
            Object hkeyObj = actionsOpen ? (Object) ("act" + i) : (Object) shown.get(i);
            float hv = hover(hkeyObj, i == hr && sp > 0.9f, k);
            if (sp <= 0.02f) continue;
            Draw.push();
            float rcy = y + LRH / 2f;
            Draw.translate(lpx() + (1f - Math.min(1f, sp)) * 46f + hv * 4f, rcy);
            Draw.scale(sp, 0.75f + 0.25f * Math.min(1.1f, sp));
            Draw.translate(-lpx(), -rcy);
            if (actionsOpen) drawActionRow(i, y, hv);
            else drawRow(shown.get(i), y, hv, shown.get(i) == module);
            Draw.pop();
        }
        Draw.endScissor();
    }

    private void drawActionRow(int i, int y, float hv) {
        int x = lpx(), w = lpw(), h = LRH;
        Skin.saoRow(x, y, w, h, hv);
        AnimeFx.shine(x, y, w, h, hv);
        if (i == 3) Draw.fill(x, y, x + 3, y + h, SAO_ORANGE);
        int cx = x + 18, cy = y + h / 2;
        int cb = i == 3 ? 0xFFB9770E : i == 2 ? 0xFFB03A3A : 0xFF39404C;
        Draw.disc(cx, cy, 12.5f, 0xFFFFFFFF);
        Draw.disc(cx, cy, 11.3f, Draw.lerp(cb, 0xFFFFFFFF, 0.22f), Draw.lerp(cb, 0xFF000000, 0.2f));
        Draw.disc(cx, cy - 5.5f, 5.5f, 0x40FFFFFF, 0x00FFFFFF);
        SaoIcons.draw(i == 0 ? "arrows" : i == 1 ? "monitor" : i == 2 ? "chevron" : "check", cx - 8, cy - 8, 0xFFFFFFFF);
        text(ACT_LABEL[i], x + 36, y + (h - 10) / 2f + 1f, 1.2f, 0xFF22262B, false);
    }

    private void drawRow(Module m, int y, float hv, boolean sel) {
        int x = lpx(), w = lpw(), h = LRH;
        boolean on = m.enabled;
        if (sel) {
            Skin.vgrad(x, y, x + w, y + h, 0xF5F2A53A, 0xF5E0881C);
            if (dshift > 0.02f) Draw.arrow(x + w + 2, y + h / 2f, 9f, 9f, false, Draw.mulAlpha(SAO_ORANGE, dshift));
            Draw.fill(x, y, x + w, y + 1, 0xCCFFFFFF);
            Draw.fill(x, y + h - 1, x + w, y + h, 0x55000000);
        } else {
            Skin.saoRow(x, y, w, h, hv);
            AnimeFx.shine(x, y, w, h, hv);
        }
        int cx = x + 19, cy = y + h / 2;
        int idx = Math.max(0, LunarLite.MODULES.indexOf(m));
        int base = on ? ICON_COLORS[idx % ICON_COLORS.length] : 0xFF9AA0A6;
        Skin.saoDisc(cx, cy, 12f, SaoIcons.forModule(m), base);

        float ns = 1.3f - 0.3f * dshift;
        String nm = Draw.fit(Lang.vi(m.name), Math.round((w - 44 - 26 * (1f - dshift)) / ns));
        text(nm, x + 38, y + (h - 10 * ns) / 2f + 1f, ns, sel ? 0xFFFFFFFF : (on ? 0xFF2B3036 : 0xFF6A7178), sel);

        boolean fav = FAVS.contains(m.id);
        if (dshift < 0.3f && (fav || hv > 0.3f)) Icons.draw("heart", x + w - 18, y + (h - 8) / 2, 1, fav ? 0xFFFF5577 : 0x66000000);
    }

    // ---- bảng tuỳ chỉnh

    private void rowLabel(String text, int x, float cy, int maxW, int col) {
        String t = Draw.fit(text, Math.max(8, Math.round(maxW / 1.2f)));
        text(t, x, cy - 4.8f, 1.2f, col, false);
    }

    private int trackW(int w) { return narrow(w) ? w - 20 : Math.max(40, Math.min(104, w - 160)); }
    private int trackX(int px, int w) { return narrow(w) ? px + 10 : px + w - trackW(w) - 6; }

    private void drawDetail(int mx, int my) {
        int px = paneX(), pw = paneW();
        int top = paneTop(), bottom = paneBottom();
        int sc = Math.round(setScroll);
        float k = AnimationsModule.menu() ? 0.3f : 1f;
        clip(px, top, pw, setViewH());
        for (DRow r : rows) {
            int ry = top + r.y - sc;
            if (ry + r.h < top || ry > bottom) continue;
            float st = r.kind == K_HEADER ? 1f : stagger(r.id);
            boolean over = st > 0.7f && detailActive() && Draw.in(mx, my, px, Math.max(ry, top), pw, Math.min(r.h, bottom - ry)) && r.kind != K_HEADER;
            r.hov += ((over ? 1f : 0f) - r.hov) * k;
            Draw.push();
            Draw.translate((1f - st) * 22f, 0f);
            drawDRow(r, px, ry, pw);
            Draw.pop();
        }
        Draw.endScissor();
        if (contentH > setViewH()) scrollbar(top, setViewH(), contentH, setScroll);
    }

    /** Hàng tuỳ chỉnh trượt vào lần lượt khi mở / đổi tính năng (như Btn.stagger của bản 1.8.9). */
    private static float stagger(int id) {
        if (!AnimationsModule.menu()) return 1f;
        float t = (System.currentTimeMillis() - animStart) * AnimationsModule.speed() / 240f - Math.min(id, 10) * 0.06f;
        return AnimationsModule.ease(t);
    }

    private boolean detailActive() { return dshift > 0.9f && confirmMod == null; }

    private void drawDRow(DRow r, int x, int y, int w) {
        int h = r.kind == K_SLIDER && narrow(w) ? BH + 14 : BH;
        int ty = y + (h - 8) / 2;
        int acc = Draw.ACCENT;
        float hov = r.hov;
        switch (r.kind) {
            case K_HEADER: {
                if (r.header.isEmpty()) {
                    Draw.fadeLine(x + 10, y + BH / 2, w - 24, 0xFFFFFF, 0x40);
                    return;
                }
                Skin.saoRow(x, y, w, BH, 0f);
                Skin.diamond(x + 20, ty + 4, 4, Skin.ORANGE);
                Draw.text(r.header.toUpperCase(java.util.Locale.ROOT), x + 30, ty, Skin.INK_SOFT, false);
                return;
            }
            case K_ENABLE:
            case K_BOOL: {
                boolean val = r.kind == K_ENABLE ? r.m.enabled : ((BoolSetting) r.s).value;
                float swT = val ? 1f : 0f;
                r.sw = r.sw < 0f ? swT : r.sw + (swT - r.sw) * (AnimationsModule.menu() ? 0.3f : 1f);
                if (Math.abs(r.sw - swT) < 0.01f) r.sw = swT;
                Skin.saoRow(x, y, w, h, hov);
                Skin.saoDisc(x + 19, y + h / 2f, 12f, "check", r.sw > 0.5f ? 0xFF3FB86F : 0xFF9AA0A6);
                rowLabel(r.kind == K_ENABLE ? "Bật tính năng" : Lang.vi(r.s.label), x + 38, y + h / 2f, w - 74, Skin.INK);
                Skin.saoSwitch(x + w - 32, y + (h - 12) / 2, r.sw);
                return;
            }
            case K_MODE: {
                Skin.saoRow(x, y, w, h, hov);
                String val = Lang.vi(((ModeSetting) r.s).get());
                int bw2 = Math.min(w / 2, Draw.width(val) + 18);
                int bx = x + w - bw2 - 4, by = y + 3;
                Skin.saoField(bx, by, bw2, h - 6, hov);
                String vt = Draw.fit(val, bw2 - 8);
                Draw.text(vt, bx + (bw2 - Draw.width(vt)) / 2f, ty, Skin.INK, false);
                Skin.saoDisc(x + 19, y + h / 2f, 12f, "chevron", 0xFF3F8EF0);
                rowLabel(Lang.vi(r.s.label), x + 38, y + h / 2f, w - bw2 - 52, Skin.INK);
                return;
            }
            case K_COLOR: {
                Skin.saoRow(x, y, w, h, hov);
                int sx = x + w - 46, sy = y + 3;
                Draw.rrect(sx, sy, sx + 40, sy + h - 6, 3, Draw.lerp(0xFF8E949B, 0xFFFFFFFF, hov));
                Draw.rrect(sx + 1, sy + 1, sx + 39, sy + h - 7, 2, 0xFF000000 | ((ColorSetting) r.s).value);
                Skin.saoDisc(x + 19, y + h / 2f, 12f, "sparkle", 0xFFE0609E);
                rowLabel(Lang.vi(r.s.label), x + 38, y + h / 2f, w - 98, Skin.INK);
                return;
            }
            case K_SLIDER: {
                SliderSetting ss = (SliderSetting) r.s;
                Skin.saoRow(x, y, w, h, hov);
                int tx = trackX(x, w);
                boolean nr = narrow(w);
                int vx0 = nr ? x + w - 44 : tx - 44;
                int vy0 = nr ? y + 6 : y + 3, vh0 = nr ? 14 : h - 6;
                int tyy = nr ? y + 9 : ty;
                float cyL = nr ? y + 13f : y + h / 2f;
                Skin.saoField(vx0, vy0, 38, vh0, 0f);
                String vt = Draw.fit(ss.display(), 34);
                Draw.text(vt, vx0 + (38 - Draw.width(vt)) / 2f, tyy, Skin.INK, false);
                Skin.saoDisc(x + 19, cyL, 12f, "sliders", 0xFFF0A30A);
                rowLabel(Lang.vi(r.s.label), x + 38, cyL, Math.max(10, vx0 - x - 44), Skin.INK);
                int ly = nr ? y + h - 9 : y + h / 2;
                int tw2 = trackW(w);
                int hx = tx + Math.round((float) ss.ratio() * (tw2 - 8));
                Draw.rrect(tx, ly - 2, tx + tw2, ly + 2, 2, 0xFF8E949B);
                Draw.fill(tx + 1, ly - 2, tx + tw2 - 1, ly - 1, 0x55000000);
                Draw.rrect(tx, ly - 2, hx + 4, ly + 2, 2, acc);
                Draw.disc(hx + 4f, ly + 0.8f, 6.6f, 0x30000000);
                Draw.disc(hx + 4f, ly, 6f, Draw.lerp(0xFF9AA0A6, acc, hov));
                Draw.disc(hx + 4f, ly, 4.7f, 0xFFFFFFFF, 0xFFDDE0E3);
                return;
            }
            case K_ACTION: {
                Skin.saoButton(x, y, w, h, r.a.label, hov, Skin.NORMAL);
                if (r.a.icon != null && !r.a.icon.isEmpty()) Draw.item(r.a.icon, x + 6, y + (h - 16) / 2);
                return;
            }
            default: { // K_RESET
                int bw = Math.min(200, w);
                Skin.saoButton(x + (w - bw) / 2, y, bw, h, "Đặt lại tuỳ chỉnh", hov, Skin.DANGER);
            }
        }
    }

    // ---- thanh cuộn

    private int barX() { return paneX() + paneW() + 3; }
    private int thumbH(int viewH, int totalH) { return Math.max(20, viewH * viewH / Math.max(1, totalH)); }

    private int thumbY(int y, int viewH, int totalH, float scroll) {
        return y + Math.round((viewH - thumbH(viewH, totalH)) * (scroll / Math.max(1, totalH - viewH)));
    }

    private static void pill(int x1, int y1, int x2, int y2, int c) {
        if (y2 - y1 < 3 || x2 - x1 < 3) {
            Draw.fill(x1, y1, x2, y2, c);
            return;
        }
        Draw.fill(x1 + 1, y1, x2 - 1, y1 + 1, c);
        Draw.fill(x1, y1 + 1, x2, y2 - 1, c);
        Draw.fill(x1 + 1, y2 - 1, x2 - 1, y2, c);
    }

    private void scrollbar(int y, int viewH, int totalH, float scroll) {
        int x = barX();
        int bh = thumbH(viewH, totalH);
        int by = thumbY(y, viewH, totalH, scroll);
        boolean hover = curMx >= x - 3 && curMx < x + BAR_W + 2 && curMy >= y && curMy < y + viewH;
        float target = dragBar || hover || Math.abs(setTarget - setScroll) > 0.8f ? 1f : 0f;
        barAnim += (target - barAnim) * (AnimationsModule.menu() ? 0.3f : 1f);
        if (Math.abs(target - barAnim) < 0.02f) barAnim = target;
        int tw = 3 + Math.round(2 * barAnim);
        int tx1 = x + 1 + Math.round((5 - tw) / 2f);
        pill(x + 1, y + 2, x + 6, y + viewH - 2, 0x33FFFFFF);
        int thumb = dragBar ? Skin.ORANGE : Draw.lerp(0xFF8E949B, Skin.ORANGE, barAnim);
        pill(tx1, by + 2, tx1 + tw, Math.min(y + viewH - 2, by + bh - 2), thumb);
    }

    private boolean startBarDrag(int mx, int my) {
        int viewH = setViewH();
        if (contentH <= viewH) return false;
        int x = barX();
        if (mx < x - 3 || mx >= x + BAR_W + 2 || my < paneTop() || my >= paneTop() + viewH) return false;
        int by = thumbY(paneTop(), viewH, contentH, setScroll), bh = thumbH(viewH, contentH);
        dragBar = true;
        dragOff = (my >= by && my < by + bh) ? my - by : bh / 2;
        dragTo(my);
        return true;
    }

    private void dragTo(int my) {
        int viewH = setViewH();
        int range = Math.max(1, viewH - thumbH(viewH, contentH));
        float v = (my - dragOff - paneTop()) / (float) range * (contentH - viewH);
        setTarget = setScroll = v;
        clampScroll();
    }

    // ------------------------------------------------------------------ chuột

    private boolean inPane(int mx, int my) {
        return mx >= paneX() && mx < paneX() + paneW() && my >= paneTop() && my < paneBottom();
    }

    private void sliderFromMouse(DRow r, int mx) {
        int w = paneW();
        float ratio = (mx - trackX(paneX(), w) - 4) / (float) (trackW(w) - 8);
        ((SliderSetting) r.s).setRatio(Math.max(0f, Math.min(1f, ratio)));
    }

    private void handle(DRow r, int btn, int mx, int my) {
        boolean right = btn == 1;
        switch (r.kind) {
            case K_ENABLE:
                askToggle(r.m);
                break;
            case K_BOOL: {
                long before = visSig(r.m);
                ((BoolSetting) r.s).toggle();
                play(((BoolSetting) r.s).value ? "menu.on" : "menu.off");
                if (visSig(r.m) != before) rebuild();
                LunarLite.saveConfig();
                break;
            }
            case K_MODE:
                play("menu.click");
                if (right) ((ModeSetting) r.s).prev();
                else ((ModeSetting) r.s).next();
                LunarLite.saveConfig();
                break;
            case K_COLOR: {
                play("menu.click");
                ColorSetting col = (ColorSetting) r.s;
                int n = ColorSetting.PRESETS.length, i = presetIndex(col.value);
                int next = !right ? (i + 1) % n : (i < 0 ? n - 1 : (i + n - 1) % n);
                col.value = ColorSetting.PRESETS[next];
                LunarLite.saveConfig();
                break;
            }
            case K_SLIDER: {
                if (right) break;
                boolean inTrack = narrow(paneW()) ? my >= paneTop() + r.y - Math.round(setScroll) + r.h - 18 - (ROW - BH)
                        : mx >= trackX(paneX(), paneW()) - 6;
                if (inTrack) {
                    sliderFromMouse(r, mx);
                    dragSlider = r;
                }
                break;
            }
            case K_ACTION:
                play("menu.click");
                r.a.run.run();
                rebuild();
                break;
            case K_RESET:
                play("menu.click");
                r.m.resetSettings();
                if (r.m instanceof HudModule hm) hm.resetPosition();
                rebuild();
                LunarLite.saveConfig();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        int btn = event.button();
        if (btn != 0 && btn != 1) return super.mouseClicked(event, doubleClick);
        int mx = vx(event.x()), my = vy(event.y());
        AnimeFx.burst((float) event.x(), (float) event.y(), Draw.ACCENT & 0xFFFFFF);
        if (confirmMod != null) {
            if (btn == 0) confirmClick(mx, my);
            return true;
        }

        if (!detail) {
            int ci = circleAt(mx, my);
            if (ci >= 0) {
                pressCircle = ci;
                pressAt = System.currentTimeMillis();
                boolean isAct = ci == tabCount();
                if (expanded && ci == selCircle()) {
                    play("menu.click");
                    expanded = false;
                } else {
                    play("menu.select");
                    if (isAct) {
                        actionsOpen = true;
                        listScroll = listTarget = 0f;
                        animStart = System.currentTimeMillis();
                    } else {
                        boolean was = actionsOpen;
                        actionsOpen = false;
                        if (ci != tabIndex()) setTab(ci);
                        else if (was) animStart = System.currentTimeMillis();
                    }
                    if (!expanded) {
                        expanded = true;
                        animStart = System.currentTimeMillis();
                    }
                }
                return true;
            }
            int ri = expanded ? rowAt(mx, my) : -1;
            if (ri >= 0 && actionsOpen) {
                play("menu.click");
                if (btn != 0) return true;
                if (ri == 0) Compat.setScreen(new ProfileScreen(this));
                else if (ri == 1) Compat.setScreen(new HudEditorScreen(this));
                else if (ri == 2) askQuit();
                else requestClose();
                return true;
            }
            if (ri >= 0) {
                Module hit = shown.get(ri);
                module = hit;
                lastModule = hit;
                if (btn == 0 && mx >= lpx() + lpw() - 22) {
                    play("menu.click");
                    if (!FAVS.remove(hit.id)) FAVS.add(hit.id);
                    buildShown();
                    return true;
                }
                if (btn == 1) askToggle(hit);
                else openModule(hit);
                return true;
            }
            return true;
        }

        int ci = btn == 0 ? circleAt(mx, my) : -1;
        if (ci >= 0) {
            play("menu.select");
            goBack();
            if (ci == tabCount()) {
                actionsOpen = true;
                listScroll = listTarget = 0f;
            } else {
                actionsOpen = false;
                if (ci != tabIndex()) setTab(ci);
            }
            expanded = true;
            animStart = System.currentTimeMillis();
            return true;
        }
        int dri = actionsOpen ? -1 : rowAt(mx, my);
        if (dri >= 0) {
            Module hit = shown.get(dri);
            if (btn == 1) askToggle(hit);
            else if (hit != module) openModule(hit);
            else play("menu.click");
            return true;
        }
        if (btn == 0 && startBarDrag(mx, my)) return true;
        if (!inPane(mx, my) || !detailActive()) return true;
        int top = paneTop(), sc = Math.round(setScroll);
        for (DRow r : rows) {
            if (r.kind == K_HEADER) continue;
            int ry = top + r.y - sc;
            int rh = r.kind == K_SLIDER && narrow(paneW()) ? BH + 14 : BH;
            if (Draw.in(mx, my, paneX(), ry, paneW(), rh)) {
                if (btn == 1 && r.kind == K_SLIDER) return true;
                handle(r, btn, mx, my);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragBar && event.button() == 0) {
            dragTo(vy(event.y()));
            return true;
        }
        if (dragSlider != null && event.button() == 0) {
            sliderFromMouse(dragSlider, vx(event.x()));
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            boolean had = dragBar || dragSlider != null;
            dragBar = false;
            dragSlider = null;
            if (had) {
                LunarLite.saveConfig();
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (confirmMod != null) return true;
        if (detail) {
            setTarget += (scrollY > 0 ? -1 : 1) * 2f * ROW;
            clampScroll();
        } else if (expanded) {
            listTarget += (scrollY > 0 ? -2 : 2) * LSTEP;
            clampList();
        }
        return true;
    }

    // ------------------------------------------------------------------ bàn phím

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (confirmMod != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeConfirm(false);
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) closeConfirm(true);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (detail) goBack();
            else requestClose();
            return true;
        }
        if (LunarLite.keyMenu != null && LunarLite.keyMenu.matches(event)) {
            requestClose();
            return true;
        }
        if (detail) {
            if (key == GLFW.GLFW_KEY_LEFT) moveModule(-1);
            else if (key == GLFW.GLFW_KEY_RIGHT) moveModule(1);
            else if (key == GLFW.GLFW_KEY_UP) { setTarget -= ROW; clampScroll(); }
            else if (key == GLFW.GLFW_KEY_DOWN) { setTarget += ROW; clampScroll(); }
            return true;
        }
        int digit = key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 ? key - GLFW.GLFW_KEY_0
                : key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9 ? key - GLFW.GLFW_KEY_KP_0 : -1;
        if (digit >= 0) {
            int slot = digit == 0 ? 9 : digit - 1;
            int idx = Math.round(listScroll / LSTEP) + slot;
            if (expanded && !actionsOpen && idx < shown.size()) openModule(shown.get(idx));
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_TAB) {
            if (!expanded) {
                expanded = true;
                animStart = System.currentTimeMillis();
            }
            actionsOpen = false;
            cycleTab(key == GLFW.GLFW_KEY_UP ? -1 : 1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        lastModule = module;
        LunarLite.saveConfig();
        super.onClose();
    }
}
