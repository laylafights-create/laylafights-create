package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Ẩn giáp của bản thân: chỉ đổi cách VẼ nhân vật của bạn (client-side). Giáp vẫn mặc, chỉ số / độ bền không đổi.
 * Cách làm 26.2: mixin vào {@code LivingEntityRenderer#extractRenderState}; sau khi trạng thái vẽ của người chơi
 * local được trích xuất thì xoá các ô trang bị giáp trong render state (không đụng tới inventory thật).
 */
public class HideArmorModule extends Module {
    private final BoolSetting helmet = add(new BoolSetting("hide_helmet", "Hide Helmet", true));
    private final BoolSetting chest = add(new BoolSetting("hide_chest", "Hide Chestplate", true));
    private final BoolSetting legs = add(new BoolSetting("hide_legs", "Hide Leggings", true));
    private final BoolSetting boots = add(new BoolSetting("hide_boots", "Hide Boots", true));
    private final BoolSetting keepInGui = add(new BoolSetting("keep_in_gui", "Show In Inventory Preview", true));

    public HideArmorModule() {
        super("hide_armor", "Hide Armor", "Ẩn giáp trên nhân vật của bạn (chỉ hiển thị phía bạn, không ảnh hưởng chỉ số giáp).",
                Category.VISUAL, false);
    }

    /** Có nên xoá giáp khỏi render state của {@code entity} không. */
    public boolean applies(Object entity) {
        if (!enabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) return false;
        // khung xem nhân vật trong kho đồ cũng đi qua đây; giữ nguyên giáp ở đó nếu được bật
        return !(keepInGui.value && dev.lunarlite.Compat.screen() != null);
    }

    private static final String[] FIELDS = {"headEquipment", "chestEquipment", "legsEquipment", "feetEquipment"};
    private static Field[] cache;
    private static Class<?> cacheFor;

    /** Xoá ô giáp trong {@code state} (reflection nên không vỡ build nếu 26.2 đổi tên field). */
    public void strip(Object state) {
        try {
            if (cacheFor != state.getClass()) {
                Field[] fs = new Field[4];
                for (int i = 0; i < 4; i++) {
                    for (Class<?> c = state.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                        try {
                            Field f = c.getDeclaredField(FIELDS[i]);
                            if (f.getType() == ItemStack.class) { f.setAccessible(true); fs[i] = f; }
                            break;
                        } catch (NoSuchFieldException ignored) { }
                    }
                }
                cache = fs;
                cacheFor = state.getClass();
            }
            boolean[] hide = {helmet.value, chest.value, legs.value, boots.value};
            for (int i = 0; i < 4; i++) {
                if (cache[i] != null && hide[i]) cache[i].set(state, ItemStack.EMPTY);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }
}
