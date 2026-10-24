package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enemy Outline: màu viền glowing của mục tiêu (rainbow / gần nhất / màu chọn). */
@Mixin(Entity.class)
public abstract class EntityGlowColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$color(CallbackInfoReturnable<Integer> cir) {
        var d = LunarLite.ENEMY;
        if (d == null) return;
        int rgb = d.glowRgb((Entity) (Object) this);
        if (rgb >= 0) cir.setReturnValue(rgb);
    }
}
