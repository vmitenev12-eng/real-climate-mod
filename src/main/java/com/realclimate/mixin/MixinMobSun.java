package com.realclimate.mixin;

import com.realclimate.climate.ClimateConfig;
import com.realclimate.climate.Geo;
import com.realclimate.climate.RealTimeService;
import com.realclimate.climate.SolarService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Нежить горит по местному солнцу. Ваниль в Mob.isSunBurnTick спрашивает
 * level.isDay() — единое время якоря на весь мир; подставляем «день ли сейчас»
 * по восходу/закату в точке, где стоит моб. Зомби в Архангельске горит в его
 * короткий зимний день, зомби в городе на часовом поясе +12 — в свой, а не в
 * чужой полдень. Яркость неба в проверке уже местная (MixinLevelReader), так
 * что горение включается и гаснет вместе с солнцем именно над головой моба.
 * Сюда же попадают скелеты и фантомы — все зовут Mob.isSunBurnTick.
 */
@Mixin(Mob.class)
public abstract class MixinMobSun {

    @Redirect(method = "isSunBurnTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isDay()Z"))
    private boolean realclimate$isLocalDay(Level level) {
        if (level.dimension() != Level.OVERWORLD || !RealTimeService.isRealTime()) {
            return level.isDay();
        }
        BlockPos pos = ((Mob) (Object) this).blockPosition();
        return SolarService.isDayAt(
                Geo.latitude(pos.getZ(), ClimateConfig.scale()),
                Geo.longitude(pos.getX(), ClimateConfig.scale()));
    }
}
