package dev.lunarlite.module;

/**
 * Kho đồ / rương / bàn chế tạo... không có nền tối phủ toàn màn hình. Việc bỏ nền nằm ở
 * {@code mixin.ScreenBackgroundMixin}; module này chỉ giữ công tắc bật / tắt.
 */
public class CleanGuisModule extends Module {
    public CleanGuisModule() {
        super("clean_guis", "Clean GUIs", "Kho đồ, rương, bàn chế tạo... không còn nền tối phủ toàn màn hình.",
                Category.VISUAL, true);
    }
}
