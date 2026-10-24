package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

public class PingModule extends TextHudModule {
    public PingModule() {
        super("ping", "Ping", "Độ trễ tới server (ms).", Category.HUD, false, 4, 164);
    }

    @Override
    protected String[] getLines(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        int ping = 0;
        ClientPacketListener conn = mc.getConnection();
        if (mc.player != null && conn != null) {
            PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
            if (info != null) ping = info.getLatency();
        }
        return new String[] { "§a" + ping + "§fms" };
    }
}
