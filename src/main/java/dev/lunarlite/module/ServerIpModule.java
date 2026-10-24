package dev.lunarlite.module;

import dev.lunarlite.setting.BoolSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/** Địa chỉ server đang chơi. (Bản 26.2 chưa vẽ icon favicon của server.) */
public class ServerIpModule extends TextHudModule {
    private final BoolSetting hide = add(new BoolSetting("hide", "Hide Address (Streamer)", false));

    public ServerIpModule() {
        super("serverip", "Server Address", "Địa chỉ server đang chơi.", Category.HUD, true, 4, 244);
    }

    @Override
    protected String[] getLines(boolean preview) {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        String ip = sd == null ? (preview ? "play.example.net" : "Singleplayer") : sd.ip;
        if (hide.value && sd != null) ip = "********";
        return new String[] { ip };
    }
}
