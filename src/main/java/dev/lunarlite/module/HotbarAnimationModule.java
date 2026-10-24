package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import dev.lunarlite.gui.Skin;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Animation cho hotbar: ô chọn trượt (lò xo) + giãn, phóng to item đang cầm, item bên cạnh phồng nhẹ, lắc khi đổi ô, pop khi nhặt.
 * Thay hotbar vanilla qua HudElementRegistry.replaceElement (xem LunarLite). Trong kho đồ (mọi AbstractContainerScreen) có khung chọn trượt, phóng to item đang trỏ, pop, vệt sáng — vẽ qua ContainerScreenMixin.
 */
public class HotbarAnimationModule extends Module {
    private static final Identifier BAR = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier SEL = Identifier.withDefaultNamespace("hud/hotbar_selection");

    private static final float SPRING_K = 480f;
    private static final float SPRING_C = 2f * 0.45f * (float) Math.sqrt(SPRING_K);

    private final BoolSetting slide = add(new BoolSetting("slide", "Ô chọn trượt mượt", true));
    private final SliderSetting slideSpeed = add(new SliderSetting("slide_speed", "Tốc độ trượt", 18, 6, 100, 2, ""));
    private final BoolSetting stretch = add(new BoolSetting("stretch", "Ô chọn giãn khi trượt", true));
    private final BoolSetting glow = add(new BoolSetting("glow", "Ô chọn phát sáng nhẹ", true));
    private final BoolSetting zoom = add(new BoolSetting("zoom", "Phóng to item đang cầm", true));
    private final SliderSetting zoomSize = add(new SliderSetting("zoom_size", "Độ phóng to", 40, 10, 80, 5, "%"));
    private final BoolSetting wave = add(new BoolSetting("wave", "Item bên cạnh phồng nhẹ", true));
    private final BoolSetting wobble = add(new BoolSetting("wobble", "Lắc nhẹ khi đổi item", true));
    private final BoolSetting breathe = add(new BoolSetting("breathe", "Item đang cầm thở nhẹ", true));
    private final BoolSetting pop = add(new BoolSetting("pop", "Pop khi nhặt/đổi item", true));

    // Kho đồ
    private final BoolSetting invAnim = add(new BoolSetting("inv_anim", "Animation trong kho đồ", true));
    private final BoolSetting invSlide = add(new BoolSetting("inv_slide", "Khung chọn trượt mượt (kho)", true));
    private final BoolSetting invZoom = add(new BoolSetting("inv_zoom", "Phóng to item đang trỏ (kho)", true));
    private final SliderSetting invZoomSize = add(new SliderSetting("inv_zoom_size", "Độ phóng to (kho)", 25, 10, 60, 5, "%"));
    private final BoolSetting invLift = add(new BoolSetting("inv_lift", "Item nổi lên + bóng đổ (kho)", true));
    private final BoolSetting invShine = add(new BoolSetting("inv_shine", "Vệt sáng quét + gợn sóng khi trỏ vào (kho)", true));
    private final BoolSetting invPop = add(new BoolSetting("inv_pop", "Pop khi item đổi chỗ (kho)", true));

    private final float[] scl = new float[9], vel = new float[9], wobT = new float[9], wobDir = new float[9];
    private final Object[] lastItem = new Object[9];
    private final int[] lastSize = new int[9];

    private float selPos = -1f, selVel = 0f;
    private int lastCur = -1;
    private float time = 0f;
    private boolean initialized = false;
    private long lastNanos = 0L;

    public HotbarAnimationModule() {
        super("hotbar_animation", "Hotbar Animation",
                "Animation cho thanh hotbar: trượt, phóng to item đang cầm, lắc, pop.", Category.PVP, true);
        for (int i = 0; i < 9; i++) { scl[i] = 1f; wobT[i] = -1f; wobDir[i] = 1f; }
        Arrays.fill(iScl, 1f);
        Arrays.fill(iWobT, -1f);
        Arrays.fill(iWobDir, 1f);
    }

    /** Có nên thay hotbar vanilla không (người xem spectator dùng hotbar vanilla). */
    public boolean shouldRender() {
        Minecraft mc = Minecraft.getInstance();
        return enabled && mc.getCameraEntity() instanceof Player p && !p.isSpectator();
    }

    public void render(int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Player p = (Player) mc.getCameraEntity();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        time += dt;

        int cur = p.getInventory().getSelectedSlot();
        updateSelection(cur, dt);
        updateSlots(p, cur, dt);

        int cx = sw / 2;
        Draw.g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR, cx - 91, sh - 22, 182, 22);

        float sx = stretch.value ? 1f + Math.min(0.45f, Math.abs(selVel) * 0.045f) : 1f;
        Draw.push();
        Draw.translate(cx - 92f + selPos * 20f + 12f, sh - 23f);
        Draw.scale(sx, 1f);
        Draw.translate(-12f, 0f);
        Draw.g.blitSprite(RenderPipelines.GUI_TEXTURED, SEL, 0, 0, 24, 23);
        if (glow.value) {
            int a = (int) (24f + 14f * (float) Math.sin(time * 3.2f));
            Draw.fill(3, 3, 21, 21, (a << 24) | 0x00FFFFFF);
        }
        Draw.pop();

        int iy = sh - 16 - 3;
        for (int i = 0; i < 9; i++) {
            if (i != cur) drawItem(p, i, cx - 90 + i * 20 + 2, iy, false);
        }
        drawItem(p, cur, cx - 90 + cur * 20 + 2, iy, true);

        // tay phụ (vanilla vẽ trong hotbar nên phải tự vẽ lại)
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty()) {
            Draw.item(off, cx - 91 - 26, iy);
            Draw.itemDecor(off, cx - 91 - 26, iy);
        }
    }

    private void updateSelection(int cur, float dt) {
        if (selPos < 0f || lastCur < 0) { selPos = cur; lastCur = cur; }
        if (cur != lastCur) {
            int d = cur - lastCur;
            if (d > 4) d -= 9; else if (d < -4) d += 9;
            wobDir[cur] = d >= 0 ? 1f : -1f;
            if (wobble.value) wobT[cur] = 0f;
            if (zoom.value) {
                vel[cur] += 2.5f;
                vel[lastCur] -= 1.2f;
            }
            lastCur = cur;
        }
        if (slide.value) {
            float k = slideSpeed.getInt() * 4f;
            float c = 2f * (float) Math.sqrt(k) * 0.8f;
            selVel += (k * (cur - selPos) - c * selVel) * dt;
            selPos += selVel * dt;
            if (Math.abs(cur - selPos) < 0.001f && Math.abs(selVel) < 0.01f) { selPos = cur; selVel = 0f; }
        } else {
            selPos = cur;
            selVel = 0f;
        }
    }

    private void updateSlots(Player p, int cur, float dt) {
        float zs = zoom.value ? zoomSize.getInt() / 100f : 0f;
        float h = dt * 0.5f;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getItem(i);
            Object id = s.isEmpty() ? null : s.getItem();
            int size = s.isEmpty() ? 0 : s.getCount();
            if (initialized && pop.value && !s.isEmpty() && (id != lastItem[i] || size > lastSize[i])) {
                vel[i] += id != lastItem[i] ? 5f : 3.5f;
            }
            lastItem[i] = id;
            lastSize[i] = size;

            float t = 1f;
            if (i == cur) t += zs;
            else if (wave.value && (i == cur - 1 || i == cur + 1)) t += zs * 0.12f;

            float v = vel[i], x = scl[i];
            for (int n = 0; n < 2; n++) {
                v += (SPRING_K * (t - x) - SPRING_C * v) * h;
                x += v * h;
            }
            if (x < 0.5f) x = 0.5f; else if (x > 2.5f) x = 2.5f;
            if (Math.abs(t - x) < 0.0005f && Math.abs(v) < 0.005f) { x = t; v = 0f; }
            scl[i] = x;
            vel[i] = v;

            if (wobT[i] >= 0f) {
                wobT[i] += dt;
                if (wobT[i] > 0.7f) wobT[i] = -1f;
            }
        }
        initialized = true;
    }

    private void drawItem(Player p, int i, int x, int y, boolean selected) {
        ItemStack s = p.getInventory().getItem(i);
        if (s.isEmpty()) return;
        float sc = scl[i];
        if (breathe.value && selected) sc *= 1f + 0.025f * (float) Math.sin(time * 3f);
        float ang = 0f;
        if (wobT[i] >= 0f) {
            float t = wobT[i];
            ang = 14f * wobDir[i] * (float) Math.exp(-7f * t) * (float) Math.sin(24f * t);
        }
        boolean xf = Math.abs(sc - 1f) > 0.002f || Math.abs(ang) > 0.1f;
        if (xf) {
            Draw.push();
            Draw.translate(x + 8f, y + 8f);
            if (ang != 0f) Draw.rotate((float) Math.toRadians(ang));
            Draw.scale(sc, sc);
            Draw.translate(-(x + 8f), -(y + 8f));
        }
        Draw.item(s, x, y);
        Draw.itemDecor(s, x, y);
        if (xf) Draw.pop();
    }

    // ====================================================================================
    //  KHO ĐỒ (AbstractContainerScreen) — gọi từ ContainerScreenMixin sau khi vanilla vẽ xong
    // ====================================================================================

    private static final int MAX_SLOTS = 256;

    private final float[] iScl = new float[MAX_SLOTS], iVel = new float[MAX_SLOTS];
    private final float[] iWobT = new float[MAX_SLOTS], iWobDir = new float[MAX_SLOTS];
    private final Object[] iLastItem = new Object[MAX_SLOTS];
    private final int[] iLastSize = new int[MAX_SLOTS];

    private Screen iGui;
    private long iLastNanos = 0L;
    private float iTime = 0f, iOpenTime = 0f;

    private float hx, hy, hvx, hvy, hAlpha;
    private int hSlot = -1;
    private float hShine = 1f;

    private Field fLeft, fTop, fHover;
    private boolean reflectReady = false, reflectFailed = false;

    public boolean shouldRenderContainer(Screen screen) {
        return enabled && invAnim.value && !reflectFailed && screen instanceof AbstractContainerScreen<?>;
    }

    private static Field findField(Class<?> c, String... names) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (String n : names) {
                try {
                    Field f = k.getDeclaredField(n);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) { }
            }
        }
        throw new NoSuchFieldException(names[0]);
    }

    private void initReflection() {
        if (reflectReady) return;
        try {
            fLeft = findField(AbstractContainerScreen.class, "leftPos");
            fTop = findField(AbstractContainerScreen.class, "topPos");
            fHover = findField(AbstractContainerScreen.class, "hoveredSlot");
            reflectReady = true;
        } catch (Throwable t) {
            reflectFailed = true;
        }
    }

    private void resetInventoryState(Screen g) {
        iGui = g;
        Arrays.fill(iScl, 1f);
        Arrays.fill(iVel, 0f);
        Arrays.fill(iWobT, -1f);
        Arrays.fill(iWobDir, 1f);
        Arrays.fill(iLastItem, null);
        Arrays.fill(iLastSize, 0);
        iOpenTime = 0f;
        hSlot = -1;
        hShine = 1f;
        hAlpha = 0f;
        hvx = hvy = 0f;
        iLastNanos = 0L;
    }

    public void renderContainer(AbstractContainerScreen<?> g, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        initReflection();
        if (reflectFailed) return;
        Draw.begin(graphics);
        if (g != iGui) resetInventoryState(g);

        long now = System.nanoTime();
        float dt = iLastNanos == 0L ? 0.016f : Math.min(0.05f, (now - iLastNanos) / 1_000_000_000f);
        iLastNanos = now;
        iTime += dt;
        iOpenTime += dt;

        int gl, gt;
        Slot hov;
        try {
            gl = fLeft.getInt(g);
            gt = fTop.getInt(g);
            hov = (Slot) fHover.get(g);
        } catch (Throwable t) {
            reflectFailed = true;
            return;
        }

        List<Slot> slots = g.getMenu().slots;
        int n = Math.min(slots.size(), MAX_SLOTS);
        int hovIdx = -1;
        if (hov != null) {
            int ix = slots.indexOf(hov);
            if (ix >= 0 && ix < n) hovIdx = ix;
        }

        updateHighlight(slots, hovIdx, gl, gt, dt);
        updateInvSlots(slots, n, hovIdx, dt);

        drawHighlight();
        for (int i = 0; i < n; i++) {
            float sc = iScl[i];
            float ang = wobbleAngle(i);
            if (sc > 1.002f || Math.abs(ang) > 0.1f) drawInvItem(slots.get(i), gl, gt, sc, ang);
        }
    }

    private void updateHighlight(List<Slot> slots, int hovIdx, int gl, int gt, float dt) {
        if (hovIdx != hSlot) {
            if (hovIdx >= 0) {
                hShine = 0f;
                Slot s = slots.get(hovIdx);
                float tx = gl + s.x, ty = gt + s.y;
                float dir = tx >= hx ? 1f : -1f;
                if (hAlpha < 0.05f || !invSlide.value) {
                    hx = tx;
                    hy = ty;
                    hvx = hvy = 0f;
                }
                if (invZoom.value) {
                    iVel[hovIdx] += 2.5f;
                    if (hSlot >= 0) iVel[hSlot] -= 1.2f;
                    if (s.hasItem()) {
                        iWobDir[hovIdx] = dir;
                        iWobT[hovIdx] = 0f;
                    }
                }
            }
            hSlot = hovIdx;
        }

        if (hSlot >= 0) {
            Slot s = slots.get(hSlot);
            float tx = gl + s.x, ty = gt + s.y;
            if (invSlide.value) {
                float k = slideSpeed.getInt() * 4f;
                float c = 2f * (float) Math.sqrt(k) * 0.8f;
                hvx += (k * (tx - hx) - c * hvx) * dt;
                hvy += (k * (ty - hy) - c * hvy) * dt;
                hx += hvx * dt;
                hy += hvy * dt;
                if (Math.abs(tx - hx) < 0.01f && Math.abs(hvx) < 0.1f) { hx = tx; hvx = 0f; }
                if (Math.abs(ty - hy) < 0.01f && Math.abs(hvy) < 0.1f) { hy = ty; hvy = 0f; }
            } else {
                hx = tx;
                hy = ty;
                hvx = hvy = 0f;
            }
        }

        hShine = Math.min(1f, hShine + dt * 3.2f);
        float target = hSlot >= 0 ? 1f : 0f;
        hAlpha += (target - hAlpha) * Math.min(1f, dt * 18f);
        if (Math.abs(target - hAlpha) < 0.003f) hAlpha = target;
    }

    private static int lerpRgb(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 255) * (1f - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1f - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1f - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private void drawHighlight() {
        if (hAlpha < 0.01f) return;
        float ex = invSlide.value && stretch.value ? Math.min(6f, Math.abs(hvx) * 0.012f) : 0f;
        float ey = invSlide.value && stretch.value ? Math.min(6f, Math.abs(hvy) * 0.012f) : 0f;
        int l = Math.round(hx - 1f - ex * 0.5f), r = Math.round(hx + 17f + ex * 0.5f);
        int t = Math.round(hy - 1f - ey * 0.5f), b = Math.round(hy + 17f + ey * 0.5f);

        float m = 0.5f + 0.5f * (float) Math.sin(iTime * 2.4f);
        int rgb = lerpRgb(0x8FDCFF, 0xC3A6FF, m);
        int bright = lerpRgb(rgb, 0xFFFFFF, 0.6f);

        if (invLift.value) {
            int sa = (int) (60f * hAlpha);
            Draw.fill(l + 3, b, r - 3, b + 1, (sa << 24));
            Draw.fill(l + 5, b + 1, r - 5, b + 2, ((sa / 2) << 24));
        }

        if (glow.value) {
            for (int k = 1; k <= 3; k++) {
                int a = (int) ((30f / k) * hAlpha);
                Draw.outline(l - k, t - k, r + k, b + k, (a << 24) | rgb);
            }
        }

        if (invShine.value && hShine < 1f) {
            int e = Math.round(hShine * 6f);
            int a = (int) ((1f - hShine) * 140f * hAlpha);
            Draw.outline(l - e, t - e, r + e, b + e, (a << 24) | bright);
        }

        if (glow.value) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(iTime * 3.2f);
            int a1 = (int) ((16f + 8f * pulse) * hAlpha);
            int a2 = (int) ((52f + 22f * pulse) * hAlpha);
            Skin.vgrad(l + 1, t + 1, r - 1, b - 1, (a1 << 24) | rgb, (a2 << 24) | rgb);
        }

        int ab = (int) (225f * hAlpha);
        int col = (ab << 24) | bright;
        Draw.fill(l + 1, t, r - 1, t + 1, col);
        Draw.fill(l + 1, b - 1, r - 1, b, col);
        Draw.fill(l, t + 1, l + 1, b - 1, col);
        Draw.fill(r - 1, t + 1, r, b - 1, col);
        int ca = (int) (110f * hAlpha);
        int cc = (ca << 24) | 0x00FFFFFF;
        Draw.fill(l, t, l + 1, t + 1, cc);
        Draw.fill(r - 1, t, r, t + 1, cc);
        Draw.fill(l, b - 1, l + 1, b, cc);
        Draw.fill(r - 1, b - 1, r, b, cc);

        if (invShine.value && hShine < 1f) {
            int ix = Math.round(hx), iy = Math.round(hy);
            int sa = (int) (100f * (float) Math.sin(Math.PI * hShine) * hAlpha);
            if (sa > 0) {
                float c = -16f + 46f * hShine;
                for (int row = 0; row < 16; row++) {
                    float x = c + (16 - row) * 0.8f;
                    int x0 = Math.max(0, (int) (x - 2f)), x1 = Math.min(16, (int) (x + 2f));
                    if (x1 > x0) Draw.fill(ix + x0, iy + row, ix + x1, iy + row + 1, (sa << 24) | 0x00FFFFFF);
                }
            }
        }
    }

    private void updateInvSlots(List<Slot> slots, int n, int hovIdx, float dt) {
        float zs = invZoom.value ? invZoomSize.getInt() / 100f : 0f;
        float h = dt * 0.5f;
        boolean canPop = invPop.value && iOpenTime > 0.4f;
        for (int i = 0; i < n; i++) {
            ItemStack s = slots.get(i).getItem();
            Object id = s.isEmpty() ? null : s.getItem();
            int size = s.isEmpty() ? 0 : s.getCount();

            if (canPop && !s.isEmpty() && (id != iLastItem[i] || size > iLastSize[i])) {
                iVel[i] += id != iLastItem[i] ? 5f : 3.5f;
            }
            iLastItem[i] = id;
            iLastSize[i] = size;

            float t = i == hovIdx ? 1f + zs : 1f;
            float v = iVel[i], x = iScl[i];
            for (int k = 0; k < 2; k++) {
                v += (SPRING_K * (t - x) - SPRING_C * v) * h;
                x += v * h;
            }
            if (x < 0.5f) x = 0.5f; else if (x > 2.5f) x = 2.5f;
            if (Math.abs(t - x) < 0.0005f && Math.abs(v) < 0.005f) { x = t; v = 0f; }
            iScl[i] = x;
            iVel[i] = v;

            if (iWobT[i] >= 0f) {
                iWobT[i] += dt;
                if (iWobT[i] > 0.7f) iWobT[i] = -1f;
            }
        }
    }

    private float wobbleAngle(int i) {
        if (iWobT[i] < 0f) return 0f;
        float t = iWobT[i];
        return 14f * iWobDir[i] * (float) Math.exp(-7f * t) * (float) Math.sin(24f * t);
    }

    private void drawInvItem(Slot slot, int gl, int gt, float sc, float ang) {
        ItemStack s = slot.getItem();
        if (s.isEmpty()) return;
        int x = gl + slot.x, y = gt + slot.y;
        float cx = x + 8f, cy = y + 8f;
        float lift = invLift.value ? Math.max(-1.5f, Math.min(3.5f, (sc - 1f) * 6f)) : 0f;

        Draw.push();
        Draw.translate(cx, cy - lift);
        if (ang != 0f) Draw.rotate((float) Math.toRadians(ang));
        Draw.scale(sc, sc);
        Draw.translate(-cx, -cy);
        Draw.item(s, x, y);
        Draw.pop();
        // số lượng / thanh độ bền vẽ không scale để nằm đúng chỗ
        Draw.itemDecor(s, x, y);
    }
}
