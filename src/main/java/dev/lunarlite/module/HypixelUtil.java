package dev.lunarlite.module;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;

/** Nhận biết Hypixel và lọc "người chơi thật" (UUID v4 = người thật, v2 = NPC). Có cache vài trăm ms. */
public final class HypixelUtil {
    private static long hostStamp, lobbyStamp;
    private static boolean host, lobby;

    private HypixelUtil() {}

    public static boolean isHypixel() {
        long now = System.currentTimeMillis();
        if (now - hostStamp > 1000L) {
            hostStamp = now;
            ServerData sd = Minecraft.getInstance().getCurrentServer();
            host = sd != null && sd.ip != null && sd.ip.toLowerCase().contains("hypixel");
        }
        return host;
    }

    /** Đang ở sảnh Hypixel (sidebar có tiêu đề "HYPIXEL"). */
    public static boolean inLobby() {
        if (!isHypixel()) return false;
        long now = System.currentTimeMillis();
        if (now - lobbyStamp > 500L) {
            lobbyStamp = now;
            lobby = false;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Objective obj = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
                if (obj != null) {
                    String title = obj.getDisplayName().getString().replaceAll("§.", "").trim();
                    lobby = title.equalsIgnoreCase("HYPIXEL");
                }
            }
        }
        return lobby;
    }

    private static boolean validName(String n) {
        if (n == null || n.length() < 3 || n.length() > 16) return false;
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(c == '_' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return true;
    }

    public static boolean isRealPlayer(Player p) {
        var conn = Minecraft.getInstance().getConnection();
        boolean inTab = conn != null && conn.getPlayerInfo(p.getUUID()) != null;
        int ver = p.getUUID().version();
        String name = p.getGameProfile().name();
        if (!isHypixel()) return inTab || (ver != 2 && validName(name));
        return ver == 4 && validName(name);
    }
}
