package dev.lunarlite.mixin;

import dev.lunarlite.gui.ChatView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat Bar: (1) bắt mọi tin vào chat để khung chat riêng ({@link ChatView}) vẽ; (2) ẩn chat vanilla khi khung riêng đã
 * hoạt động. Mọi inject {@code require = 0}: sai tên / chữ ký ở 26.2 thì không có tác dụng và chat vanilla vẫn hiện
 * như cũ (chỉ ẩn vanilla sau khi đã bắt được tin đầu tiên nên không bao giờ mất chat).
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent")
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), require = 0)
    private void zhwii$add1(@Coerce Object msg, CallbackInfo ci) {
        ChatView.capture(msg);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;"
            + "Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), require = 0)
    private void zhwii$add3(@Coerce Object msg, @Coerce Object sig, @Coerce Object tag, CallbackInfo ci) {
        ChatView.capture(msg);
    }

    @Inject(method = {"extractRenderState", "render"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$hide(CallbackInfo ci) {
        if (ChatView.takeover()) {
            ChatView.markSuppressed();
            ci.cancel();
        }
    }
}
