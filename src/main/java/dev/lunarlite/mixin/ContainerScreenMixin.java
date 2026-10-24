package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Animation kho đồ của Hotbar Animation: vẽ khung chọn trượt + item phóng to đè lên sau khi vanilla vẽ xong.
 * {@code require = 0}: sai tên hàm thì chỉ mất animation kho, không crash.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void zhwii$invAnim(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        var h = LunarLite.HOTBAR;
        var self = (AbstractContainerScreen<?>) (Object) this;
        if (h != null && h.shouldRenderContainer(self)) {
            try {
                h.renderContainer(self, g, mouseX, mouseY);
            } catch (Throwable ignored) { }
        }
    }
}
