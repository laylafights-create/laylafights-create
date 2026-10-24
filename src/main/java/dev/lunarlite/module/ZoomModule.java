package dev.lunarlite.module;

import dev.lunarlite.LunarLite;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import net.minecraft.client.Minecraft;

/**
 * Zoom mượt: giữ (hoặc bấm bật/tắt) phím Zoom để thu hẹp FOV, nhả để về lại.
 * Không dùng mixin nên FOV được đổi trực tiếp qua option {@code fov} (game giới hạn 30..110), và độ nhạy chuột
 * được giảm theo hệ số khi đang zoom. Giá trị gốc luôn được trả lại khi nhả phím / thoát game.
 */
public class ZoomModule extends Module {
    private final ModeSetting activation = add(new ModeSetting("activation", "Activation", 0, "Hold", "Toggle"));
    private final SliderSetting zoomFov = add(new SliderSetting("zoom_fov", "Zoom FOV", 30, 30, 70, 1, ""));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth Transition", true));
    private final SliderSetting speed = add(new SliderSetting("speed", "Transition Speed", 12, 2, 30, 1, ""), "Options", smooth);
    private final BoolSetting reduceSens = add(new BoolSetting("reduce_sens", "Reduce Mouse Sensitivity", true));
    private final SliderSetting sens = add(new SliderSetting("sens", "Zoom Sensitivity", 40, 10, 100, 5, "%"), "Options", reduceSens);

    private boolean toggled, active;
    private boolean saved;
    private int baseFov;
    private final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll To Adjust", true));
    private final SliderSetting scrollStep = add(new SliderSetting("scroll_step", "Scroll Step", 3, 1, 10, 1, " FOV"), "Options", scroll);
    private final BoolSetting scrollInvert = add(new BoolSetting("scroll_invert", "Invert Scroll Direction", false), "Options", scroll);
    private final BoolSetting scrollAccel = add(new BoolSetting("scroll_accel", "Scroll Acceleration", true), "Options", scroll);
    /** FOV đích khi đang zoom (lăn chuột chỉnh được; game giới hạn 30..110). */
    private float target = -1f;
    private int scrollCombo;
    private long lastScrollNs;
    private double baseSens;
    private float cur;
    private long lastNs;

    public ZoomModule() {
        super("zoom", "Zoom", "Thu phóng mượt: giữ phím Zoom để nhìn xa.", Category.VISUAL, true);
    }

    private boolean wantZoom() {
        if (!enabled || LunarLite.keyZoom == null) return false;
        if (activation.is("Toggle")) {
            while (LunarLite.keyZoom.consumeClick()) toggled = !toggled;
            return toggled;
        }
        toggled = false;
        return LunarLite.keyZoom.isDown();
    }

    /** Gọi mỗi frame (từ vòng vẽ HUD) để chuyển cảnh mượt. */
    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            restore();
            return;
        }
        boolean want = wantZoom();
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;

        if (want && !saved) {
            baseFov = mc.options.fov().get();
            baseSens = mc.options.sensitivity().get();
            saved = true;
            cur = 0f;
            target = zoomFov.getFloat();
        }
        if (!saved) return;

        float target = want ? 1f : 0f;
        if (smooth.value) cur += (target - cur) * Math.min(1f, dt * speed.getFloat());
        else cur = target;
        if (!want && cur < 0.01f) {
            restore();
            return;
        }
        active = true;
        float tf = target < 0f ? zoomFov.getFloat() : target;
        int fov = Math.round(baseFov + (tf - baseFov) * cur);
        mc.options.fov().set(fov);
        if (reduceSens.value) {
            double f = 1.0 + (sens.get() / 100.0 - 1.0) * cur;
            mc.options.sensitivity().set(baseSens * f);
        }
    }

    /** Trả FOV và độ nhạy về giá trị người chơi đã đặt. */
    public void restore() {
        if (!saved) return;
        Minecraft mc = Minecraft.getInstance();
        mc.options.fov().set(baseFov);
        mc.options.sensitivity().set(baseSens);
        saved = false;
        active = false;
        cur = 0f;
    }

    public boolean isActive() { return active; }

    /** Trả true nếu zoom dùng sự kiện lăn chuột này (khi đó hotbar không bị đổi ô). */
    public boolean handleScroll(double dy) {
        if (!scroll.value || !active || !enabled || dy == 0 || !wantsZoomNoConsume()) return false;
        float mult = 1f;
        long now = System.nanoTime();
        if (scrollAccel.value) {
            float gap = (now - lastScrollNs) / 1e9f;
            scrollCombo = gap < 0.18f ? Math.min(scrollCombo + 1, 10) : 0;
            mult = 1f + 2f * (scrollCombo / 10f);
        } else {
            scrollCombo = 0;
        }
        lastScrollNs = now;
        int dir = (dy > 0 ? 1 : -1) * (scrollInvert.value ? -1 : 1); // dir > 0 = zoom vào
        float cur0 = target < 0f ? zoomFov.getFloat() : target;
        float st = scrollStep.getFloat() * mult;
        if (dir > 0) st = Math.min(st, cur0 * 0.5f);
        target = Math.max(30f, Math.min(110f, cur0 - dir * st));
        return true;
    }

    /** Kiểm tra đang giữ / bật zoom mà không tiêu thụ click của chế độ Toggle. */
    private boolean wantsZoomNoConsume() {
        if (LunarLite.keyZoom == null) return false;
        return activation.is("Toggle") ? toggled : LunarLite.keyZoom.isDown();
    }

    @Override
    public void onToggle() {
        if (!enabled) {
            toggled = false;
            restore();
        }
    }
}
