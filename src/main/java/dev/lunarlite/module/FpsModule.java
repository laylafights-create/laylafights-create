package dev.lunarlite.module;

import dev.lunarlite.Compat;
import dev.lunarlite.setting.ModeSetting;

public class FpsModule extends TextHudModule {
    private final ModeSetting style = add(new ModeSetting("style", "Style", 2, "FPS: 144", "[144 FPS]", "144 FPS", "144"));

    public FpsModule() {
        super("fps", "FPS", "Hiển thị số khung hình mỗi giây.", Category.HUD, true, 4, 4);
    }

    @Override
    protected String[] getLines(boolean preview) {
        int f = Compat.fps();
        String s;
        switch (style.index) {
            case 1: s = "[" + f + " FPS]"; break;
            case 2: s = f + " FPS"; break;
            case 3: s = String.valueOf(f); break;
            default: s = "FPS: " + f;
        }
        return new String[] { s };
    }
}
