package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** Combo card: số lớn đổi màu theo bậc (trắng -> vàng -> đỏ), nhãn "COMBO" và thanh thời gian còn lại. */
public class ComboModule extends HudModule {
    private static int combo;
    private static long lastHit;
    private int prevHurt;

    private final SliderSetting timeout = add(new SliderSetting("timeout", "Reset After", 3, 1, 8, 1, "s"));

    private static final String LABEL = "COMBO";
    private static final float NUM_SCALE = 2f;
    private static final int PAD_X = 7, PAD_Y = 5, GAP = 6, BAR_H = 2;
    private static final int NUM_H = 16; // 8px chữ * NUM_SCALE
    private static final int PREVIEW_COMBO = 7;

    public ComboModule() {
        super("combo", "Combo", "Số đòn đánh liên tiếp mà không bị đánh lại.", Category.PVP, false, 4, 64);
    }

    /** Gọi khi mình đánh trúng một sinh vật. */
    public static void onAttack() {
        combo++;
        lastHit = System.currentTimeMillis();
    }

    @Override
    public void onTick() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (p.hurtTime > prevHurt) combo = 0; // mình vừa bị đánh -> reset combo
        prevHurt = p.hurtTime;
        if (combo > 0 && System.currentTimeMillis() - lastHit > timeout.getInt() * 1000L) combo = 0;
    }

    private static int tierColor(int c) {
        if (c <= 10) return Draw.lerp(0xFFFFFFFF, 0xFFFFD25C, Math.max(0, c - 1) / 9f);
        return Draw.lerp(0xFFFFD25C, 0xFFFF5C5C, Math.min(1f, (c - 10) / 15f));
    }

    private int shown(boolean preview) {
        return combo <= 0 && preview ? PREVIEW_COMBO : combo;
    }

    private int numBoxW(int c) {
        return Math.round(Math.max(Draw.width("x" + c), Draw.width("x00")) * NUM_SCALE);
    }

    @Override
    public int getWidth(boolean preview) {
        int c = shown(preview);
        if (c <= 0) return 0;
        return PAD_X * 2 + numBoxW(c) + GAP + Draw.width(LABEL);
    }

    @Override
    public int getHeight(boolean preview) {
        return shown(preview) <= 0 ? 0 : PAD_Y * 2 + NUM_H + 3 + BAR_H;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        int c = shown(preview);
        if (c <= 0) return;
        long now = System.currentTimeMillis();
        int w = getWidth(preview), h = getHeight(preview);

        float b = (preview || !dev.lunarlite.module.AnimationsModule.hud()) ? 0f
                : Math.max(0f, 1f - (now - lastHit) * AnimationsModule.speed() / 200f);
        if (b > 0f) {
            float cx = x + w / 2f, cy = y + h / 2f, k = 1f + 0.22f * b;
            Draw.push();
            Draw.translate(cx, cy);
            Draw.scale(k, k);
            Draw.translate(-cx, -cy);
        }

        drawBox(x, y, w, h);

        String num = "x" + c;
        int nbw = numBoxW(c);
        int numColor = tierColor(c);
        float nx = x + PAD_X + (nbw - Draw.width(num) * NUM_SCALE) / 2f;
        Draw.push();
        Draw.translate(nx, y + PAD_Y);
        Draw.scale(NUM_SCALE, NUM_SCALE);
        Draw.text(num, 0, 0, numColor, shadow.value);
        Draw.pop();

        Draw.text(LABEL, x + PAD_X + nbw + GAP, y + PAD_Y + (NUM_H - 8) / 2f, 0xFFB8B8B8, false);

        float remain = preview ? 0.7f
                : Math.max(0f, 1f - (now - lastHit) / (timeout.getInt() * 1000f));
        int bx = x + PAD_X, bw = w - PAD_X * 2, by = y + h - PAD_Y - BAR_H;
        Draw.rrect(bx, by, bx + bw, by + BAR_H, 1, 0x55000000);
        int fw = Math.round(bw * remain);
        if (fw >= 2) Draw.rrect(bx, by, bx + fw, by + BAR_H, 1, numColor);
        else if (fw > 0) Draw.fill(bx, by, bx + fw, by + BAR_H, numColor);

        if (b > 0f) Draw.pop();
    }
}
