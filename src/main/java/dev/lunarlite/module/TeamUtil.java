package dev.lunarlite.module;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

/**
 * Phân biệt đồng đội / kẻ địch (dùng được trên Hypixel): 1) màu giáp da nhuộm, 2) màu prefix ngắn của team,
 * 3) cùng scoreboard team. Kết quả cache ~0.4s mỗi người.
 */
public final class TeamUtil {
    private static final String COLORS = "0123456789abcdef";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final int[] LEGACY_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private TeamUtil() {}

    private static final class Verdict { long until; boolean mate; }

    private static final Map<Integer, Verdict> CACHE = new HashMap<>();
    private static Level lastWorld;

    public static boolean isTeammate(Player me, Player p) {
        if (me == null || p == null) return false;
        if (me == p) return true;
        Level w = Minecraft.getInstance().level;
        if (w != lastWorld || CACHE.size() > 256) { CACHE.clear(); lastWorld = w; }
        long now = System.currentTimeMillis();
        Verdict v = CACHE.computeIfAbsent(p.getId(), k -> new Verdict());
        if (now >= v.until) {
            v.mate = compute(me, p);
            v.until = now + 400L;
        }
        return v.mate;
    }

    private static boolean compute(Player me, Player p) {
        int a = dyedArmor(me), b = dyedArmor(p);
        if (a != -1 && b != -1) return a == b;
        int ca = markerColor(me), cb = markerColor(p);
        if (ca != -1 && cb != -1) return ca == cb;
        Team ta = me.getTeam(), tb = p.getTeam();
        if (ta == null || tb == null || !ta.isAlliedTo(tb)) return false;
        if (ta instanceof PlayerTeam pt && pt.getPlayers().size() > 4) return false;
        if (!HypixelUtil.isHypixel() && ta.isAllowFriendlyFire()) return false;
        return true;
    }

    private static int dyedArmor(Player p) {
        for (EquipmentSlot slot : ARMOR) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            var dyed = s.get(DataComponents.DYED_COLOR);
            if (dyed != null) return dyed.rgb() & 0xFFFFFF;
        }
        return -1;
    }

    private static int markerColor(Player p) {
        if (!(p.getTeam() instanceof PlayerTeam t)) return -1;
        String prefix = legacy(t.getPlayerPrefix());
        int visible = prefix.replaceAll("§.", "").trim().length();
        if (visible < 1 || visible > 2) return -1;
        int color = -1;
        for (int i = 0; i + 1 < prefix.length(); i++) {
            if (prefix.charAt(i) == '§') {
                int idx = COLORS.indexOf(Character.toLowerCase(prefix.charAt(i + 1)));
                if (idx >= 0) color = idx;
            }
        }
        return color;
    }

    /** Chuyển Component thành chuỗi có mã § (chỉ màu) để so sánh. */
    private static String legacy(net.minecraft.network.chat.Component c) {
        StringBuilder sb = new StringBuilder();
        c.visit((style, text) -> {
            var col = style.getColor();
            if (col != null) {
                int rgb = col.getValue() & 0xFFFFFF;
                for (int i = 0; i < LEGACY_RGB.length; i++) {
                    if (LEGACY_RGB[i] == rgb) {
                        sb.append('§').append(COLORS.charAt(i));
                        break;
                    }
                }
            }
            sb.append(text);
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return sb.toString();
    }
}
