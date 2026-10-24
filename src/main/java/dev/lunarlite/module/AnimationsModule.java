package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;

/** Công tắc chung cho animation của menu và HUD (không có HUD riêng). */
public class AnimationsModule extends Module {
    private final BoolSetting menu = add(new BoolSetting("menu", "Menu Animations", true));
    private final BoolSetting hud = add(new BoolSetting("hud", "HUD Animations", true));
    private final SliderSetting speed = add(new SliderSetting("speed", "Animation Speed", 100, 50, 200, 10, "%"));

    public AnimationsModule() {
        super("animations", "Animations",
                "Animation mở/đóng menu, hover nút, chuyển trang và hiện HUD.", Category.VISUAL, true);
    }

    public static boolean menu() {
        AnimationsModule a = LunarLite.ANIM;
        return a == null || (a.enabled && a.menu.value);
    }

    public static boolean hud() {
        AnimationsModule a = LunarLite.ANIM;
        return a == null || (a.enabled && a.hud.value);
    }

    /** Ease-out-back: vượt nhẹ rồi về đúng vị trí, nhìn "đàn hồi" hơn. */
    public static float easeOutBack(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float c1 = 1.1f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Hệ số tốc độ: 1.0 = bình thường. */
    public static float speed() {
        AnimationsModule a = LunarLite.ANIM;
        return a == null ? 1f : a.speed.getFloat() / 100f;
    }

    /** Ease-out cubic, t tự kẹp trong 0..1. */
    public static float ease(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = 1f - t;
        return 1f - u * u * u;
    }
}
