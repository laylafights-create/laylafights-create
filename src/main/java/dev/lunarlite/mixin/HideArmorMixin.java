package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class HideArmorMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void zhwii$hideArmor(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        if (LunarLite.HIDE_ARMOR != null && LunarLite.HIDE_ARMOR.applies(entity)) LunarLite.HIDE_ARMOR.strip(state);
    }
}
