package dev.lunarlite.module;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import dev.lunarlite.Configuration;
import dev.lunarlite.LunarLite;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.SliderSetting;
import dev.lunarlite.gui.ChatPositionScreen;
import dev.lunarlite.gui.ChatView;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Chat Bar (bản 26.2, rút gọn): chỉ giữ tin người chơi + sự kiện Bedwars, tự dịch tin nhận sang tiếng Việt và tin gửi
 * sang tiếng Anh (Google Translate endpoint công khai, chạy nền, có cache).
 * Hiển thị: khung chat riêng {@link ChatView} (kính, avatar, tin giết người, kéo thả vị trí) thay chat vanilla qua
 * ChatComponentMixin; thanh nhập có viền màu + bộ đếm ký tự qua ChatScreenMixin.
 * Chưa có: animation gõ / xoá chữ, đổi vị trí / độ mờ thanh nhập (vanilla vẽ nền thanh nhập, không huỷ được an toàn).
 */
public class ChatBarModule extends Module {
    public static final BoolSetting PLAYER_ONLY = new BoolSetting("playerOnly", "Chỉ tin người chơi", true);
    public static final BoolSetting BEDWARS = new BoolSetting("bedwars", "Giữ tin sự kiện Bedwars", true);
    public static final BoolSetting SHOW_KILLS = new BoolSetting("kills", "Giữ tin giết người", true);
    public static final BoolSetting AUTO_TRANSLATE = new BoolSetting("translate", "Dịch sang tiếng Việt", true);
    public static final BoolSetting TRANSLATE_OUT = new BoolSetting("translateOut", "Dịch tin gửi sang tiếng Anh", true);
    public static final BoolSetting SHOW_OUT_ECHO = new BoolSetting("showOutEcho", "Hiện bản dịch đã gửi", true);
    public static final BoolSetting HIDE_ORIGINAL = new BoolSetting("hideOriginal", "Tự ẩn tin gốc", false);

    public static final BoolSetting CHAR_COUNTER = new BoolSetting("charCounter", "Bộ đếm ký tự", false);
    public static final BoolSetting PLAYER_HEADS = new BoolSetting("heads", "Avatar trước tin nhắn", true);
    public static final SliderSetting ICON_SIZE = new SliderSetting("iconSize", "Cỡ avatar", 8, 6, 14, 1, " px");
    public static final SliderSetting ICON_X = new SliderSetting("iconX", "Avatar lệch ngang", 0, -20, 20, 1, " px");
    public static final SliderSetting ICON_Y = new SliderSetting("iconY", "Avatar lệch dọc", 0, -10, 10, 1, " px");
    public static final SliderSetting BAR_WIDTH = new SliderSetting("barW", "Rộng thanh nhập (0 = tự động)", 0, 0, 600, 5, " px");
    public static final SliderSetting CHAT_WIDTH = new SliderSetting("chatWidth", "Rộng mỗi dòng chat", 320, 120, 900, 10, " px");
    public static final SliderSetting CHAT_GAP = new SliderSetting("chatGap", "Khoảng cách dòng chat", 0, 0, 4, 1, " dòng");
    public static final BoolSetting ANIM_MESSAGES = new BoolSetting("animMessages", "Animation tin nhắn mới", true);
    public static final BoolSetting BAR_BORDER = new BoolSetting("barBorder", "Viền màu thanh nhập", false);
    public static final BoolSetting HIDE_CHAT_BACKGROUND = new BoolSetting("hideChatBackground", "Ẩn nền khung chat", false);
    public static final BoolSetting FIT_CHAT_BACKGROUND = new BoolSetting("fitChatBackground", "Nền chat vừa chiều dài chữ", false);
    public static final BoolSetting CHAT_GLASS = new BoolSetting("chatGlass", "Khung chat kiểu kính (bo góc, viền màu)", true);
    public static final BoolSetting HIDE_CHAT_SCROLLBAR = new BoolSetting("hideChatScrollbar", "Ẩn thanh cuộn chat", false);

    /** Vị trí kéo thả (px GUI, tính từ vị trí mặc định; x phải +, y xuống +). */
    public static int windowX = 0, windowY = 0;

    public static String translateTo = "vi";
    public static String outLang = "en";

    private static ChatBarModule self;

    public static ChatBarModule instance() { return self; }

    private static final Pattern PLAYER_CHAT = Pattern.compile(
            "^(?:(?:Guild|Party|Officer|Co-op|From|To)\\s*>?\\s*)?(?:\\[[^\\]]{1,24}\\]\\s*)*(?:<([A-Za-z0-9_]{1,16})>\\s*(\\S.*)|([A-Za-z0-9_]{1,16})\\s*[:»>]\\s*(\\S.*))$");
    private static final Pattern BW_EVENT = Pattern.compile(
            "BED DESTRUCTION|FINAL KILL|TEAM ELIMINATED|has been eliminated|You have been eliminated"
                    + "|Protect your bed|The game starts in|Victory|Your bed was");
    private static final Pattern KILL_VERB = Pattern.compile(
            "\\b(killed|shot|knocked|struck|fell|died|burned|drowned|exploded|blown|suffocated|starved|squashed"
                    + "|withered|perished|slain|slaughtered|thrown|pushed|punched|fireballed|melted|zapped|hit)\\b");
    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ChatTranslate");
        t.setDaemon(true);
        return t;
    });
    private static final ExecutorService OUT = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ChatTranslateOut");
        t.setDaemon(true);
        return t;
    });

    private static final Map<String, String[]> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String[]> e) { return size() > 1024; }
    };
    private static volatile long failUntil;
    private static int fails;
    private static boolean sending;

    public ChatBarModule() {
        super("chatbar", "Chat Bar", "Lọc chat chỉ còn tin người chơi, tự dịch tin nhận sang tiếng Việt và tin gửi sang tiếng Anh.",
                Category.UTILITY, true);
        self = this;
        add(PLAYER_ONLY, "Chat");
        add(BEDWARS, "Chat", PLAYER_ONLY);
        add(SHOW_KILLS, "Chat", PLAYER_ONLY);
        add(AUTO_TRANSLATE, "Chat");
        add(HIDE_ORIGINAL, "Chat", AUTO_TRANSLATE);
        add(TRANSLATE_OUT, "Chat");
        add(SHOW_OUT_ECHO, "Chat", TRANSLATE_OUT);
        add(ANIM_MESSAGES, "Chat");
        add(CHAT_GLASS, "Chat");
        add(HIDE_CHAT_BACKGROUND, "Chat");
        add(FIT_CHAT_BACKGROUND, "Chat");
        add(HIDE_CHAT_SCROLLBAR, "Chat");
        add(CHAR_COUNTER, "Thanh nhập");
        add(BAR_BORDER, "Thanh nhập");
        add(PLAYER_HEADS, "Avatar");
        add(ICON_SIZE, "Avatar", PLAYER_HEADS);
        add(ICON_X, "Avatar", PLAYER_HEADS);
        add(ICON_Y, "Avatar", PLAYER_HEADS);
        add(BAR_WIDTH, "Vị trí");
        add(CHAT_WIDTH, "Vị trí");
        add(CHAT_GAP, "Vị trí");

        // chạy sau KillBoard (EARLY) để Kill Board vẫn đọc được tin dù bị ẩn khỏi chat
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> overlay || allowIncoming(msg));
        ClientSendMessageEvents.ALLOW_CHAT.register(ChatBarModule::allowOutgoing);
    }

    // ------------------------------------------------------------------ nhận

    /** {tên, nội dung} nếu là tin người chơi. */
    public static String[] parsePlayerChat(String plain) {
        Matcher m = PLAYER_CHAT.matcher(plain.trim());
        if (!m.matches()) return null;
        boolean angle = m.group(1) != null;
        String n = angle ? m.group(1) : m.group(3);
        String body = angle ? m.group(2) : m.group(4);
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null) return null;
        boolean tagged = plain.trim().startsWith("[");
        if (conn.getPlayerInfo(n) == null && !tagged) return null;
        return new String[]{n, body};
    }

    private static boolean isKill(String plain) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null || !KILL_VERB.matcher(plain).find()) return false;
        Matcher m = WORD.matcher(plain);
        while (m.find()) {
            if (conn.getPlayerInfo(m.group()) != null) return m.start() == 0;
        }
        return false;
    }

    private static boolean allowIncoming(Component message) {
        ChatBarModule me = self;
        if (me == null || !me.enabled || message == null) return true;
        String plain = message.getString();
        String[] pc = parsePlayerChat(plain);
        if (pc == null) {
            if (!PLAYER_ONLY.value) return true;
            if (BEDWARS.value && BW_EVENT.matcher(plain).find()) return true;
            return SHOW_KILLS.value && isKill(plain);
        }
        if (!AUTO_TRANSLATE.value || pc[1].length() < 2 || VI_TONE.matcher(pc[1]).find()) return true;
        final boolean hide = HIDE_ORIGINAL.value;
        final String name = pc[0], body = pc[1];
        POOL.execute(() -> {
            String[] r = null;
            try { r = translate(body, "auto", translateTo, 4000, false); } catch (IOException ignored) { }
            final String[] res = r;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.player == null) return;
                boolean same = res == null || res[1].equalsIgnoreCase(translateTo)
                        || res[0].trim().equalsIgnoreCase(body.trim());
                if (hide) {
                    dev.lunarlite.Compat.chat(mc.player, same ? message
                            : Component.literal("§7" + name + ": §f" + res[0] + " §8(dịch)"));
                } else if (!same) {
                    dev.lunarlite.Compat.chat(mc.player, Component.literal("§8  ↳ §7" + res[0]));
                }
            });
        });
        return !hide; // ẩn tin gốc: hiện lại khi bản dịch xong
    }

    // ------------------------------------------------------------------ gửi

    private static boolean allowOutgoing(String text) {
        ChatBarModule me = self;
        if (sending || me == null || !me.enabled || !TRANSLATE_OUT.value || text == null || text.length() < 2) return true;
        if (text.startsWith("/") || !text.matches(".*\\p{L}.*")) return true;
        sendTranslated(text);
        return false;
    }

    // ------------------------------------------------------------------ dịch

    private static String httpGet(String url, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return out.toString(StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** Trả về {bản dịch, mã ngôn ngữ gốc}. outgoing = tin gửi đi: bỏ qua cooldown lỗi. */
    private static String[] translate(String text, String src, String target, int timeoutMs, boolean outgoing) throws IOException {
        loadCache();
        String key = src + ">" + target + "\u0000" + text;
        String[] hit = cacheGet(key);
        if (hit != null) return hit;
        if (!outgoing && System.currentTimeMillis() < failUntil) throw new IOException("translate cooldown");
        String[] res;
        try {
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=" + src + "&tl="
                    + target + "&dt=t&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            JsonArray root = JsonParser.parseString(httpGet(url, timeoutMs)).getAsJsonArray();
            StringBuilder sb = new StringBuilder();
            for (JsonElement seg : root.get(0).getAsJsonArray()) sb.append(seg.getAsJsonArray().get(0).getAsString());
            String lang = root.size() > 2 && !root.get(2).isJsonNull() ? root.get(2).getAsString() : "";
            res = new String[]{sb.toString(), lang};
        } catch (IOException ex) {
            if (!outgoing) {
                synchronized (CACHE) {
                    if (++fails >= 2) { failUntil = System.currentTimeMillis() + 15000L; fails = 0; }
                }
            }
            throw ex;
        } catch (RuntimeException ex) {
            throw new IOException("bad translate response", ex);
        }
        synchronized (CACHE) {
            fails = 0;
            if (outgoing) failUntil = 0L;
        }
        cachePut(key, res);
        return res;
    }

    private static volatile long restoreFailUntil;
    private static boolean cacheLoaded;
    private static int unsaved;

    private static File cacheFile() {
        return new File(LunarLite.configDir(), "zhwii_translate_cache.json");
    }

    private static void loadCache() {
        synchronized (CACHE) {
            if (cacheLoaded) return;
            cacheLoaded = true;
            File f = cacheFile();
            if (f.isFile()) {
                try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                    Map<String, String[]> m = new Gson().fromJson(r, new TypeToken<LinkedHashMap<String, String[]>>() {}.getType());
                    if (m != null) {
                        for (Map.Entry<String, String[]> en : m.entrySet()) {
                            String[] v = en.getValue();
                            if (v != null && v.length >= 2 && v[0] != null && v[1] != null) CACHE.put(en.getKey(), v);
                        }
                    }
                } catch (Exception ignored) { }
            }
            Runtime.getRuntime().addShutdownHook(new Thread(ChatBarModule::saveCache, "ChatTranslateCacheSave"));
        }
    }

    /** Ghi cache qua file tạm rồi đổi tên (crash giữa chừng không làm hỏng file). */
    static void saveCache() {
        String json;
        synchronized (CACHE) {
            if (!cacheLoaded || unsaved == 0) return;
            json = new Gson().toJson(new LinkedHashMap<String, String[]>(CACHE));
            unsaved = 0;
        }
        try {
            File f = cacheFile();
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            File tmp = new File(f.getPath() + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) { w.write(json); }
            java.nio.file.Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) { }
    }

    private static String[] cacheGet(String key) {
        synchronized (CACHE) { return CACHE.get(key); }
    }

    private static void cachePut(String key, String[] val) {
        boolean save;
        synchronized (CACHE) {
            CACHE.put(key, val);
            save = ++unsaved >= 20;
        }
        if (save) POOL.execute(ChatBarModule::saveCache);
    }

    // ------------------------------------------------------------------ hướng dịch tin gửi (nút VI>EN / EN>VI)

    /** true = gõ tiếng Việt, gửi tiếng Anh (VI>EN); false = gõ tiếng Anh, gửi tiếng Việt (EN>VI). */
    public static boolean viToEn = true;

    public static String dirLabel() { return viToEn ? "VI>EN" : "EN>VI"; }

    public static void toggleDir() {
        viToEn = !viToEn;
        outLang = viToEn ? "en" : "vi";
        dev.lunarlite.LunarLite.saveConfig();
    }

    @Override
    public void load(Configuration c) {
        super.load(c);
        viToEn = c.getBoolean("viToEn", id, true, "Hướng dịch tin gửi: true = VI>EN, false = EN>VI");
        outLang = viToEn ? "en" : "vi";
        windowX = c.getInt("windowX", id, 0, -4000, 4000, "Vị trí ngang khung tin nhắn");
        windowY = c.getInt("windowY", id, 0, -4000, 4000, "Vị trí dọc khung tin nhắn");
    }

    @Override
    public void save(Configuration c) {
        super.save(c);
        c.get(id, "viToEn", true).set(viToEn);
        c.get(id, "windowX", 0).set(windowX);
        c.get(id, "windowY", 0).set(windowY);
    }

    @Override
    public void resetSettings() {
        super.resetSettings();
        windowX = windowY = 0;
    }

    /** Nút "Kéo thả vị trí chat…" trong bảng tuỳ chỉnh của menu. */
    @Override
    public List<Action> actions() {
        List<Action> l = new ArrayList<>();
        l.add(new Action("Kéo thả vị trí chat…", ChatBarModule::openPositionEditor, null));
        return l;
    }

    public static void openPositionEditor() {
        dev.lunarlite.Compat.setScreen(new ChatPositionScreen(dev.lunarlite.Compat.screen()));
    }

    // ------------------------------------------------------------------ tin giết người / màu đội

    /** {người bị giết, người giết hoặc null} nếu là tin chết/giết, ngược lại null. */
    public static String[] parseKill(String plain) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn == null || !KILL_VERB.matcher(plain).find()) return null;
        Matcher m = WORD.matcher(plain);
        String victim = null, killer = null;
        while (m.find()) {
            String w = m.group();
            if (conn.getPlayerInfo(w) == null) continue;
            if (victim == null) {
                if (m.start() != 0) return null; // tin chết luôn bắt đầu bằng tên người bị giết
                victim = w;
            } else if (!w.equals(victim)) {
                killer = w;
            }
        }
        return victim == null ? null : new String[]{victim, killer};
    }

    private static final Pattern TEAM_TAG = Pattern.compile(
            "\\[(RED|BLUE|GREEN|YELLOW|AQUA|WHITE|PINK|GRAY|GREY)\\]", Pattern.CASE_INSENSITIVE);
    private static final int[] MC_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static int colorByName(String n) {
        switch (n) {
            case "RED": return MC_COLORS[12];
            case "BLUE": return MC_COLORS[9];
            case "GREEN": return MC_COLORS[10];
            case "YELLOW": return MC_COLORS[14];
            case "AQUA": return MC_COLORS[11];
            case "WHITE": return MC_COLORS[15];
            case "PINK": return MC_COLORS[13];
            case "GRAY": case "GREY": return MC_COLORS[8];
            default: return -1;
        }
    }

    /** Màu đội (RGB) của người chơi theo team scoreboard; không có thì lấy từ tag [RED]/[BLUE]... trong tin; -1 nếu không rõ. */
    public static int teamRgb(String name, String plain) {
        Minecraft mc = Minecraft.getInstance();
        int c = -1;
        try {
            if (mc.level != null) {
                var team = mc.level.getScoreboard().getPlayersTeam(name);
                if (team != null) {
                    int cf = dev.lunarlite.Compat.teamRgb(team);
                    if (cf >= 0) c = cf & 0xFFFFFF;
                    if (c == -1 && team.getPlayerPrefix() != null) {
                        var tc = team.getPlayerPrefix().getStyle().getColor();
                        if (tc != null) c = tc.getValue() & 0xFFFFFF;
                    }
                }
            }
        } catch (Throwable ignored) { }
        if (c == -1 && plain != null && !plain.isEmpty()) {
            Matcher tm = TEAM_TAG.matcher(plain);
            if (tm.find()) c = colorByName(tm.group(1).toUpperCase());
        }
        return c;
    }

    /** Dịch tin gõ -> ngôn ngữ đích (thử tối đa 3 lần). Null nếu lỗi. */
    private static String translateOut(String text) {
        final String src, from;
        if (viToEn) {
            boolean english = looksEnglish(text);
            src = english ? text : normalizeVi(text);
            from = english ? "auto" : "vi";
        } else {
            src = text;
            from = "auto";
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String[] r = translate(src, from, outLang, 3000, true);
                String t = r[0].trim();
                if (!t.isEmpty()) return t;
            } catch (Exception ignored) { }
            if (attempt < 2) {
                try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
            }
        }
        return null;
    }

    /** Dịch tin bạn gõ rồi gửi (tách tối đa 3 tin <= 100 ký tự). Lỗi thì gửi nguyên văn, không nuốt tin. */
    static void sendTranslated(final String text) {
        OUT.execute(() -> {
            final String translated = translateOut(text);
            final boolean ok = translated != null;
            final java.util.List<String> parts = splitChat(ok ? translated : text);
            final Minecraft mc = Minecraft.getInstance();
            if (!ok) {
                mc.execute(() -> {
                    if (mc.player != null) dev.lunarlite.Compat.chat(mc.player, Component.literal(
                            "§c[Chat Bar] Không dịch được (lỗi mạng / Google giới hạn), đã gửi nguyên văn."));
                });
            }
            for (int i = 0; i < parts.size(); i++) {
                final String send = parts.get(i);
                mc.execute(() -> {
                    if (mc.player == null || mc.getConnection() == null) return;
                    sending = true;
                    try { mc.getConnection().sendChat(send); } finally { sending = false; }
                });
                if (i < parts.size() - 1) {
                    try { Thread.sleep(700); } catch (InterruptedException e) { return; }
                }
            }
            if (ok && SHOW_OUT_ECHO.value && !translated.equals(text)) {
                mc.execute(() -> {
                    if (mc.player != null) dev.lunarlite.Compat.chat(mc.player, Component.literal("§8  ↳ §7" + translated));
                });
            }
        });
    }

    // ------------------------------------------------------------------ tiếng Việt không dấu / Telex
    // ===== Tiếng Việt không dấu: mở rộng viết tắt + khôi phục dấu trước khi dịch sang tiếng Anh =====
    /** Có dấu thanh/dấu mũ (không tính "đ", vì "đc", "đi" vẫn cần xử lý tiếp). */
    private static final Pattern VI_TONE = Pattern.compile(
            "[àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹ]",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern WORD_TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    /** Viết tắt hay gặp khi chat -> dạng không dấu đầy đủ (sẽ được khôi phục dấu ở bước sau). */
    private static final Map<String, String> VI_SLANG = new java.util.HashMap<String, String>();
    static {
        String[][] p = {
                {"ko", "khong"}, {"k", "khong"}, {"hok", "khong"}, {"hong", "khong"}, {"kg", "khong"}, {"khg", "khong"},
                {"dc", "duoc"}, {"đc", "duoc"}, {"dk", "duoc"}, {"dcj", "duoc"},
                {"j", "gi"}, {"gj", "gi"}, {"r", "roi"}, {"rui", "roi"},
                {"vs", "voi"}, {"vz", "vay"}, {"v", "vay"}, {"z", "vay"},
                {"bn", "bao nhieu"}, {"bnh", "bao nhieu"}, {"ntn", "nhu the nao"},
                {"ae", "anh em"}, {"mn", "moi nguoi"}, {"mk", "minh"}, {"mik", "minh"},
                {"ng", "nguoi"}, {"nc", "noi chuyen"}, {"trc", "truoc"}, {"sr", "xin loi"},
                {"cx", "cung"}, {"cg", "cung"}, {"lm", "lam"}, {"nma", "nhung ma"}, {"nhg", "nhung"},
                {"thx", "cam on"}, {"tks", "cam on"}, {"bt", "binh thuong"}, {"ik", "di"},
                {"ak", "a"}, {"ah", "a"}, {"e", "em"}, {"a", "anh"}
        };
        for (String[] x : p) VI_SLANG.put(x[0], x[1]);
    }

    /** Từ game / viết tắt quốc tế: giữ nguyên, không đưa vào bước khôi phục dấu. */
    private static final java.util.Set<String> KEEP_WORDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "gg", "ez", "wp", "gl", "hf", "bed", "bw", "rush", "pls", "plz", "afk", "lol", "ping", "lag",
            "noob", "pro", "team", "final", "kill", "void", "diamond", "emerald", "iron", "gold", "ok", "oke",
            "xp", "op", "hp", "pvp", "gm", "brb", "wtf", "omg", "fps", "dm", "tp", "bridge", "invis",
            "minecraft", "hypixel", "bedwars", "skywars", "discord", "youtube", "tiktok", "facebook", "server"));

    /** Từ tiếng Anh phổ biến (đã loại các từ trùng tiếng Việt không dấu như me, do, to, no, can, day, may...). */
    private static final java.util.Set<String> EN_COMMON = new java.util.HashSet<String>(java.util.Arrays.asList(
            "the", "is", "are", "was", "were", "you", "your", "we", "they", "it", "this", "that", "what", "why",
            "how", "who", "where", "when", "have", "has", "with", "for", "and", "but", "not", "will", "would",
            "just", "please", "thanks", "thank", "hello", "hi", "hey", "yes", "go", "get", "come", "need", "want",
            "know", "good", "bad", "nice", "base", "attack", "defend", "dont", "im", "i", "my", "our", "he", "she",
            "of", "on", "at", "from", "there", "here", "now", "up", "down", "help", "stop", "wait", "come", "lets"));

    private static String[] splitWords(String s) {
        return s.trim().isEmpty() ? new String[0] : s.trim().split("\\s+");
    }

    /** Tin không dấu nhưng thật ra là tiếng Anh -> bỏ qua khôi phục dấu, để Google tự nhận diện. */
    private static boolean looksEnglish(String text) {
        if (VI_TONE.matcher(text).find()) return false;
        String[] t = splitWords(text);
        if (t.length == 0) return false;
        int en = 0, keep = 0;
        for (String w : t) {
            String l = w.toLowerCase().replaceAll("[^\\p{L}']", "");
            if (EN_COMMON.contains(l)) en++;
            else if (KEEP_WORDS.contains(l)) keep++;
        }
        if (en + keep == t.length) return true;       // toàn từ Anh / từ game
        return t.length >= 2 && en * 10 >= t.length * 6; // >= 60% là từ Anh phổ biến
    }

    private static boolean needsRestore(String s) {
        return !VI_TONE.matcher(s).find() && s.matches(".*\\p{L}.*");
    }

    private static String expandSlang(String s) {
        Matcher m = WORD_TOKEN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String w = m.group().toLowerCase();
            String rep = KEEP_WORDS.contains(w) ? m.group() : VI_SLANG.containsKey(w) ? VI_SLANG.get(w) : m.group();
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString().replace('đ', 'd').replace('Đ', 'D'); // để Input Tools tự đoán lại đ/d theo ngữ cảnh
    }

    /** Khôi phục dấu qua Google Input Tools. Lỗi / kết quả lạ thì trả về nguyên chuỗi vào. */
    private static String restoreDiacritics(String text) {
        loadCache();
        final String key = "restore\u0000" + text;
        String[] hit = cacheGet(key);
        if (hit != null) return hit[0];
        if (System.currentTimeMillis() < restoreFailUntil) return text;
        try {
            String url = "https://inputtools.google.com/request?itc=vi-t-i0-und&num=1&cp=0&cs=1&ie=utf-8&oe=utf-8"
                    + "&app=demopage&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
            JsonArray root = JsonParser.parseString(httpGet(url, 2000)).getAsJsonArray();
            if (!"SUCCESS".equals(root.get(0).getAsString())) return text;
            String best = root.get(1).getAsJsonArray().get(0).getAsJsonArray()
                    .get(1).getAsJsonArray().get(0).getAsString().trim();
            if (best.isEmpty() || best.length() > text.length() * 2 + 4) return text;

            // Giữ nguyên từ game (rush, bed, gg...) nếu số từ trước/sau khớp nhau
            String[] in = splitWords(text), out = splitWords(best);
            if (in.length == out.length) {
                for (int i = 0; i < in.length; i++) {
                    if (KEEP_WORDS.contains(in[i].toLowerCase())) out[i] = in[i];
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < out.length; i++) sb.append(i > 0 ? " " : "").append(out[i]);
                best = sb.toString();
            }
            cachePut(key, new String[]{best, ""});
            return best;
        } catch (Exception ex) {
            restoreFailUntil = System.currentTimeMillis() + 30000L; // lỗi: 30s sau mới thử lại
            return text;
        }
    }

    /** Chuẩn hoá tin tiếng Việt trước khi dịch: không dấu -> mở rộng viết tắt -> khôi phục dấu. */
    private static String normalizeVi(String text) {
        if (!needsRestore(text)) return text;
        String decoded = telexDecode(text);
        if (!decoded.equals(text)) {
            // có gõ kiểu Telex ("ddang baor veej giuowng"): giải mã chỗ đã gõ dấu, rồi nhờ Input Tools bù dấu còn thiếu
            String restored = restoreDiacritics(expandSlang(stripMarks(decoded)));
            return mergeMarks(decoded, restored);
        }
        return restoreDiacritics(expandSlang(text));
    }

    // ===== Telex: "ddang baor veej giuowng" -> "đang bảo vệ giương" (dấu thanh còn thiếu do Input Tools bù) =====
    private static final String[] TONE_ROWS = {"aáàảãạ", "ăắằẳẵặ", "âấầẩẫậ", "eéèẻẽẹ", "êếềểễệ", "iíìỉĩị",
            "oóòỏõọ", "ôốồổỗộ", "ơớờởỡợ", "uúùủũụ", "ưứừửữự", "yýỳỷỹỵ"};
    private static final String VI_VOWELS = "aăâeêioôơuưy";
    private static final Pattern FINAL_CONS = Pattern.compile("c|ch|m|n|ng|nh|p|t|");

    private static char withTone(char v, int tone) {
        for (String row : TONE_ROWS) if (row.charAt(0) == v) return row.charAt(tone);
        return v;
    }

    /** Ký tự thứ i có phải nguyên âm không ("u" sau "q" và "i" trong "gi" + nguyên âm là phụ âm). */
    private static boolean isVowelAt(String s, int i) {
        char c = s.charAt(i);
        if (VI_VOWELS.indexOf(c) < 0) return false;
        if (c == 'u' && i > 0 && s.charAt(i - 1) == 'q') return false;
        if (c == 'i' && i > 0 && s.charAt(i - 1) == 'g' && i + 1 < s.length() && VI_VOWELS.indexOf(s.charAt(i + 1)) >= 0) return false;
        return true;
    }

    /** Giải mã một từ Telex: dd->đ, aa/ee/oo->â/ê/ô, aw/ow/uw/uow->ă/ơ/ư/ươ, s f r x j -> sắc huyền hỏi ngã nặng. */
    private static String telexWord(String w) {
        String s = w.toLowerCase();
        if (KEEP_WORDS.contains(s) || EN_COMMON.contains(s) || VI_SLANG.containsKey(s)) return w;
        s = s.replace("dd", "đ");
        int tone = 0;
        for (int k = 1; k < s.length() && tone == 0; k++) {
            int t = "sfrxj".indexOf(s.charAt(k)) + 1;
            if (t == 0) continue;
            boolean vowelBefore = false;
            for (int i = 0; i < k; i++) if (isVowelAt(s, i) || s.charAt(i) == 'w') vowelBefore = true;
            // phím dấu phải đứng sau nguyên âm và chỉ còn phụ âm cuối (c, ch, m, n, ng, nh, p, t) phía sau
            if (vowelBefore && FINAL_CONS.matcher(s.substring(k + 1)).matches()) {
                tone = t;
                s = s.substring(0, k) + s.substring(k + 1);
            }
        }
        s = s.replace("uwow", "ươ").replace("uow", "ươ");
        s = s.replace("aa", "â").replace("ee", "ê").replace("oo", "ô")
                .replace("aw", "ă").replace("ow", "ơ").replace("uw", "ư").replace('w', 'ư');
        if (tone > 0) {
            int a = -1, b = -1;
            for (int i = 0; i < s.length(); i++) {
                if (isVowelAt(s, i)) { if (a < 0) a = i; b = i; }
                else if (a >= 0) break;
            }
            if (a >= 0) {
                int pick = -1;
                for (int i = b; i >= a; i--) {
                    if ("ăâêôơư".indexOf(s.charAt(i)) >= 0) { pick = i; break; }
                }
                if (pick < 0) {
                    int len = b - a + 1;
                    if (len == 1) pick = a;
                    else if (len == 2) {
                        String pair = s.substring(a, b + 1);
                        boolean closed = b < s.length() - 1;
                        pick = (closed || pair.equals("oa") || pair.equals("oe") || pair.equals("uy")) ? b : a;
                    } else pick = a + 1;
                }
                char[] cs = s.toCharArray();
                cs[pick] = withTone(cs[pick], tone);
                s = new String(cs);
            }
        }
        if (s.equals(w.toLowerCase())) return w; // không có gì để giải mã
        if (Character.isUpperCase(w.charAt(0))) s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return s;
    }

    private static String telexDecode(String text) {
        Matcher m = WORD_TOKEN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(telexWord(m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    /** Bỏ toàn bộ dấu (kể cả â ê ô ơ ư ă đ). */
    private static String stripMarks(String s) {
        String d = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return d.replace('đ', 'd').replace('Đ', 'D');
    }

    /** Bỏ dấu thanh, giữ dấu mũ / móc / trăng và đ ("giường" -> "giương"). */
    private static String shape(String s) {
        String d = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("[\\u0300\\u0301\\u0309\\u0303\\u0323]", "");
        return java.text.Normalizer.normalize(d, java.text.Normalizer.Form.NFC);
    }

    /** Ghép: từ nào bạn đã gõ đủ dấu thì giữ, còn thiếu dấu thanh / dấu mũ thì lấy bản Input Tools đoán. */
    private static String mergeMarks(String decoded, String restored) {
        String[] d = splitWords(decoded), r = splitWords(restored);
        if (d.length != r.length) return restored; // viết tắt làm đổi số từ: dùng bản bù dấu
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            String dw = d[i], rw = r[i];
            boolean hasTone = !shape(dw).equals(dw);
            boolean hasMark = !stripMarks(dw).equals(dw);
            String pick = shape(rw).equals(shape(dw)) ? (hasTone ? dw : rw) : (hasMark ? dw : rw);
            sb.append(i > 0 ? " " : "").append(pick);
        }
        return sb.toString();
    }

    /** Cắt bản dịch thành tối đa 3 tin <= 100 ký tự, ưu tiên cắt ở dấu cách để không đứt giữa từ. */
    private static java.util.List<String> splitChat(String s) {
        java.util.List<String> parts = new java.util.ArrayList<String>();
        s = s.trim();
        while (s.length() > 100 && parts.size() < 2) {
            int cut = s.lastIndexOf(' ', 100);
            if (cut < 40) cut = 100;
            parts.add(s.substring(0, cut).trim());
            s = s.substring(cut).trim();
        }
        if (s.length() > 100) s = s.substring(0, 100);
        if (!s.isEmpty()) parts.add(s);
        return parts;
    }
}
