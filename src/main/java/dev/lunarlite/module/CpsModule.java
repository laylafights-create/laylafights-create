package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;

/**
 * Đếm click chuột. 26.x không còn sự kiện chuột kiểu Forge nên ta đo cạnh lên của nút chuột
 * mỗi frame (gọi {@link #poll()} từ vòng vẽ HUD và từ tick).
 */
public class CpsModule extends TextHudModule {
    private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
    private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();
    private static boolean wasLeft, wasRight;

    private final BoolSetting showRight = add(new BoolSetting("show_right", "Show Right Click", true));

    public CpsModule() {
        super("cps", "CPS", "Số lần click chuột mỗi giây (trái | phải).", Category.PVP, true, 4, 24);
    }

    /** Ghi nhận click mới bằng cách so trạng thái nút chuột với lần gọi trước. */
    public static void poll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler == null) return;
        boolean l = mc.mouseHandler.isLeftPressed(), r = mc.mouseHandler.isRightPressed();
        long now = System.currentTimeMillis();
        if (l && !wasLeft) LEFT.add(now);
        if (r && !wasRight) RIGHT.add(now);
        wasLeft = l;
        wasRight = r;
    }

    private static int count(ArrayDeque<Long> list) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!list.isEmpty() && list.getFirst() < cutoff) list.removeFirst();
        return list.size();
    }

    public static int left() { return count(LEFT); }
    public static int right() { return count(RIGHT); }

    @Override
    protected String[] getLines(boolean preview) {
        return new String[] { "CPS: " + left() + (showRight.value ? " | " + right() : "") };
    }
}
