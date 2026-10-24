package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.gui.Proj;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ColorSetting;
import dev.lunarlite.setting.SliderSetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Phát hiện kẻ địch: bảng danh sách (tên, avatar, máu, khoảng cách, mũi tên hướng, báo âm thanh khi có người vào)
 * + "Enemy Outline". Ở 1.8.9 viền là silhouette stencil; ở 26.2 viền là silhouette thật: mixin
 * {@code MinecraftGlowMixin} / {@code EntityGlowColorMixin} bật hiệu ứng "glowing" của game cho đúng các mục tiêu
 * và đổi màu viền (rainbow / gần nhất / màu chọn). Vì game tự vẽ nên độ dày viền do game quyết định.
 * Khung hộp 2D (chiếu từ thế giới, có glow + độ dày) giữ lại làm lựa chọn "Khung hộp 2D".
 */
public class EnemyDetectorModule extends HudModule {
    private static final int W = 140, HEAD = 20, ROW_FULL = 20, ROW_COMPACT = 12;

    private final SliderSetting radius = add(new SliderSetting("radius", "Bán kính phát hiện", 30, 5, 100, 1, " block"));
    private final SliderSetting maxRows = add(new SliderSetting("max_rows", "Số người hiển thị tối đa", 5, 1, 8, 1, ""));
    private final BoolSetting showHealth = add(new BoolSetting("show_health", "Hiện thanh máu", true));
    private final BoolSetting showFace = add(new BoolSetting("show_face", "Hiện avatar", true));
    private final BoolSetting showDir = add(new BoolSetting("show_dir", "Hiện mũi tên hướng", true));
    private final SliderSetting rainbowSpeed = add(new SliderSetting("rainbow_speed", "Tốc độ rainbow (giây/vòng)", 4.0, 1.0, 10.0, 0.5, "s"));
    private final SliderSetting arrowSize = add(new SliderSetting("arrow_size", "Cỡ mũi tên", 1.0, 0.6, 2.0, 0.1, "x"), "Arrow", showDir);
    private final BoolSetting arrowRainbow = add(new BoolSetting("arrow_rainbow", "Rainbow mũi tên", false), "Arrow", showDir);
    private final BoolSetting arrowOutline = add(new BoolSetting("arrow_outline", "Viền mũi tên (Outline)", false), "Arrow", showDir);
    private final ColorSetting arrowOutColor = add(new ColorSetting("arrow_outline_color", "Màu viền", 0xFFFFFF), "Arrow", showDir);
    private final SliderSetting arrowOutWidth = add(new SliderSetting("arrow_outline_width", "Độ dày viền", 1.0, 0.5, 3.0, 0.25, "px"), "Arrow", showDir);
    private final BoolSetting arrowOutRainbow = add(new BoolSetting("arrow_outline_rainbow", "Rainbow viền", false), "Arrow", showDir);
    private final BoolSetting arrowGlow = add(new BoolSetting("arrow_glow", "Hào quang mũi tên", false), "Arrow", showDir);
    private final BoolSetting ignoreTeam = add(new BoolSetting("ignore_team", "Bỏ qua đồng đội (cùng team)", true));
    private final BoolSetting ignoreNpc = add(new BoolSetting("ignore_npc", "Bỏ qua NPC", true));
    private final BoolSetting alert = add(new BoolSetting("alert", "Báo âm thanh khi có người vào", true));
    private final ColorSetting alertColor = add(new ColorSetting("alert_color", "Màu cảnh báo", 0xFF5555));
    private final BoolSetting showList = add(new BoolSetting("show_list", "Hiện danh sách", true));

    private final BoolSetting outline = add(new BoolSetting("outline", "Enemy Outline", false), "Outline");
    private final ColorSetting outColor = add(new ColorSetting("outline_color", "Color", 0xFFFFFF), "Outline", outline);
    private final SliderSetting outWidth = add(new SliderSetting("outline_width", "Line Width", 2.0, 1.0, 10.0, 0.5, "px"), "Outline", outline);
    private final BoolSetting outGlow = add(new BoolSetting("outline_glow", "Glow", true), "Outline", outline);
    private final SliderSetting outMax = add(new SliderSetting("outline_max", "Max Players", 5, 1, 10, 1, ""), "Outline", outline);
    private final BoolSetting throughWalls = add(new BoolSetting("outline_walls", "Through Walls", true), "Outline", outline);
    private final BoolSetting outNearHi = add(new BoolSetting("outline_near_hi", "Highlight Nearest", false), "Outline", outline);
    private final ColorSetting outNearColor = add(new ColorSetting("outline_near_color", "Nearest Color", 0xFF3B3B), "Outline", outline);
    private final BoolSetting outNearestOnly = add(new BoolSetting("outline_nearest_only", "Nearest Only", false), "Outline", outline);
    private final BoolSetting outInvisible = add(new BoolSetting("outline_invisible", "Show Invisible", true), "Outline", outline);
    private final BoolSetting outRainbow = add(new BoolSetting("outline_rainbow", "Rainbow Outline", true), "Outline", outline);
    private final BoolSetting outBox = add(new BoolSetting("outline_box", "Khung hộp 2D (thay silhouette)", false), "Outline", outline);
    private final BoolSetting outDebug = add(new BoolSetting("outline_debug", "Debug (chat)", false), "Outline", outline);

    private static class Entry {
        final int id;
        final String name;
        final float dist, hp, bearing;
        final UUID uuid;

        Entry(int id, String name, float dist, float hp, float bearing, UUID uuid) {
            this.id = id; this.name = name; this.dist = dist; this.hp = hp; this.bearing = bearing; this.uuid = uuid;
        }
    }

    private static final List<Entry> SAMPLE = new ArrayList<>();
    static {
        SAMPLE.add(new Entry(-1, "Steve", 8.4f, 20f, 0f, null));
        SAMPLE.add(new Entry(-2, "Alex", 17.2f, 14f, 70f, null));
        SAMPLE.add(new Entry(-3, "Notch", 26.9f, 6f, -140f, null));
    }

    private static final int GRACE_TICKS = 10;
    private static final double STICKY_RANGE = 3.0;
    private static final int C_SAFE = 0xFF5CE08A, C_WARN = 0xFFFFC857, C_DANGER = 0xFFFF5C5C;

    private static final Comparator<Entry> BY_DIST = Comparator.comparingDouble(e -> e.dist);

    private List<Entry> found = new ArrayList<>();
    private final Set<Integer> inside = new HashSet<>();
    private final Map<Integer, Integer> listMiss = new HashMap<>();
    private final Set<Integer> outlineIds = new HashSet<>();
    private int nearestId = -1;
    private int cooldown, flash;
    private final Map<Integer, Float> appear = new HashMap<>();
    private final Map<Integer, Float> hpAnim = new HashMap<>();

    public EnemyDetectorModule() {
        super("enemy_detector", "Phát hiện kẻ địch",
                "Hiện người chơi khác trong bán kính bạn chọn, kèm khoảng cách, máu và viền kẻ địch.",
                Category.PVP, true, 4, 20);
    }

    // ------------------------------------------------------------------ quét

    private boolean isEnemy(Minecraft mc, Player p) {
        if (ignoreNpc.value && !HypixelUtil.isRealPlayer(p)) return false;
        if (ignoreTeam.value && TeamUtil.isTeammate(mc.player, p)) return false;
        return true;
    }

    private static boolean alive(Player p) {
        return !p.isRemoved() && p.deathTime <= 0;
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (!enabled || mc.level == null || me == null) {
            found = new ArrayList<>();
            inside.clear();
            listMiss.clear();
            clearOutline();
            flash = 0;
            return;
        }
        if (cooldown > 0) cooldown--;
        if (flash > 0) flash--;

        float r = radius.getInt();
        List<Entry> list = new ArrayList<>();
        Set<Integer> now = new HashSet<>();
        boolean newcomer = false;
        for (Player p : mc.level.players()) {
            if (p == me || !alive(p) || p.isSpectator()) continue;
            int id = p.getId();
            boolean was = inside.contains(id);
            float d = me.distanceTo(p);
            if (d > r + (was ? STICKY_RANGE : 0.0)) continue;
            if (isEnemy(mc, p)) {
                listMiss.remove(id);
            } else if (was) {
                int n = listMiss.getOrDefault(id, 0) + 1;
                if (n > GRACE_TICKS) { listMiss.remove(id); continue; }
                listMiss.put(id, n);
            } else {
                continue;
            }
            float bearing = (float) (Math.atan2(-(p.getX() - me.getX()), p.getZ() - me.getZ()) * 180.0 / Math.PI);
            list.add(new Entry(id, p.getGameProfile().name(), d, p.getHealth() + p.getAbsorptionAmount(), bearing, p.getUUID()));
            now.add(id);
            if (!was) newcomer = true;
        }
        Collections.sort(list, BY_DIST);
        found = list;
        inside.clear();
        inside.addAll(now);
        listMiss.keySet().retainAll(now);

        if (newcomer) flash = 24;
        if (alert.value && newcomer && cooldown == 0) {
            dev.lunarlite.SaoFx.ui("alert.enemy", 1f);
            cooldown = 20;
        }

        if (outline.value) updateOutlineTargets(mc, me); else clearOutline();
    }

    // ------------------------------------------------------------------ chọn mục tiêu viền

    private static final int LOBBY_TICKS = 40;
    /** Chỉ đủ để chống đảo thứ hạng khi hai người cách nhau gần bằng nhau. */
    private static final float STICKY_BONUS = 0.25f;
    /** Người đang trượt điều kiện (giữ chỗ tạm) luôn xếp sau mọi kẻ địch hợp lệ. */
    private static final float GRACE_PENALTY = 1000f;
    /** Đẩy kẻ địch gần nhất lên hạng 1 chắc chắn. */
    private static final float NEAREST_BOOST = 10000f;

    private static final class Cand {
        final Player p;
        final float key;
        Cand(Player p, float key) { this.p = p; this.key = key; }
    }

    private static final Comparator<Cand> CAND_ORDER = (a, b) -> Float.compare(a.key, b.key);

    private final List<Cand> candBuf = new ArrayList<>();
    /** id -> số tick liên tiếp đã trượt điều kiện (chỉ có với người đang được viền). */
    private final Map<Integer, Integer> miss = new HashMap<>();
    private int lobbyTicks;
    private int[] eligible = new int[16];
    private int eligibleN;
    private long lastLiveNs;
    private int dbgTick, dbgGlow;

    /** Hợp lệ để viền (còn sống, đúng điều kiện tàng hình, là kẻ địch thật) — KHÔNG xét bán kính. */
    private boolean valid(Minecraft mc, Player p) {
        if (!alive(p)) return false;
        if (p.isInvisible() && !outInvisible.value) return false;
        return isEnemy(mc, p);
    }

    private void addEligible(int id) {
        if (eligibleN == eligible.length) eligible = Arrays.copyOf(eligible, eligibleN * 2);
        eligible[eligibleN++] = id;
    }

    private void clearOutline() {
        outlineIds.clear();
        nearestId = -1;
        eligibleN = 0;
        miss.clear();
    }

    private void updateOutlineTargets(Minecraft mc, LocalPlayer me) {
        if (HypixelUtil.inLobby()) {
            if (lobbyTicks < 10000) lobbyTicks++;
        } else {
            lobbyTicks = 0;
        }
        if (lobbyTicks > LOBBY_TICKS) { clearOutline(); return; }

        List<Cand> cand = candBuf;
        cand.clear();
        Player nearest = null;
        float nearestKey = Float.MAX_VALUE, nearestDist = 0f;
        double rad = radius.value;
        eligibleN = 0;
        for (Player p : mc.level.players()) {
            if (p == me || p.isRemoved() || p.isSpectator()) continue;
            int id = p.getId();
            boolean was = outlineIds.contains(id);
            float d = me.distanceTo(p);
            boolean valid = valid(mc, p);
            boolean ok = valid && d <= rad + (was ? STICKY_RANGE : 0.0);
            // Kẻ địch gần nhất: khoảng cách THẬT, cập nhật mỗi tick, không giữ chỗ.
            if (valid && d <= rad + STICKY_RANGE) addEligible(id);
            if (valid && d <= rad && d < nearestKey) { nearestKey = d; nearest = p; nearestDist = d; }
            if (ok) {
                miss.remove(id);
            } else if (was && alive(p)) {
                int n = miss.getOrDefault(id, 0) + 1;
                if (n > GRACE_TICKS) { miss.remove(id); continue; }
                miss.put(id, n); // giữ thêm vài tick, tránh nháy
            } else {
                continue;
            }
            float key = was ? d - STICKY_BONUS : d;
            if (!valid) key += GRACE_PENALTY;
            cand.add(new Cand(p, key));
        }
        if (nearest != null) {
            for (int i = cand.size() - 1; i >= 0; i--) {
                if (cand.get(i).p == nearest) cand.remove(i);
            }
            cand.add(new Cand(nearest, nearestDist - NEAREST_BOOST));
            miss.remove(nearest.getId());
        }
        cand.sort(CAND_ORDER);
        outlineIds.clear();
        int max = Math.min(cand.size(), outNearestOnly.value ? 1 : outMax.getInt());
        for (int i = 0; i < max; i++) outlineIds.add(cand.get(i).p.getId());
        nearestId = nearest != null && max > 0 ? nearest.getId() : -1;
        miss.keySet().retainAll(outlineIds);

        if (outDebug.value) {
            if (++dbgTick >= 20) { dbgTick = 0; printDebug(mc, me); }
        } else {
            dbgTick = 0;
        }
    }

    /**
     * Mỗi khung hình: tìm lại kẻ địch gần nhất trong số ứng viên và gắn viền cho người đó ngay, không đợi tick sau.
     * Người xa nhất đang có viền nhường chỗ nếu đã đủ "Max Players".
     */
    private void refreshNearestLive(Minecraft mc, float pt) {
        long now = System.nanoTime();
        if (now - lastLiveNs < 500000L) return; // chỉ 1 lần mỗi khung hình (nhiều entity cùng hỏi)
        lastLiveNs = now;
        LocalPlayer me = mc.player;
        if (me == null || mc.level == null || eligibleN == 0) return;
        double rad = radius.value, rsq = rad * rad;
        Vec3 mp = new Vec3(Mth.lerp(pt, me.xo, me.getX()), Mth.lerp(pt, me.yo, me.getY()), Mth.lerp(pt, me.zo, me.getZ()));
        int best = -1;
        double bestSq = Double.MAX_VALUE;
        for (int i = 0; i < eligibleN; i++) {
            Entity ent = mc.level.getEntity(eligible[i]);
            if (!(ent instanceof Player) || ent.isRemoved()) continue;
            double sq = distSq(ent, mp, pt);
            if (sq <= rsq && sq < bestSq) { bestSq = sq; best = eligible[i]; }
        }
        if (best == -1) return;
        nearestId = best;
        miss.remove(best);
        if (outNearestOnly.value) {
            if (outlineIds.size() != 1 || !outlineIds.contains(best)) {
                outlineIds.clear();
                outlineIds.add(best);
            }
            return;
        }
        if (outlineIds.contains(best)) return;
        if (outlineIds.size() >= outMax.getInt()) {
            int far = -1;
            double farSq = -1.0;
            for (int id : outlineIds) {
                Entity e2 = mc.level.getEntity(id);
                double sq = e2 == null ? Double.MAX_VALUE : distSq(e2, mp, pt);
                if (sq > farSq) { farSq = sq; far = id; }
            }
            if (far != -1) outlineIds.remove(far);
        }
        outlineIds.add(best);
    }

    private static double distSq(Entity e, Vec3 m, float pt) {
        double dx = Mth.lerp(pt, e.xo, e.getX()) - m.x;
        double dy = Mth.lerp(pt, e.yo, e.getY()) - m.y;
        double dz = Mth.lerp(pt, e.zo, e.getZ()) - m.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void printDebug(Minecraft mc, LocalPlayer me) {
        try {
            me.sendSystemMessage(Component.literal("§b[Outline]§r mục tiêu=" + outlineIds.size() + " sảnh=" + lobbyTicks
                    + " | glow=" + dbgGlow + " | khung2D=" + (outBox.value ? "bật" : "tắt")));
            StringBuilder sb = new StringBuilder("§b[Outline]§r gần: ");
            int cnt = 0;
            for (Player q : mc.level.players()) {
                if (q == me) continue;
                float dd = me.distanceTo(q);
                if (dd > radius.value + STICKY_RANGE) continue;
                String why;
                if (q.isRemoved() || q.deathTime > 0) why = "chết";
                else if (q.isSpectator()) why = "spectator";
                else if (q.isInvisible() && !outInvisible.value) why = "tàng hình(tắt)";
                else if (ignoreNpc.value && !HypixelUtil.isRealPlayer(q)) why = "bị coi là NPC(uuid v" + q.getUUID().version() + ")";
                else if (ignoreTeam.value && TeamUtil.isTeammate(me, q)) why = "bị coi là ĐỒNG ĐỘI";
                else if (dd > radius.value) why = "ngoài bán kính";
                else if (outlineIds.contains(q.getId())) why = "OK(có trong danh sách viền)";
                else why = "hợp lệ nhưng không vào danh sách";
                sb.append(q.getGameProfile().name()).append(' ').append(Math.round(dd)).append("m=").append(why).append("; ");
                if (++cnt >= 4) break;
            }
            if (cnt > 0) me.sendSystemMessage(Component.literal(sb.toString()));
        } catch (Throwable ignored) { }
        dbgGlow = 0;
    }

    // ------------------------------------------------------------------ silhouette (hook của mixin)

    /** {@code Minecraft#shouldEntityAppearGlowing}: true nếu entity này là mục tiêu viền. */
    public boolean glowWanted(Entity e) {
        if (!enabled || !outline.value || outBox.value || !(e instanceof Player) || outlineIds.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        refreshNearestLive(mc, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (!outlineIds.contains(e.getId())) return false;
        if (!throughWalls.value && !visible(mc, mc.player.getEyePosition(), e.position().add(0, e.getBbHeight() * 0.6, 0))) return false;
        dbgGlow++;
        return true;
    }

    /** {@code Entity#getTeamColor}: màu viền (RGB) của mục tiêu, hoặc -1 nếu không phải mục tiêu. */
    public int glowRgb(Entity e) {
        if (!enabled || !outline.value || outBox.value || !outlineIds.contains(e.getId())) return -1;
        if (outNearHi.value && e.getId() == nearestId) return outNearColor.rgb() & 0xFFFFFF;
        if (outRainbow.value) return rainbowRgb((e.getId() * 61) % 360);
        return outColor.rgb() & 0xFFFFFF;
    }

    // ------------------------------------------------------------------ viền (2D)

    /** Gọi mỗi khung hình trong lớp HUD (sau {@link Proj#setup}). */
    public void overlay(float pt) {
        if (!enabled || !outline.value || !outBox.value || outlineIds.isEmpty() || !Proj.ready) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        refreshNearestLive(mc, pt);
        int idx = 0;
        for (int id : outlineIds) {
            if (!(mc.level.getEntity(id) instanceof Player p) || p.isRemoved()) continue;
            idx++;
            double x = Mth.lerp(pt, p.xo, p.getX()), y = Mth.lerp(pt, p.yo, p.getY()), z = Mth.lerp(pt, p.zo, p.getZ());
            double hw = p.getBbWidth() / 2.0, h = p.getBbHeight();
            if (!throughWalls.value && !visible(mc, new Vec3(Proj.camX, Proj.camY, Proj.camZ), new Vec3(x, y + h * 0.6, z))) continue;
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            boolean any = false;
            for (int i = 0; i < 8; i++) {
                float[] s = Proj.project(x + ((i & 1) == 0 ? -hw : hw), y + ((i & 2) == 0 ? 0 : h), z + ((i & 4) == 0 ? -hw : hw));
                if (s == null) { any = false; minX = Float.MAX_VALUE; break; }
                any = true;
                minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
                minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
            }
            if (!any || maxX - minX > 6000f) continue;
            int rgb = outColor.rgb() & 0xFFFFFF;
            if (outRainbow.value) rgb = rainbowRgb(idx * 60f);
            if (outNearHi.value && id == nearestId) rgb = outNearColor.rgb() & 0xFFFFFF;
            int w = Math.max(1, Math.round(outWidth.getFloat()));
            int x1 = Math.round(minX), y1 = Math.round(minY), x2 = Math.round(maxX), y2 = Math.round(maxY);
            if (outGlow.value) {
                for (int g = 3; g >= 1; g--) frame(x1 - g * 2, y1 - g * 2, x2 + g * 2, y2 + g * 2, w, Draw.withAlpha(rgb, 0x18 + (4 - g) * 0x10));
            }
            frame(x1, y1, x2, y2, w, Draw.withAlpha(rgb, 0xFF));
        }
    }

    private static void frame(int x1, int y1, int x2, int y2, int w, int argb) {
        Draw.fill(x1, y1, x2, y1 + w, argb);
        Draw.fill(x1, y2 - w, x2, y2, argb);
        Draw.fill(x1, y1 + w, x1 + w, y2 - w, argb);
        Draw.fill(x2 - w, y1 + w, x2, y2 - w, argb);
    }

    private static boolean visible(Minecraft mc, Vec3 cam, Vec3 target) {
        return mc.level.clip(new ClipContext(cam, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player))
                .getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------ hiển thị

    private int rowH() { return showHealth.value || showFace.value ? ROW_FULL : ROW_COMPACT; }

    private int rows(boolean preview) {
        return Math.min(preview ? SAMPLE.size() : found.size(), maxRows.getInt());
    }

    @Override public int getWidth(boolean preview) { return preview || showList.value ? W : 0; }

    @Override
    public int getHeight(boolean preview) {
        int n = rows(preview);
        return HEAD + (n > 0 ? n * rowH() + 3 : 0);
    }

    private int distColor(float d) {
        float ratio = d / Math.max(1f, radius.getInt());
        if (ratio < 0.34f) return C_DANGER;
        if (ratio < 0.67f) return C_WARN;
        return C_SAFE;
    }

    private static int hpColor(float hp) {
        float r = hp / 20f;
        return r > 0.7f ? C_SAFE : r > 0.35f ? C_WARN : C_DANGER;
    }

    private int rainbowRgb(float offset) {
        float period = Math.max(500f, rainbowSpeed.getFloat() * 1000f);
        float hue = (((System.currentTimeMillis() % (long) period) / period) + offset / 360f) % 1f;
        if (hue < 0f) hue += 1f;
        return java.awt.Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF;
    }

    /** Mũi tên chỉ hướng (đầu nhọn + khía đuôi). deg = 0 là phía trước mặt, dương là sang phải. */
    private void drawArrow(float cx, float cy, float deg, int argb, float rowOffset) {
        int fillRgb = argb & 0xFFFFFF;
        if (arrowRainbow.value) fillRgb = rainbowRgb(rowOffset);
        int lineRgb = arrowOutRainbow.value ? rainbowRgb(rowOffset + 90f) : (arrowOutColor.rgb() & 0xFFFFFF);
        float s = arrowSize.getFloat();
        // làm việc ở độ phân giải gấp 4 để quét tam giác bằng số nguyên
        float[] vx = {0f, 2.8f * s * 4, 0f, -2.8f * s * 4};
        float[] vy = {-3.6f * s * 4, 2.6f * s * 4, 0.8f * s * 4, 2.6f * s * 4};
        Draw.push();
        Draw.translate(cx, cy);
        Draw.rotate((float) Math.toRadians(deg));
        Draw.scale(0.25f, 0.25f);
        if (arrowGlow.value) {
            for (int i = 3; i >= 1; i--) tri(vx, vy, i * 5, Draw.withAlpha(fillRgb, 0x14));
        }
        if (arrowOutline.value) tri(vx, vy, Math.max(2, Math.round(arrowOutWidth.getFloat() * 4f)), Draw.withAlpha(lineRgb, 0xFF));
        tri(vx, vy, 0, Draw.withAlpha(fillRgb, 0xFF));
        Draw.pop();
    }

    /** Tô mũi tên (mũi nhọn, hai cánh, khía đuôi) nở ra {@code grow} đơn vị; toạ độ ở độ phân giải gấp 4. */
    private static void tri(float[] vx, float[] vy, int grow, int argb) {
        int top = Math.round(vy[0]) - grow, bottom = Math.round(vy[1]) + grow;
        for (int y = top; y < bottom; y++) {
            float yy = y + 0.5f;
            float t = Math.max(0f, Math.min(1f, (yy - vy[0]) / (vy[1] - vy[0])));
            int outer = Math.round(vx[1] * t) + grow;
            int inner = 0;
            if (yy > vy[2]) {
                float nt = Math.max(0f, Math.min(1f, (yy - vy[2]) / Math.max(0.01f, vy[1] - vy[2])));
                inner = Math.max(0, Math.round(vx[1] * nt) - grow);
            }
            if (inner <= 0) {
                Draw.fill(-outer, y, outer, y + 1, argb);
            } else if (outer > inner) {
                Draw.fill(-outer, y, -inner, y + 1, argb);
                Draw.fill(inner, y, outer, y + 1, argb);
            }
        }
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        List<Entry> es = preview ? SAMPLE : found;
        int total = es.size();
        int h = getHeight(preview);
        boolean danger = total > 0;
        int status = danger ? distColor(es.get(0).dist) : C_SAFE;
        int alertRgb = alertColor.rgb() & 0xFFFFFF;
        float pulse = preview || flash <= 0 ? 0f
                : (float) (Math.sin(flash * 0.8) * 0.5 + 0.5) * Math.min(1f, flash / 8f);

        if (background.value) Draw.rrect(x, y, x + W, y + h, 3, bgColor());
        if (border.value) Draw.ring(x, y, x + W, y + h, Draw.withAlpha(status, 0x88));

        Draw.rrect(x + 2, y + 2, x + W - 2, y + HEAD - 2, 3, 0x40000000);
        if (pulse > 0.01f) Draw.rrect(x + 2, y + 2, x + W - 2, y + HEAD - 2, 3, Draw.withAlpha(alertRgb, Math.round(pulse * 0x66)));

        int dot = danger ? Draw.withAlpha(alertRgb, 0xFF) : C_SAFE;
        int icx = x + 10, icy = y + HEAD / 2;
        Draw.rrect(icx - 4, icy - 4, icx + 5, icy + 5, 4, Draw.withAlpha(dot, 0x38 + Math.round(pulse * 0x70)));
        Draw.rrect(icx - 2, icy - 2, icx + 3, icy + 3, 2, dot);

        String title = danger ? "KẺ ĐỊCH" : "AN TOÀN";
        int ty0 = y + (HEAD - 8) / 2;
        Draw.text("§l" + title, x + 18, ty0, dot, shadow.value);
        if (danger) {
            String n = String.valueOf(total);
            int bw = Math.max(11, Draw.width(n) + 7);
            int bx = x + 18 + Draw.width("§l" + title) + 5;
            Draw.rrect(bx, y + 5, bx + bw, y + 15, 4, Draw.withAlpha(alertRgb, 0xFF));
            Draw.text(n, bx + (bw - Draw.width(n)) / 2f, ty0, 0xFF101010, false);
        }
        String rs = radius.getInt() + "m";
        int rw = Draw.width(rs) + 7;
        Draw.rrect(x + W - 4 - rw, y + 5, x + W - 4, y + 15, 4, 0x30FFFFFF);
        Draw.text(rs, x + W - 4 - rw + 3.5f, ty0, 0xFFB8B8B8, false);

        if (!danger) return;
        Draw.fadeLine(x + 6, y + HEAD - 1, W - 12, status & 0xFFFFFF, 0xAA);

        int rh = rowH();
        int shown = Math.min(total, maxRows.getInt());
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < shown; i++) {
            Entry e = es.get(i);
            int ry = y + HEAD + 2 + i * rh;
            int cardX = x + 3, cardW = W - 6, cardH = rh - 2;

            float ap = 1f, hpShow = e.hp;
            if (!preview) {
                Float cur = appear.get(e.id);
                ap = cur == null ? 0f : cur + (1f - cur) * 0.22f;
                if (ap > 0.99f) ap = 1f;
                appear.put(e.id, ap);
                Float hc = hpAnim.get(e.id);
                hpShow = hc == null ? e.hp : hc + (e.hp - hc) * 0.2f;
                hpAnim.put(e.id, hpShow);
                seen.add(e.id);
            }
            int dc = distColor(e.dist);
            boolean nearest = i == 0;

            Draw.push();
            Draw.translate(Math.round((1f - ap) * 16f), 0f);
            Draw.rrect(cardX, ry, cardX + cardW, ry + cardH, 3, nearest ? Draw.withAlpha(dc, 0x26) : 0x14FFFFFF);
            if (ap < 1f) Draw.rrect(cardX, ry, cardX + cardW, ry + cardH, 3, Draw.withAlpha(alertRgb, Math.round((1f - ap) * 0x55)));
            Draw.rrect(cardX + 1, ry + 3, cardX + 3, ry + 3 + Math.max(2, cardH - 6), 1, dc);

            int cx = cardX + 6;
            if (showFace.value) {
                int fs = rh - 8, fx = cx + 1, fy = ry + (cardH - fs) / 2;
                Draw.rrect(fx - 1, fy - 1, fx + fs + 1, fy + fs + 1, 2, nearest ? Draw.withAlpha(dc, 0xFF) : 0xFF000000);
                PlayerInfo info = e.uuid != null && mc.getConnection() != null ? mc.getConnection().getPlayerInfo(e.uuid) : null;
                if (!Draw.head(info, fx, fy, fs)) {
                    Draw.fill(fx, fy, fx + fs, fy + fs, 0xFF8A8A8A);
                    if (e.name != null && !e.name.isEmpty()) Draw.centered(e.name.substring(0, 1).toUpperCase(), fx + fs / 2f, fy + (fs - 8) / 2f, 0xFFFFFFFF, false);
                }
                cx += fs + 7;
            }
            boolean bar = showHealth.value;
            int ty = bar ? ry + 3 : ry + (cardH - 8) / 2;

            int rx = cardX + cardW - 4;
            String ds = (Math.round(e.dist * 10f) / 10f) + "m";
            int pw = Draw.width(ds) + 6;
            int px = rx - pw;
            Draw.rrect(px, ty - 1, px + pw, ty + 9, 3, Draw.withAlpha(dc, 0x38));
            Draw.text(ds, px + 3, ty, dc, shadow.value);

            int nameRight = px - 4;
            if (showDir.value) {
                float rel = preview || mc.player == null ? e.bearing : e.bearing - mc.player.getYRot();
                int acx = px - 8, acy = ty + 2;
                Draw.rrect(acx - 5, acy - 5, acx + 5, acy + 5, 5, 0x40000000);
                drawArrow(acx, acy, rel, dc, i * 45f);
                nameRight = acx - 7;
            }
            Draw.text(Draw.fit(e.name, Math.max(10, nameRight - cx)), cx, ty, textColor(x + ry), shadow.value);

            if (bar) {
                int bx0 = cx, bx1 = cardX + cardW - 4, by = ry + 12, bw = bx1 - bx0;
                Draw.hbar(bx0, by, bw, 3, Math.max(0f, Math.min(1f, hpShow / 20f)), hpColor(hpShow) | 0xFF000000, 0x66000000);
            }
            Draw.pop();
        }
        if (!preview) {
            appear.keySet().retainAll(seen);
            hpAnim.keySet().retainAll(seen);
        }
    }
}
