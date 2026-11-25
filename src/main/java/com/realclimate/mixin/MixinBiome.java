package com.realclimate.mixin;

import com.realclimate.climate.ClimateService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Тип осадков решает реальный климат мода, а не шаблон биома: снег лежит и
 * вода замерзает там, где по реальной температуре ниже нуля. Через shouldFreeze
 * автоматически следует и тип осадков — getPrecipitationAt опирается на него.
 */
@Mixin(Biome.class)
public abstract class MixinBiome {

    @Inject(method = "shouldSnow", at = @At("HEAD"), cancellable = true)
    private void realclimate$shouldSnow(BlockGetter level, BlockPos pos, int height,
                                        CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ClimateService.temperature(pos.getX(), pos.getZ(), pos.getY() + height) < 0.0);
    }

    @Inject(method = "shouldFreeze", at = @At("HEAD"), cancellable = true)
    private void realclimate$shouldFreeze(LevelReader level, BlockPos pos,
                                          CallbackInfoReturnable<Boolean> cir) {
        // Биомы без осадков (пустыни) остаются без снега и льда.
        if (!((Biome) (Object) this).hasPrecipitation()) {
            cir.setReturnValue(false);
            return;
        }
        cir.setReturnValue(ClimateService.temperature(pos.getX(), pos.getZ(), pos.getY()) < 0.0);
    }
}
