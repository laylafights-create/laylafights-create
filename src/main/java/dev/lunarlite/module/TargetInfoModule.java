package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.ModeSetting;
import dev.lunarlite.setting.SliderSetting;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Vector3f;

/** Thẻ mục tiêu (avatar | tên + khoảng cách / % máu / thanh máu) cho mọi sinh vật: người chơi và mob. */
public class TargetInfoModule extends HudModule {
    private static LivingEntity lastHit;
    private static long lastHitAt;
    private final BoolSetting showAvatar = add(new BoolSetting("show_avatar", "Hiện avatar", true), "Thông tin mục tiêu");
    private final BoolSetting showHealth = add(new BoolSetting("show_health", "Hiện máu", true), "Thông tin mục tiêu");
    private final BoolSetting showHeartText = add(new BoolSetting("health_text", "Hiện % máu", true), "Thông tin mục tiêu", showHealth);
    private final BoolSetting showDistance = add(new BoolSetting("show_distance", "Hiện khoảng cách", false), "Thông tin mục tiêu");
    private final BoolSetting showMaxHealth = add(new BoolSetting("show_max_health", "Hiện số máu", false), "Thông tin mục tiêu", showHealth);
    private final ModeSetting nameMode = add(new ModeSetting("name_mode", "Kiểu tên", 0, "Tên hiển thị", "Tên tài khoản", "Rút gọn", "Ẩn"), "Thông tin mục tiêu");
    private final ModeSetting healthMode = add(new ModeSetting("health_mode", "Màu thanh máu", 2, "Chuyển màu", "Xanh lá", "Đỏ", "Tùy chỉnh"), "Thông tin mục tiêu", showHealth);
    private final SliderSetting range = add(new SliderSetting("range", "Tầm xa tối đa", 8, 1, 64, 1, " block"), "Thông tin mục tiêu");
    private final SliderSetting barHeight = add(new SliderSetting("bar_height", "Độ cao thanh máu", 4, 2, 10, 1, " px"), "Thông tin mục tiêu", showHealth);
    private final SliderSetting cardWidth = add(new SliderSetting("card_width", "Độ rộng thẻ", 130, 100, 240, 2, " px"), "Thông tin mục tiêu");
    private final ColorSetting healthColor = add(new ColorSetting("health_color", "Màu máu tùy chỉnh", 0x55FF55), "Thông tin mục tiêu", showHealth);
    private final SliderSetting keep = add(new SliderSetting("keep", "Giữ sau khi đánh", 3, 0, 10, 1, "s"), "Thông tin mục tiêu");
    private final ColorSetting nameColor = add(new ColorSetting("name_color", "Màu tên", 0xFFFFFF), "Thông tin mục tiêu");

    private static final int AVATAR = 30;
    private static final int PAD = 6;
    private static final int ROW = 12;      // name row
    private static final int TEXT_ROW = 11; // hp text row
    private static final int GAP = 2;       // gap above the bar

    public TargetInfoModule() {
        super("targetinfo", "Thông tin mục tiêu", "Hiện tên, avatar và máu của mục tiêu (người chơi / mob) dưới tâm ngắm.", Category.PVP, true, 4, 270);
    }

    public static void onAttack(Entity e) {
        if (e instanceof LivingEntity le) { lastHit = le; lastHitAt = System.currentTimeMillis(); }
    }

    private LivingEntity target() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        LivingEntity cand = null;
        if (mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive()) cand = le;
        else if (lastHit != null && lastHit.isAlive() && keep.getInt() > 0
                && System.currentTimeMillis() - lastHitAt < keep.getInt() * 1000L) cand = lastHit;
        if (cand == null) return null;
        return mc.player.distanceTo(cand) <= range.getFloat() ? cand : null;
    }

    private String targetName(LivingEntity e, boolean preview) {
        if (preview || e == null) return "Skeleton";
        if (nameMode.is("Ẩn")) return "";
        String n = nameMode.is("Tên tài khoản") && e instanceof Player p ? p.getGameProfile().name() : e.getDisplayName().getString();
        n = Draw.strip(n);
        if (nameMode.is("Rút gọn") && n.length() > 12) n = n.substring(0, 12) + "...";
        return n;
    }

    @Override public int getWidth(boolean preview) { return cardWidth.getInt(); }

    /** Height of the text/bar column only (no padding). */
    private int contentHeight() {
        int content = 0;
        if (!nameMode.is("Ẩn") || showDistance.value) content += ROW;
        if (showHealth.value) {
            if (showHeartText.value) content += TEXT_ROW;
            if (content > 0) content += GAP;
            content += barHeight.getInt();
        }
        return content;
    }

    @Override public int getHeight(boolean preview) {
        int h = contentHeight() + PAD * 2;
        return showAvatar.value ? Math.max(h, AVATAR + 8) : Math.max(h, 20);
    }

    /** Rounded horizontal bar segment; falls back to a plain rect when too thin to round. */
    private static void seg(int x1, int y1, int x2, int y2, int color) {
        if (x2 <= x1 || y2 <= y1) return;
        int r = Math.min((y2 - y1) / 2, (x2 - x1) / 2);
        if (r >= 1) Draw.rrect(x1, y1, x2, y2, r, color);
        else Draw.fill(x1, y1, x2, y2, color);
    }

    /** Health bar: dark track, lagging "damage" trail, shaded main fill with a bright end cap, quarter ticks. */
    private static void healthBar(int x, int y, int w, int h, float fill, float trail, int color) {
        seg(x - 1, y - 1, x + w + 1, y + h + 1, 0x40000000);     // soft outer frame
        seg(x, y, x + w, y + h, 0x90000000);                       // track
        int tx = x + Math.round(w * trail);
        int fx = x + Math.round(w * fill);
        if (tx > fx) seg(x, y, tx, y + h, 0x66FFFFFF);
        seg(x, y, fx, y + h, color);
        if (h >= 4 && fx - x > 3) {
            Draw.fill(x + 1, y + 1, fx - 1, y + 2, 0x48FFFFFF);               // top shine
            Draw.fill(x + 1, y + h - 2, fx - 1, y + h - 1, 0x30000000);       // bottom shade
            Draw.fill(fx - 1, y + 1, fx, y + h - 1, 0x60FFFFFF);              // bright end cap
        }
        if (h >= 4) {
            for (int i = 1; i < 4; i++) {
                int tickX = x + w * i / 4;
                Draw.fill(tickX, y + 1, tickX + 1, y + h - 1, 0x30000000);
            }
        }
    }

    /** Small rounded pill with centred text, tinted by {@code tint}. */
    private void pill(int x, int y, String text, int tint, int textColor) {
        int pw = Draw.width(text) + 8;
        Draw.rrect(x, y, x + pw, y + 10, 4, Draw.withAlpha(tint, 0x40));
        Draw.text(text, x + 4, y + 1, textColor, false);
    }

    /** Card frame: soft coloured glow, base box, health-tinted border + inner hairline, top sheen, corner brackets. */
    private void drawFrame(int x, int y, int w, int h, int accent) {
        int c = accent & 0xFFFFFF;
        // outer glow (two soft layers outside the box)
        Draw.rrect(x - 2, y - 2, x + w + 2, y + h + 2, 7, Draw.withAlpha(c, 0x14));
        Draw.rrect(x - 1, y - 1, x + w + 1, y + h + 1, 6, Draw.withAlpha(c, 0x22));
        drawBox(x, y, w, h);
        // top sheen + bottom shade give the panel depth
        Draw.rrect(x + 2, y + 2, x + w - 2, y + h / 2, 4, 0x10FFFFFF);
        Draw.rrect(x + 2, y + h - h / 4, x + w - 2, y + h - 2, 4, 0x14000000);
        // border (health colour) + inner hairline
        Draw.ring(x, y, x + w, y + h, Draw.withAlpha(c, 0x99));
        Draw.ring(x + 1, y + 1, x + w - 1, y + h - 1, 0x16FFFFFF);
        // corner brackets
        int b = Draw.withAlpha(c, 0xE0), L = 6;
        Draw.fill(x + 3, y + 3, x + 3 + L, y + 4, b);
        Draw.fill(x + 3, y + 3, x + 4, y + 3 + L, b);
        Draw.fill(x + w - 3 - L, y + 3, x + w - 3, y + 4, b);
        Draw.fill(x + w - 4, y + 3, x + w - 3, y + 3 + L, b);
        Draw.fill(x + 3, y + h - 4, x + 3 + L, y + h - 3, b);
        Draw.fill(x + 3, y + h - 3 - L, x + 4, y + h - 3, b);
        Draw.fill(x + w - 3 - L, y + h - 4, x + w - 3, y + h - 3, b);
        Draw.fill(x + w - 4, y + h - 3 - L, x + w - 3, y + h - 3, b);
    }

    private static Method entityMethod;
    private static boolean entitySearched;

    /** Vẽ mô hình sinh vật bằng InventoryScreen (tên hàm / chữ ký đổi theo bản 26.x nên tìm bằng reflection). */
    private boolean drawModel(LivingEntity e, int ax, int ay) {
        if (!entitySearched) {
            entitySearched = true;
            try {
                Class<?> c = Class.forName("net.minecraft.client.gui.screens.inventory.InventoryScreen");
                for (Method m : c.getMethods()) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (Modifier.isStatic(m.getModifiers()) && m.getName().contains("ntityInInventoryFollowsMouse")
                            && pt.length > 5 && pt[pt.length - 1].isAssignableFrom(LivingEntity.class)) { entityMethod = m; break; }
                }
            } catch (ReflectiveOperationException | LinkageError ignored) { }
        }
        if (entityMethod == null) return false;
        try {
            Class<?>[] pt = entityMethod.getParameterTypes();
            Object[] args = new Object[pt.length];
            int ints = 0, floats = 0;
            int[] iv = {ax, ay, ax + AVATAR, ay + AVATAR, Math.max(4, (int) Math.min(AVATAR / Math.max(0.3f, e.getBbHeight()), AVATAR / (Math.max(0.3f, e.getBbWidth()) * 1.3f)))};
            float[] fv = {0.0625f, ax + AVATAR / 2f, ay + AVATAR / 2f - e.getEyeHeight() * 8f};
            for (int i = 0; i < pt.length; i++) {
                Class<?> t = pt[i];
                if (t.isAssignableFrom(Draw.g.getClass())) args[i] = Draw.g;
                else if (t == int.class) args[i] = iv[Math.min(ints++, iv.length - 1)];
                else if (t == float.class) args[i] = fv[Math.min(floats++, fv.length - 1)];
                else if (t == Vector3f.class) args[i] = new Vector3f(0f, e.getBbHeight() / 2f + 0.0625f, 0f);
                else if (t.isAssignableFrom(LivingEntity.class)) args[i] = e;
                else return false;
            }
            Draw.scissor(ax, ay, AVATAR, AVATAR);
            try { entityMethod.invoke(null, args); } finally { Draw.endScissor(); }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            entityMethod = null;
            return false;
        }
    }

    private void drawAvatar(LivingEntity e, int ax, int ay, boolean preview) {
        boolean ok = false;
        if (e != null && !preview) {
            if (e instanceof Player p) {
                PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null
                        : Minecraft.getInstance().getConnection().getPlayerInfo(p.getUUID());
                ok = Draw.head(info, ax, ay, AVATAR);
            }
            if (!ok) ok = drawModel(e, ax, ay);
        }
        if (!ok) {
            Draw.rrect(ax, ay, ax + AVATAR, ay + AVATAR, 4, 0x40FFFFFF);
            String q = e != null && !preview ? Draw.strip(e.getDisplayName().getString()) : "?";
            q = q.isEmpty() ? "?" : q.substring(0, 1).toUpperCase();
            Draw.text(q, ax + (AVATAR - Draw.width(q)) / 2f, ay + (AVATAR - 8) / 2f, 0xFFFFFFFF, true);
        }
    }

    private LivingEntity lastTarget;
    private float shownRatio = 1f;
    private float trailRatio = 1f;

    @Override protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity e = preview ? null : target();
        if (!preview && e == null) {
            lastTarget = null;
            return;
        }
        int w = getWidth(preview), h = getHeight(preview);

        float hp = preview || e == null ? 8.2f : Math.max(0f, e.getHealth());
        float maxHp = preview || e == null ? 20f : Math.max(1f, e.getMaxHealth());
        float ratio = Math.max(0f, Math.min(1f, hp / maxHp));
        // thanh máu chạy mượt; vệt trắng tụt chậm phía sau khi mất máu; đổi mục tiêu thì nhảy thẳng
        if (preview || e != lastTarget) {
            shownRatio = ratio;
            trailRatio = ratio;
        } else {
            shownRatio += (ratio - shownRatio) * 0.2f;
            trailRatio = trailRatio > shownRatio ? trailRatio + (shownRatio - trailRatio) * 0.06f : shownRatio;
        }
        lastTarget = e;

        int hc;
        if (healthMode.is("Xanh lá")) hc = 0xFF5CE08A;
        else if (healthMode.is("Đỏ")) hc = 0xFFFF5C5C;
        else if (healthMode.is("Tùy chỉnh")) hc = 0xFF000000 | healthColor.rgb();
        else hc = Draw.lerp(0xFFFF5C5C, 0xFF5CE08A, shownRatio);

        drawFrame(x, y, w, h, hc);

        int avX = x + PAD, avY = y + (h - AVATAR) / 2;
        int textX = showAvatar.value ? avX + AVATAR + 8 : x + 8;
        int right = x + w - PAD;
        if (showAvatar.value) {
            // dark rounded plate + thin ring tinted by the health colour
            Draw.rrect(avX - 2, avY - 2, avX + AVATAR + 2, avY + AVATAR + 2, 5, Draw.withAlpha(hc, 0x30));   // soft glow
            Draw.rrect(avX - 1, avY - 1, avX + AVATAR + 1, avY + AVATAR + 1, 4, 0xA0000000);
            Draw.rrect(avX, avY, avX + AVATAR, avY + AVATAR / 2, 3, 0x14FFFFFF);                                // top sheen
            Draw.ring(avX - 1, avY - 1, avX + AVATAR + 1, avY + AVATAR + 1, Draw.withAlpha(hc, 0xCC));
        }

        // text column is vertically centred in the card
        int lineY = y + (h - contentHeight()) / 2;

        String ds = null;
        int pillW = 0;
        if (showDistance.value) {
            double d = preview || e == null || mc.player == null ? 3.2 : mc.player.distanceTo(e);
            ds = String.format(java.util.Locale.ROOT, "%.1fm", d);
            pillW = Draw.width(ds) + 8;
        }
        String name = targetName(e, preview);
        if (!nameMode.is("Ẩn") && name.length() > 0) {
            int maxW = Math.max(10, right - textX - (pillW > 0 ? pillW + 4 : 0));
            Draw.text(Draw.fit(name, maxW), textX, lineY + 1, 0xFF000000 | nameColor.rgb(), shadow.value);
        }
        if (ds != null) {
            int px = right - pillW;
            pill(px, lineY, ds, 0xFFFFFF, 0xFFD8D8D8);
        }
        if (!nameMode.is("Ẩn") || ds != null) lineY += ROW;

        if (showHealth.value) {
            if (showHeartText.value) {
                String pct = Math.round(ratio * 100f) + "%";
                pill(textX, lineY, pct, hc & 0xFFFFFF, hc);
                if (showMaxHealth.value) {
                    String hpTxt = String.format(java.util.Locale.ROOT, "%.1f/%d", hp, (int) maxHp);
                    Draw.text(hpTxt, right - Draw.width(hpTxt), lineY + 1, 0xFFC0C0C0, false);
                }
                lineY += TEXT_ROW;
            }
            if (!nameMode.is("Ẩn") || showDistance.value || showHeartText.value) lineY += GAP;
            healthBar(textX, lineY, right - textX, barHeight.getInt(), shownRatio, trailRatio, hc);
        }
        if (showAvatar.value) drawAvatar(e, avX, avY, preview);
    }
}
