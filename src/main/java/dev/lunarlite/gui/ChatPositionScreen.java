package dev.lunarlite.gui;

import dev.lunarlite.Compat;
import dev.lunarlite.LunarLite;
import dev.lunarlite.module.ChatBarModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Kéo thả khung tin nhắn tới vị trí tuỳ ý; lăn chuột trên khung đổi độ rộng. Esc / Xong để lưu. */
public class ChatPositionScreen extends Screen {
    private final Screen parent;
    private boolean dragging;
    private int grabX, grabY, startWX, startWY;
    private final int[] bDefault = new int[4], bDone = new int[4];

    public ChatPositionScreen(Screen parent) {
        super(Component.literal("Vị trí chat"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mx, int my, float delta) {
        Draw.begin(graphics);
        Draw.fill(0, 0, width, height, 0x66000000);
        ChatView.preview = true;
        try {
            ChatView.render(width, height);
        } finally {
            ChatView.preview = false;
        }
        int[] r = ChatView.windowRect(width, height);
        boolean hov = dragging || Draw.in(mx, my, r[0], r[1], r[2] - r[0], r[3] - r[1]);
        int c = hov ? 0xFFFFD27A : Draw.withAlpha(Draw.ACCENT & 0xFFFFFF, 0xAA);
        Draw.outline(r[0] - 1, r[1] - 1, r[2] + 1, r[3] + 1, c);
        Draw.text("Khung tin nhắn  " + ChatBarModule.CHAT_WIDTH.getInt() + "px", r[0] + 4, Math.max(2, r[1] - 11), 0xFFFFFFFF, true);

        int cx = width / 2, by = height / 2 + 20;
        button(bDefault, cx - 102, by, 100, 20, "Mặc định", mx, my);
        button(bDone, cx + 2, by, 100, 20, "Xong", mx, my);
        Draw.centered("Kéo khung để di chuyển  •  Lăn chuột trên khung: đổi độ rộng  •  Esc: xong",
                cx, height / 2 - 4, 0xFFE0E0E0, true);
    }

    private static void button(int[] b, int x, int y, int w, int h, String label, int mx, int my) {
        b[0] = x; b[1] = y; b[2] = w; b[3] = h;
        boolean over = Draw.in(mx, my, x, y, w, h);
        Skin.saoRow(x, y, w, h, over ? 1f : 0f);
        Draw.fill(x, y, x + 3, y + h, over ? Skin.ORANGE : 0xFFB9770E);
        Draw.text(label, x + (w - Draw.width(label)) / 2f, y + (h - 8) / 2f, 0xFF22262B, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();
        if (event.button() == 0) {
            if (Draw.in(mx, my, bDefault[0], bDefault[1], bDefault[2], bDefault[3])) {
                ChatBarModule.windowX = ChatBarModule.windowY = 0;
                return true;
            }
            if (Draw.in(mx, my, bDone[0], bDone[1], bDone[2], bDone[3])) { onClose(); return true; }
            int[] r = ChatView.windowRect(width, height);
            if (Draw.in(mx, my, r[0], r[1], r[2] - r[0], r[3] - r[1])) {
                dragging = true;
                grabX = mx;
                grabY = my;
                startWX = ChatBarModule.windowX;
                startWY = ChatBarModule.windowY;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        ChatBarModule.windowX = Math.max(-4000, Math.min(4000, startWX + (int) event.x() - grabX));
        ChatBarModule.windowY = Math.max(-4000, Math.min(4000, startWY + (int) event.y() - grabY));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            LunarLite.saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        int[] r = ChatView.windowRect(width, height);
        if (Draw.in((int) x, (int) y, r[0], r[1], r[2] - r[0], r[3] - r[1])) {
            ChatBarModule.CHAT_WIDTH.set(ChatBarModule.CHAT_WIDTH.get() + (sy > 0 ? 10 : -10));
            LunarLite.saveConfig();
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        LunarLite.saveConfig();
        Compat.setScreen(parent);
    }
}
