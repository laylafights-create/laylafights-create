package dev.lunarlite.module;

import dev.lunarlite.gui.Draw;
import dev.lunarlite.setting.BoolSetting;
import dev.lunarlite.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorModule extends HudModule {
    private static final int ROW = 18;

    private final BoolSetting showHeld = add(new BoolSetting("show_held", "Show Held Item", true));
    private final ModeSetting durability = add(new ModeSetting("durability", "Durability", 0, "Number", "Percent", "Off"));
    private final ModeSetting durColor = add(new ModeSetting("dur_color", "Durability Color", 0, "Gradient", "Custom"));

    public ArmorModule() {
        super("armor", "Armor Status", "Giáp đang mặc và vật phẩm cầm trên tay, kèm độ bền.", Category.PVP, true, 400, 4);
    }

    private int slots() { return showHeld.value ? 5 : 4; }

    @Override public int getWidth(boolean preview) { return durability.is("Off") ? 20 : 20 + 34; }
    @Override public int getHeight(boolean preview) { return ROW * slots() - 2; }

    private ItemStack[] stacks(boolean preview) {
        ItemStack[] out = new ItemStack[5];
        for (int i = 0; i < out.length; i++) out[i] = ItemStack.EMPTY;
        if (preview) {
            out[0] = new ItemStack(Items.DIAMOND_SWORD);
            out[1] = new ItemStack(Items.DIAMOND_HELMET);
            out[2] = new ItemStack(Items.DIAMOND_CHESTPLATE);
            out[3] = new ItemStack(Items.DIAMOND_LEGGINGS);
            out[4] = new ItemStack(Items.DIAMOND_BOOTS);
            return out;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return out;
        out[0] = p.getMainHandItem();
        out[1] = p.getItemBySlot(EquipmentSlot.HEAD);
        out[2] = p.getItemBySlot(EquipmentSlot.CHEST);
        out[3] = p.getItemBySlot(EquipmentSlot.LEGS);
        out[4] = p.getItemBySlot(EquipmentSlot.FEET);
        return out;
    }

    @Override
    protected void draw(int x, int y, boolean preview) {
        ItemStack[] items = stacks(preview);
        drawBox(x - 2, y - 2, getWidth(preview) + 4, getHeight(preview) + 4);
        int row = 0;
        for (int i = showHeld.value ? 0 : 1; i < items.length; i++, row++) {
            ItemStack s = items[i];
            if (s.isEmpty()) continue;
            int iy = y + row * ROW;
            Draw.rrect(x - 1, iy - 1, x + getWidth(preview) + 1, iy + 16, 3, 0x1AFFFFFF);
            Draw.item(s, x, iy);

            boolean dmgable = s.isDamageableItem();
            String text = null;
            float frac = 1f;
            if (dmgable && !durability.is("Off")) {
                int left = s.getMaxDamage() - s.getDamageValue();
                frac = Math.max(0f, (float) left / s.getMaxDamage());
                text = durability.is("Percent") ? Math.round(frac * 100f) + "%" : String.valueOf(left);
            } else if (!dmgable && s.getCount() > 1) {
                text = String.valueOf(s.getCount());
            }
            if (text != null) {
                int base = durColor.is("Gradient") && dmgable
                        ? (java.awt.Color.HSBtoRGB(frac / 3f, 1f, 1f) & 0xFFFFFF) : color.rgb();
                boolean dmg = dmgable && !durability.is("Off");
                rainbow.drawString(text, x + 20, iy + (dmg ? 2 : 5), base, shadow.value, iy);
                if (dmg) {
                    int bc = durColor.is("Gradient") ? Draw.lerp(0xFFFF5C5C, 0xFF5CE08A, frac) : (0xFF000000 | color.rgb());
                    Draw.hbar(x + 20, iy + 12, 30, 2, frac, bc, 0x66000000);
                }
            }
        }
    }
}
