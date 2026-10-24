package dev.lunarlite.module;

/** Ẩn scoreboard (sidebar) của server khi bật. Dữ liệu không đổi nên module khác (Kill Board) vẫn đọc được. */
public class ScoreboardModule extends HudModule {
    public static ScoreboardModule INSTANCE;

    public ScoreboardModule() {
        super("scoreboard", "Ẩn Scoreboard", "Ẩn scoreboard (bảng bên phải) của server.", Category.HUD, false, 520, 80);
        INSTANCE = this;
    }

    public static boolean hidden() { return INSTANCE != null && INSTANCE.enabled; }

    @Override public int getWidth(boolean preview) { return 0; }
    @Override public int getHeight(boolean preview) { return 0; }
    @Override protected void draw(int x, int y, boolean preview) { }
}
