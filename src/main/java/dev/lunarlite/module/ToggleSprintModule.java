package dev.lunarlite.module;

import dev.lunarlite.Compat;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class ToggleSprintModule extends TextHudModule {
    private final BoolSetting showHud = add(new BoolSetting("show_hud", "Show Status Text", true));
    private final ModeSetting label = add(new ModeSetting("label", "Label", 0,
            "[Sprinting (Toggled)]", "[Sprinting]", "Sprint"));

    public ToggleSprintModule() {
        super("togglesprint", "Toggle Sprint", "Tự động chạy nhanh khi giữ phím tiến.", Category.UTILITY, false, 4, 224);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!enabled || p == null || Compat.screen() != null) return;
        if (mc.options.keyUp.isDown() && !p.isShiftKeyDown() && !p.isUsingItem()
                && !p.horizontalCollision && p.getFoodData().getFoodLevel() > 6) {
            mc.options.keySprint.setDown(true);
        }
    }

    @Override
    protected String[] getLines(boolean preview) {
        if (!showHud.value) return new String[0];
        Minecraft mc = Minecraft.getInstance();
        if (preview || (mc.player != null && mc.player.isSprinting())) {
            return new String[] { label.get() };
        }
        return new String[0];
    }
}
