package com.realclimate.mixin;

import com.realclimate.climate.ClimateService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Тип осадков решает реальный климат мода, а не шаблон биома: снег лежит, вода
 * замерзает и падает снег там, где по реальной температуре ниже нуля.
 *
 * Сигнатуры — как в 1.20.1: shouldSnow(LevelReader, BlockPos), две перегрузки
 * shouldFreeze и getPrecipitationAt(BlockPos), который решает дождь/снег
 * при рендере и у сущностей.
 */
@Mixin(Biome.class)
public abstract class MixinBiome {

    @Inject(method = "shouldSnow", at = @At("HEAD"), cancellable = true)
    private void realclimate$shouldSnow(LevelReader level, BlockPos pos,
                                        CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(shouldBeCold((Biome) (Object) this, pos));
    }

    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private void realclimate$shouldFreeze(LevelReader level, BlockPos pos,
                                          CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(shouldBeCold((Biome) (Object) this, pos));
    }

    @Inject(method = "shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void realclimate$shouldFreezeCheck(LevelReader level, BlockPos pos, boolean mustBeAtSeaLevel,
                                               CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(shouldBeCold((Biome) (Object) this, pos));
    }

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void realclimate$getPrecipitationAt(BlockPos pos,
                                                CallbackInfoReturnable<Biome.Precipitation> cir) {
        Biome self = (Biome) (Object) this;
        if (!self.hasPrecipitation()) {
            cir.setReturnValue(Biome.Precipitation.NONE);
            return;
        }
        boolean cold = ClimateService.temperature(pos.getX(), pos.getZ(), pos.getY()) < 0.0;
        cir.setReturnValue(cold ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN);
    }

    private static boolean shouldBeCold(Biome biome, BlockPos pos) {
        // Биомы без осадков (пустыни) остаются без снега и льда.
        if (!biome.hasPrecipitation()) return false;
        return ClimateService.temperature(pos.getX(), pos.getZ(), pos.getY()) < 0.0;
    }
}
