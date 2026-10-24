package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Zoom: khi đang zoom, lăn chuột chỉnh mức zoom thay vì đổi ô hotbar. Sai tên hàm thì chỉ mất tính năng lăn chuột. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$zoomScroll(long window, double xoffset, double yoffset, CallbackInfo ci) {
        if (LunarLite.ZOOM != null && LunarLite.ZOOM.handleScroll(yoffset)) ci.cancel();
    }
}
