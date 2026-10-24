package dev.lunarlite.mixin;

import dev.lunarlite.LunarLite;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$noDestroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (LunarLite.NOPART != null && LunarLite.NOPART.enabled) ci.cancel();
    }

    @Inject(method = "crack", at = @At("HEAD"), cancellable = true, require = 0)
    private void zhwii$noCrack(BlockPos pos, Direction side, CallbackInfo ci) {
        if (LunarLite.NOPART != null && LunarLite.NOPART.enabled) ci.cancel();
    }
}
