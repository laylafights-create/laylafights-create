package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bỏ nền tối (và blur) của các màn hình container. Mọi inject đều {@code require = 0}: sai tên thì bỏ qua, không crash. */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$noBg(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (zhwii$clean()) ci.cancel();
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$noTransparentBg(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (zhwii$clean()) ci.cancel();
    }

    private boolean zhwii$clean() {
        return LunarLite.CLEAN != null && LunarLite.CLEAN.enabled && (Object) this instanceof AbstractContainerScreen<?>;
    }
}
