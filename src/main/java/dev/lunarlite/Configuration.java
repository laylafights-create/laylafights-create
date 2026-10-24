package dev.lunarlite;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cấu hình dạng văn bản (.cfg) thay cho {@code net.minecraftforge.common.config.Configuration} của Forge 1.8.9.
 * Giữ nguyên các hàm mà module / setting đang dùng nên code cũ gần như không phải sửa.
 *
 * Định dạng:
 * <pre>
 * [fps]
 * enabled=true
 * x=4
 * </pre>
 */
public class Configuration {
    /** Giá trị của một khoá; chỉ cần {@link #set(Object)}. */
    public static final class Prop {
        private final Map<String, String> cat;
        private final String key;

        Prop(Map<String, String> cat, String key) {
            this.cat = cat;
            this.key = key;
        }

        public Prop set(Object v) {
            cat.put(key, String.valueOf(v));
            return this;
        }
    }

    private final File file;
    private final Map<String, Map<String, String>> data = new LinkedHashMap<>();

    public Configuration(File file) {
        this.file = file;
    }

    private Map<String, String> cat(String name) {
        return data.computeIfAbsent(name, k -> new LinkedHashMap<>());
    }

    public void load() {
        data.clear();
        if (file == null || !file.isFile()) return;
        try (BufferedReader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line, cur = null;
            while ((line = r.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    cur = line.substring(1, line.length() - 1);
                    cat(cur);
                } else if (cur != null) {
                    int eq = line.indexOf('=');
                    if (eq > 0) cat(cur).put(line.substring(0, eq).strip(), line.substring(eq + 1).strip());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save() {
        if (file == null) return;
        StringBuilder sb = new StringBuilder("# Zhwii Client\n");
        for (Map.Entry<String, Map<String, String>> c : data.entrySet()) {
            sb.append('\n').append('[').append(c.getKey()).append("]\n");
            for (Map.Entry<String, String> e : c.getValue().entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue().replace('\n', ' ')).append('\n');
            }
        }
        try {
            File parent = file.getAbsoluteFile().getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<String> getCategoryNames() {
        return new LinkedHashSet<>(data.keySet());
    }

    /** Tìm tên mục không phân biệt hoa thường (file hồ sơ cũ từ Forge có thể hạ chữ thường). */
    private Map<String, String> find(String name) {
        Map<String, String> m = data.get(name);
        if (m != null) return m;
        for (Map.Entry<String, Map<String, String>> e : data.entrySet()) {
            if (e.getKey().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) return e.getValue();
        }
        return null;
    }

    private String raw(String key, String category) {
        Map<String, String> m = find(category);
        return m == null ? null : m.get(key);
    }

    public boolean getBoolean(String key, String category, boolean def, String comment) {
        String s = raw(key, category);
        if (s == null) return def;
        return s.equalsIgnoreCase("true") ? true : s.equalsIgnoreCase("false") ? false : def;
    }

    public int getInt(String key, String category, int def, int min, int max, String comment) {
        String s = raw(key, category);
        if (s == null) return def;
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(s)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public float getFloat(String key, String category, float def, float min, float max, String comment) {
        String s = raw(key, category);
        if (s == null) return def;
        try {
            return Math.max(min, Math.min(max, Float.parseFloat(s)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public String getString(String key, String category, String def, String comment) {
        String s = raw(key, category);
        return s == null ? def : s;
    }

    /** Lấy (hoặc tạo) một khoá để ghi giá trị bằng {@link Prop#set(Object)}. */
    public Prop get(String category, String key, Object def) {
        Map<String, String> m = cat(category);
        if (!m.containsKey(key)) m.put(key, String.valueOf(def));
        return new Prop(m, key);
    }
}
