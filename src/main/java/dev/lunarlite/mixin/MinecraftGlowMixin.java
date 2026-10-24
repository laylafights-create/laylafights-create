package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enemy Outline: bắt game vẽ viền "glowing" (silhouette xuyên tường) cho các mục tiêu của Enemy Detector. */
@Mixin(Minecraft.class)
public abstract class MinecraftGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        var d = LunarLite.ENEMY;
        if (d != null && d.glowWanted(entity)) cir.setReturnValue(true);
    }
}
