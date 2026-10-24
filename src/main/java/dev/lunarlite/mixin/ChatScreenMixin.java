package dev.lunarlite.mixin;

import dev.lunarlite.gui.ChatView;
import dev.lunarlite.gui.Draw;
import dev.lunarlite.module.ChatBarModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Nút đổi hướng dịch tin gửi (VI>EN / EN>VI) nằm bên phải, NGOÀI ô nhập chat; ô nhập được thu hẹp để chừa chỗ.
 * Mọi inject đều {@code require = 0}: sai tên ở 26.2 thì mất nút, không crash.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow protected EditBox input;

    private static boolean zhwii$on() {
        ChatBarModule m = ChatBarModule.instance();
        return m != null && m.enabled && ChatBarModule.TRANSLATE_OUT.value;
    }

    private static int zhwii$bw() { return Draw.width(ChatBarModule.dirLabel()) + 10; }

    private static boolean zhwii$bar() {
        ChatBarModule m = ChatBarModule.instance();
        return m != null && m.enabled;
    }

    private void zhwii$layout() {
        if (input == null) return;
        Screen s = (Screen) (Object) this;
        int rs = zhwii$on() ? zhwii$bw() + 4 : 0;
        int full = s.width - 4 - rs;
        int want = ChatBarModule.BAR_WIDTH.getInt();
        input.setWidth(zhwii$bar() && want > 0 ? Math.max(140, Math.min(full, want)) : full);
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void zhwii$init(CallbackInfo ci) {
        if (!zhwii$bar()) return;
        zhwii$layout();
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void zhwii$removed(CallbackInfo ci) {
        ChatView.resetScroll();
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$scroll(double x, double y, double sx, double sy, CallbackInfoReturnable<Boolean> cir) {
        if (zhwii$bar() && ChatView.takeover()) {
            ChatView.scroll(sy);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void zhwii$draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Draw.begin(g);
        if (zhwii$bar() && input != null) {
            int x1 = input.getX() - 2, y1 = input.getY() - 2, x2 = input.getX() + input.getWidth() + 2, y2 = input.getY() + input.getHeight() + 2;
            if (ChatBarModule.BAR_BORDER.value) {
                Draw.ring(x1, y1, x2, y2, Draw.withAlpha(Draw.ACCENT & 0xFFFFFF, 0xCC));
            }
            if (ChatBarModule.CHAR_COUNTER.value) {
                int n = input.getValue().length();
                String t = n + "/100";
                Draw.text(t, x2 - Draw.width(t) - 4, y1 - 10, n > 100 ? 0xFFFF5555 : 0xFFAAAAAA, true);
            }
        }
        if (!zhwii$on()) return;
        Screen s = (Screen) (Object) this;
        int bw = zhwii$bw(), x = s.width - bw - 2, y = s.height - 14;
        boolean hov = Draw.in(mouseX, mouseY, x, y, bw, 12);
        Draw.rrect(x, y, x + bw, y + 12, 3, hov ? 0xC8FFFFFF & 0xFF55AAFF : 0xB0000000);
        Draw.outline(x, y, x + bw, y + 12, hov ? 0xFFFFFFFF : 0xFF55AAFF);
        Draw.text(ChatBarModule.dirLabel(), x + 5, y + 2, 0xFFFFFFFF, true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$click(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!zhwii$on() || event.button() != 0) return;
        Screen s = (Screen) (Object) this;
        int bw = zhwii$bw(), x = s.width - bw - 2, y = s.height - 14;
        if (Draw.in((int) event.x(), (int) event.y(), x, y, bw, 12)) {
            ChatBarModule.toggleDir();
            zhwii$layout();
            cir.setReturnValue(true);
        }
    }
}
