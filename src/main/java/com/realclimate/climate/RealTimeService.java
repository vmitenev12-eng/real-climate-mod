package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Реальные время суток по долготе: мировое время выставляется по реальному UTC
 * плюс смещение из долготы (15° = 1 час, как в природе). Какая долгота задаёт
 * мировое время — настраивается: спавн мира или фиксированное число.
 * Часовой пояс любой точки мира = её долгота / 15.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class RealTimeService {
    private static MinecraftServer server;
    private static double anchorLongitude = Double.NaN;

    private RealTimeService() {}

    public static void init(MinecraftServer s) {
        server = s;
        anchorLongitude = Double.NaN;
    }

    public static void clear() {
        server = null;
    }

    /** Сервер для смежных сервисов (таяние снега и т.п.). */
    public static MinecraftServer server() {
        return server;
    }

    /** Локальное солнечное время в точке, час (0..24). */
    public static double localHours(double x) {
        long now = System.currentTimeMillis();
        double utcHours = (now % 86_400_000L) / 3_600_000.0;
        double lon = Geo.longitude(x, ClimateConfig.scale());
        return (utcHours + lon / 15.0 + 24.0) % 24.0;
    }

    /**
     * Включена ли синхронизация времени суток с реальным временем. Для смежных модов (Public Transport Mod 2):
     * когда true, время мира ведёт этот мод, а их собственные часы и расписания должны идти по поясу из
     * {@link #zoneOffsetSeconds(Level)}.
     */
    public static boolean isRealTime() {
        try {
            return ClimateConfig.REAL_TIME.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Смещение часового пояса от UTC в секундах для якорной долготы (спавн мира или число из конфига):
     * долгота / 15 часов. Работает и на клиенте (нужен только спавн уровня и конфиг масштаба), поэтому часы
     * на клиенте и сервере совпадают с небом.
     */
    public static int zoneOffsetSeconds(Level level) {
        double lon;
        String anchor = ClimateConfig.TIME_ANCHOR.get();
        try {
            lon = Math.max(-180.0, Math.min(180.0, Double.parseDouble(anchor)));
        } catch (NumberFormatException ignored) {
            lon = Geo.longitude(level.getSharedSpawnPos().getX(), ClimateConfig.scale());
        }
        return (int) Math.round(lon / 15.0 * 3600.0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || server == null) return;
        if (server.getTickCount() % 20 != 0) return;
        if (!ClimateConfig.REAL_TIME.get()) return;
        ServerLevel overworld = server.overworld();
        if (overworld != null
                && overworld.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) {
            if (Double.isNaN(anchorLongitude)) anchorLongitude = resolveAnchor();

            double hours = localHours(anchorWorldX());
            // В Minecraft день начинается в 06:00 (0 тиков = восход).
            long target = (long) (((hours - 6.0 + 24.0) % 24.0) * 1000.0);
            long current = overworld.getDayTime() % 24000L;
            long drift = Math.floorMod(target - current, 24000L);
            // Подтягиваем время только при заметном расхождении, не дёргая каждый тик.
            if (drift > 30L && drift < 23970L) {
                overworld.setDayTime(overworld.getDayTime() - current + target);
            }
        }

        // Небо у каждого игрока — своё: раз в секунду шлём ему «время неба»
        // для его широты/долготы (SolarService растягивает световой день на
        // 0..12000 тиков, ночь — на 12000..24000). Пакет уходит после ванильной
        // рассылки мирового времени в этом же тике, поэтому побеждает наш.
        // Мировое время (логика жителей, графики) по-прежнему ведёт якорь.
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            double lat = Geo.latitude(p.getZ(), ClimateConfig.scale());
            double lon = Geo.longitude(p.getX(), ClimateConfig.scale());
            p.connection.send(new ClientboundSetTimePacket(
                    p.level().getGameTime(), SolarService.skyTicks(lat, lon), false));
        }
    }

    /** Сразу после входа игрока шлём его небо, не дожидаясь секундного тика. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || server == null) return;
        if (!isRealTime()) return;
        double lat = Geo.latitude(p.getZ(), ClimateConfig.scale());
        double lon = Geo.longitude(p.getX(), ClimateConfig.scale());
        p.connection.send(new ClientboundSetTimePacket(
                p.level().getGameTime(), SolarService.skyTicks(lat, lon), false));
    }

    private static double anchorWorldX() {
        String anchor = ClimateConfig.TIME_ANCHOR.get();
        try {
            // Якорь задан числом — трактуем как долготу напрямую (с учётом начала координат).
            double lon = Math.max(-180.0, Math.min(180.0, Double.parseDouble(anchor)));
            return Geo.xForLongitude(lon, ClimateConfig.scale());
        } catch (NumberFormatException ignored) {
            // По умолчанию — долгота точки спавна.
        }
        return server.overworld().getSharedSpawnPos().getX();
    }

    private static double resolveAnchor() {
        String anchor = ClimateConfig.TIME_ANCHOR.get();
        try {
            return Math.max(-180.0, Math.min(180.0, Double.parseDouble(anchor)));
        } catch (NumberFormatException ignored) {
            return Geo.longitude(server.overworld().getSharedSpawnPos().getX(), ClimateConfig.scale());
        }
    }
}
