package dev.lunarlite.gui;

import dev.lunarlite.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Màn hình chết kiểu SAO thay cho DeathScreen của game. */
public class SaoDeathScreen extends Screen {
    public SaoDeathScreen() { super(Component.literal("YOU ARE DEAD")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        Draw.begin(graphics);
        SaoDeath.render(width, height, mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Minecraft mc = Minecraft.getInstance();
        int mx = (int) event.x(), my = (int) event.y();
        if (SaoDeath.overRespawn(width, height, mx, my) && mc.player != null) {
            mc.player.respawn();
            Compat.setScreen(null);
            return true;
        }
        if (SaoDeath.overTitle(width, height, mx, my)) {
            if (!Compat.disconnect()) { SaoDeath.enabled = false; SaoDeath.update(false); }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
