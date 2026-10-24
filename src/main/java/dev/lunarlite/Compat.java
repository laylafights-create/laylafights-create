package dev.lunarlite;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Gom mọi chỗ API của Minecraft hay đổi giữa các bản 26.x vào một file.
 * Ở 26.2 các hàm màn hình chuyển từ {@code Minecraft} sang {@code Gui} (Fabric: "Screen methods moved from
 * Minecraft to the Gui class"), nên dùng reflection thử cả hai chỗ để cùng một jar chạy được ở 26.1 / 26.2.
 */
public final class Compat {
    private Compat() {}

    private static Method find(Class<?> c, String name, int params) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        }
        return null;
    }

    private static Object call(Object target, String name, Object... args) {
        if (target == null) return null;
        Method m = find(target.getClass(), name, args.length);
        if (m == null) throw new NoSuchMethodError(target.getClass().getName() + "#" + name);
        try {
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 26.2 đổi tên {@code setScreen} thành {@code setScreenAndShow} và dời việc quản lý màn hình sang {@code Gui}
     * (Gui cũ đổi tên thành Hud). Thử lần lượt các tên + các chủ sở hữu để cùng một jar chạy được 26.1 / 26.2.
     */
    public static void setScreen(Screen s) {
        Minecraft mc = Minecraft.getInstance();
        Object[] owners = {mc.gui, mc};
        String[] names = {"setScreenAndShow", "setScreen"};
        for (Object owner : owners) {
            for (String n : names) {
                if (owner == null) continue;
                Method m = find(owner.getClass(), n, 1);
                if (m == null) continue;
                try {
                    m.invoke(owner, s);
                    return;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new NoSuchMethodError("Minecraft/Gui#setScreenAndShow|setScreen");
    }

    /** Màn hình đang mở: ở 26.2 là field {@code Gui.screen} (trước đó {@code Minecraft.screen}). */
    public static Screen screen() {
        Minecraft mc = Minecraft.getInstance();
        Object[] owners = {mc.gui, mc};
        for (Object owner : owners) {
            if (owner == null) continue;
            for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("screen");
                    if (!Screen.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    return (Screen) f.get(owner);
                } catch (NoSuchFieldException ignored) {
                } catch (ReflectiveOperationException | RuntimeException e) {
                    break;
                }
            }
            for (String n : new String[] {"screen", "getScreen"}) {
                Method m = find(owner.getClass(), n, 0);
                if (m == null || !Screen.class.isAssignableFrom(m.getReturnType())) continue;
                try {
                    return (Screen) m.invoke(owner);
                } catch (ReflectiveOperationException | RuntimeException ignored) { }
            }
        }
        return null;
    }

    /** Số khung hình hiện tại (API {@code Minecraft#getFps} ở 26.x; thử cả field {@code fps} nếu đổi tên). */
    public static int fps() {
        Minecraft mc = Minecraft.getInstance();
        try {
            return ((Number) call(mc, "getFps")).intValue();
        } catch (RuntimeException | NoSuchMethodError e) {
            try {
                java.lang.reflect.Field f = Minecraft.class.getDeclaredField("fps");
                f.setAccessible(true);
                return ((Number) f.get(null)).intValue();
            } catch (ReflectiveOperationException | RuntimeException e2) {
                return 0;
            }
        }
    }

    /** Thoát về màn hình chính. Chữ ký {@code Minecraft#disconnect} đổi giữa các bản 26.x nên thử nhiều cách. */
    public static boolean disconnect() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level != null) mc.level.disconnect(net.minecraft.network.chat.Component.empty());
        } catch (RuntimeException | LinkageError ignored) { }
        for (Method m : mc.getClass().getMethods()) {
            if (!m.getName().equals("disconnect")) continue;
            Class<?>[] pt = m.getParameterTypes();
            Object[] a = new Object[pt.length];
            boolean ok = true;
            for (int i = 0; i < pt.length; i++) {
                if (pt[i] == boolean.class) a[i] = false;
                else if (Screen.class.isAssignableFrom(pt[i])) a[i] = new net.minecraft.client.gui.screens.TitleScreen();
                else { ok = false; break; }
            }
            if (!ok) continue;
            try { m.invoke(mc, a); return true; } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return false;
    }

    /** Gửi dòng vào khung chat cục bộ (thay {@code displayClientMessage(Component, false)} đã bị bỏ ở 26.2). */
    public static void chat(net.minecraft.world.entity.player.Player p, net.minecraft.network.chat.Component c) {
        if (p != null) p.sendSystemMessage(c);
    }

    /** Màu RGB của team (26.2: {@code PlayerTeam#getColor} trả {@code Optional<TeamColor>}); -1 nếu không có. */
    public static int teamRgb(net.minecraft.world.scores.PlayerTeam team) {
        if (team == null) return -1;
        try {
            Object v = team.getColor();
            if (v instanceof java.util.Optional<?> o) v = o.orElse(null);
            return rgbOf(v);
        } catch (RuntimeException | LinkageError e) {
            return -1;
        }
    }

    private static int rgbOf(Object v) {
        if (v == null) return -1;
        if (v instanceof Number n) return n.intValue() & 0xFFFFFF;
        if (v instanceof net.minecraft.network.chat.TextColor tc) return tc.getValue() & 0xFFFFFF;
        for (String n : new String[] {"getColor", "color", "getRgb", "rgb", "getValue", "value", "getTextColor", "textColor"}) {
            Method m = find(v.getClass(), n, 0);
            if (m == null) continue;
            try {
                Object r = m.invoke(v);
                if (r instanceof java.util.Optional<?> o) r = o.orElse(null);
                if (r != null && r != v) {
                    int c = rgbOf(r);
                    if (c >= 0) return c;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return -1;
    }

    /** F1 (ẩn HUD): {@code Options#hideGui} là field ở bản cũ, có thể thành hàm/OptionInstance ở 26.2. */
    public static boolean hideGui() {
        Object o = Minecraft.getInstance().options;
        try {
            java.lang.reflect.Field f = o.getClass().getField("hideGui");
            return f.getBoolean(o);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
        Method m = find(o.getClass(), "hideGui", 0);
        if (m != null) {
            try {
                Object r = m.invoke(o);
                if (r instanceof Boolean b) return b;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return false;
    }
}
