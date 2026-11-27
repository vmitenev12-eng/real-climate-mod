package com.realclimate.mixin;

import com.realclimate.climate.ClimateConfig;
import com.realclimate.climate.Geo;
import com.realclimate.climate.RealTimeService;
import com.realclimate.climate.SolarService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Освещённость для игровой логики — по местному солнцу точки, а не по единому
 * мировому времени. Ваниль хранит в световом движке «сырой» свет неба (15 под
 * открытым небом и днём, и ночью), а время суток учитывает единственным
 * вычитанием глобального getSkyDarken() — одного на всё измерение. Мы вместо
 * него подставляем затемнение от восхода/заката именно в этой точке карты
 * (SolarService.skyDarkenAt), поэтому спавн мобов (Monster.isDarkEnoughToSpawn),
 * яркость при горении нежити и любые другие проверки освещённости живут по
 * солнцу своей долготы и широты: город на +12 не живёт «ночью по дневным
 * часам». Накрываем обе перегрузки метода (вариант без аргумента в ванили
 * просто зовёт вариант с глобальным затемнением). Клиента не трогаем — там
 * небо уже персональное (пакеты RealTimeService).
 */
@Mixin(LevelReader.class)
public interface MixinLevelReader {

    @Inject(method = "getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"), cancellable = true)
    default void realclimate$localSkyDarken(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        realclimate$applyLocalDarken(pos, cir);
    }

    @Inject(method = "getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;I)I",
            at = @At("HEAD"), cancellable = true)
    default void realclimate$localSkyDarkenArg(BlockPos pos, int skyDarken,
                                               CallbackInfoReturnable<Integer> cir) {
        realclimate$applyLocalDarken(pos, cir);
    }

    /** Общий расчёт: сырая освещённость минус затемнение по местному солнцу. */
    default void realclimate$applyLocalDarken(BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        LevelReader self = (LevelReader) this;
        if (!(self instanceof Level level) || level.isClientSide
                || level.dimension() != Level.OVERWORLD || !RealTimeService.isRealTime()) return;
        if (pos.getX() < -30000000 || pos.getZ() < -30000000
                || pos.getX() >= 30000000 || pos.getZ() >= 30000000) {
            cir.setReturnValue(15); // за пределами мира ваниль отвечает 15
            return;
        }
        double lat = Geo.latitude(pos.getZ(), ClimateConfig.scale());
        double lon = Geo.longitude(pos.getX(), ClimateConfig.scale());
        int darken = SolarService.skyDarkenAt(lat, lon,
                level.getRainLevel(1.0F), level.getThunderLevel(1.0F));
        int sky = Math.max(0, self.getBrightness(LightLayer.SKY, pos) - darken);
        int block = self.getBrightness(LightLayer.BLOCK, pos);
        cir.setReturnValue(Math.max(sky, block));
    }
}
