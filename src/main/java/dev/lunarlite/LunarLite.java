package dev.lunarlite;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lunarlite.gui.AnimeHit;
import dev.lunarlite.gui.Draw;
import dev.lunarlite.gui.HudEditorScreen;
import dev.lunarlite.gui.MenuScreen;
import dev.lunarlite.gui.Proj;
import dev.lunarlite.module.BlockOutlineModule;
import dev.lunarlite.module.BowTrajectoryModule;
import dev.lunarlite.module.EnemyDetectorModule;
import dev.lunarlite.module.AimAssistModule;
import dev.lunarlite.module.AnimationsModule;
import dev.lunarlite.module.ArmorModule;
import dev.lunarlite.module.BridgeModule;
import dev.lunarlite.module.ChatBarModule;
import dev.lunarlite.module.HitEffectModule;
import dev.lunarlite.module.CleanGuisModule;
import dev.lunarlite.module.HideArmorModule;
import dev.lunarlite.module.HotbarAnimationModule;
import dev.lunarlite.module.NoBlockParticlesModule;
import dev.lunarlite.module.KillBoardModule;
import dev.lunarlite.module.ItemPickupModule;
import dev.lunarlite.module.ComboModule;
import dev.lunarlite.module.CpsModule;
import dev.lunarlite.module.FpsModule;
import dev.lunarlite.module.FullbrightModule;
import dev.lunarlite.module.HudModule;
import dev.lunarlite.module.KeystrokesModule;
import dev.lunarlite.module.Module;
import dev.lunarlite.module.PingModule;
import dev.lunarlite.module.PotionModule;
import dev.lunarlite.module.ScoreboardModule;
import dev.lunarlite.module.VelocityModule;
import dev.lunarlite.module.ServerIpModule;
import dev.lunarlite.module.TargetInfoModule;
import dev.lunarlite.module.ThemeModule;
import dev.lunarlite.module.ToggleSprintModule;
import dev.lunarlite.module.ZoomModule;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

/**
 * Zhwii Client cho Minecraft 26.2 (Fabric). Bản port từ Forge 1.8.9 — xem README để biết phần đã / chưa port.
 * (Tên lớp giữ nguyên là LunarLite để khớp với bản gốc.)
 */
public class LunarLite implements ClientModInitializer {
    public static final String MODID = "zhwii";
    public static final String NAME = "Zhwii Client";
    public static final String VERSION = "7.0.0";

    public static final List<Module> MODULES = new ArrayList<>();
    public static KeyMapping keyMenu, keyZoom;

    public static ZoomModule ZOOM;
    public static AnimationsModule ANIM;
    public static FullbrightModule FULLBRIGHT;
    public static AimAssistModule AIM;
    public static BridgeModule BRIDGE;
    public static HotbarAnimationModule HOTBAR;
    public static HitEffectModule HIT;
    public static BowTrajectoryModule BOW;
    public static BlockOutlineModule OUTLINE;
    public static EnemyDetectorModule ENEMY;
    public static HideArmorModule HIDE_ARMOR;
    public static CleanGuisModule CLEAN;
    public static NoBlockParticlesModule NOPART;

    private static Configuration config;
    private static File configFile;

    @Override
    public void onInitializeClient() {
        configFile = FabricLoader.getInstance().getConfigDir().resolve("zhwii.cfg").toFile();
        config = new Configuration(configFile);

        ZOOM = new ZoomModule();
        ANIM = new AnimationsModule();
        FULLBRIGHT = new FullbrightModule();
        AIM = new AimAssistModule();
        BRIDGE = new BridgeModule();
        HOTBAR = new HotbarAnimationModule();
        HIT = new HitEffectModule();
        BOW = new BowTrajectoryModule();
        OUTLINE = new BlockOutlineModule();
        ENEMY = new EnemyDetectorModule();
        HIDE_ARMOR = new HideArmorModule();
        CLEAN = new CleanGuisModule();
        NOPART = new NoBlockParticlesModule();

        // HUD
        MODULES.add(new FpsModule());
        MODULES.add(new PingModule());
        MODULES.add(new ServerIpModule());
        MODULES.add(new ScoreboardModule());
        MODULES.add(new ItemPickupModule());
        // PvP
        MODULES.add(new CpsModule());
        MODULES.add(new KeystrokesModule());
        MODULES.add(new TargetInfoModule());
        MODULES.add(new ComboModule());
        MODULES.add(new ArmorModule());
        MODULES.add(new PotionModule());
        MODULES.add(AIM);
        MODULES.add(new VelocityModule());
        MODULES.add(BRIDGE);
        MODULES.add(new KillBoardModule());
        MODULES.add(HIT);
        MODULES.add(ENEMY);
        MODULES.add(BOW);
        MODULES.add(HOTBAR);
        MODULES.add(new ChatBarModule());
        // Hình ảnh / Tiện ích
        MODULES.add(new ToggleSprintModule());
        MODULES.add(ZOOM);
        MODULES.add(FULLBRIGHT);
        MODULES.add(OUTLINE);
        MODULES.add(ANIM);
        MODULES.add(HIDE_ARMOR);
        MODULES.add(CLEAN);
        MODULES.add(NOPART);
        MODULES.add(new ThemeModule());

        try {
            config.load();
        } catch (RuntimeException e) {
            // file hỏng: dùng mặc định
        }
        for (Module m : MODULES) m.load(config);
        loadGeneral(config);
        saveConfig();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MODID, "main"));
        keyMenu = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.zhwii.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, category));
        keyZoom = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.zhwii.zoom", InputConstants.Type.KEYSYM, InputConstants.KEY_C, category));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MODID, "hud"), (graphics, deltaTracker) -> {
                    Draw.begin(graphics);
                    float pt = deltaTracker.getGameTimeDeltaPartialTick(false);
                    if (AIM != null) AIM.onRender(pt);
                    if (BRIDGE != null) BRIDGE.onRender(pt);
                    renderHud();
                    Minecraft cm = Minecraft.getInstance();
                    if (cm.player != null && !Compat.hideGui()) {
                        dev.lunarlite.gui.ChatView.render(cm.getWindow().getGuiScaledWidth(), cm.getWindow().getGuiScaledHeight());
                    }
                });
        // Hotbar animation: thay hotbar vanilla khi module bật
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (graphics, deltaTracker) -> {
            if (HOTBAR != null && HOTBAR.shouldRender()) {
                Draw.begin(graphics);
                Minecraft m = Minecraft.getInstance();
                HOTBAR.render(m.getWindow().getGuiScaledWidth(), m.getWindow().getGuiScaledHeight());
            } else {
                vanilla.extractRenderState(graphics, deltaTracker);
            }
        });
        // Kill Board: ẩn bossbar
        HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> (graphics, deltaTracker) -> {
            if (!KillBoardModule.hideBoss()) vanilla.extractRenderState(graphics, deltaTracker);
        });
        // Ẩn sidebar scoreboard của server khi module "Ẩn Scoreboard" bật
        HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (graphics, deltaTracker) -> {
            if (!ScoreboardModule.hidden()) vanilla.extractRenderState(graphics, deltaTracker);
        });

        ClientTickEvents.END_CLIENT_TICK.register(LunarLite::onTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (ZOOM != null) ZOOM.restore();
            if (FULLBRIGHT != null && FULLBRIGHT.enabled) { FULLBRIGHT.enabled = false; FULLBRIGHT.onTick(); }
            saveConfig();
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player == Minecraft.getInstance().player) {
                if (entity instanceof net.minecraft.world.entity.LivingEntity) ComboModule.onAttack();
                if (HIT != null) HIT.onAttack(entity);
                TargetInfoModule.onAttack(entity);
            }
            return InteractionResult.PASS;
        });
    }

    private static void loadGeneral(Configuration c) {
        SaoFx.deathFx = c.getBoolean("deathFx", "general", true, "Hiệu ứng chết kiểu Sword Art Online");
        SaoFx.shatterFx = c.getBoolean("shatterFx", "general", true, "Sinh vật vỡ thành mảnh khi chết kiểu SAO");
    }

    // ------------------------------------------------------------------ vòng lặp

    private static void onTick(Minecraft mc) {
        CpsModule.poll();
        SaoFx.onTick(mc);
        if (mc.player == null) return;
        if (keyMenu != null) {
            while (keyMenu.consumeClick()) {
                if (Compat.screen() == null) Compat.setScreen(new MenuScreen());
            }
        }
        for (Module m : MODULES) m.onTick();
    }

    private static void renderHud() {
        Minecraft mc = Minecraft.getInstance();
        CpsModule.poll();
        if (ZOOM != null) ZOOM.frame();
        if (mc.player == null || Compat.hideGui()) return;
        if (Compat.screen() instanceof HudEditorScreen) return;
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (Proj.setup(pt, w, h)) {
            if (OUTLINE != null) OUTLINE.overlay(pt);
            if (BOW != null) BOW.overlay(pt);
            if (ENEMY != null) ENEMY.overlay(pt);
            dev.lunarlite.gui.SaoShatter.render();
        }
        AnimeHit.render(pt, w, h);
        for (Module m : MODULES) {
            if (m.enabled && m instanceof HudModule hud) hud.render(w, h, false);
        }
        if (dev.lunarlite.gui.SaoDeath.active() && !(Compat.screen() instanceof dev.lunarlite.gui.SaoDeathScreen)) {
            dev.lunarlite.gui.SaoDeath.render(w, h, 0, 0, false);
        }
    }

    // ------------------------------------------------------------------ config

    /** Thư mục config của game (nơi chứa file config chính và thư mục hồ sơ). */
    public static File configDir() {
        File dir = configFile == null ? null : configFile.getParentFile();
        return dir != null ? dir : new File("config");
    }

    /** Ghi toàn bộ cài đặt hiện tại vào {@code c} (không tự lưu file). */
    public static void fill(Configuration c) {
        for (Module m : MODULES) m.save(c);
        c.get("general", "deathFx", true).set(SaoFx.deathFx);
        c.get("general", "shatterFx", true).set(SaoFx.shatterFx);
    }

    public static void saveConfig() {
        if (config == null) return;
        try {
            fill(config);
            config.save();
        } catch (RuntimeException e) {
            // không ghi được file: bỏ qua, thử lại lần sau
        }
    }

    /**
     * Nạp cài đặt từ {@code c} (đã load). Chỉ tính năng có mặt trong {@code c} mới bị ghi đè.
     * Sau đó lưu vào config chính. Trả về số tính năng đã nạp.
     */
    public static int applyConfig(Configuration c) {
        Set<String> names = c.getCategoryNames();
        int n = 0;
        for (Module m : MODULES) {
            if (!ProfileManager.has(names, m.id)) continue;
            m.load(c);
            n++;
        }
        saveConfig();
        return n;
    }
}
