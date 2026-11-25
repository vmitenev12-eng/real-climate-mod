package com.realclimate.climate;

import com.mojang.brigadier.context.CommandContext;
import com.realclimate.RealClimate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.LocalDateTime;

@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class ClimateEvents {
    private ClimateEvents() {}

    @SubscribeEvent
    public static void onStarting(ServerStartingEvent e) {
        ClimateService.start(e.getServer().overworld().getSeed());
        RealTimeService.init(e.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        ClimateService.stop();
        VentilationService.clear();
        RealTimeService.clear();
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent e) {
        e.addListener(new ThermalDataLoader());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("climate")
                .executes(ClimateEvents::info)
                .then(Commands.literal("body").executes(ClimateEvents::body))
                .then(Commands.literal("time").executes(ClimateEvents::time)));
    }

    private static int time(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Vec3 p = src.getPosition();
        double hours = RealTimeService.localHours(p.x);
        double lon = Geo.longitude(p.x, ClimateConfig.scale());
        String text = String.format("Локальное время: %02d:%02d (долгота %.1f°, UTC%+d)",
                (int) hours, (int) ((hours % 1.0) * 60.0), lon, Math.round(lon / 15.0));
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int body(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("Команда доступна только игроку."));
            return 0;
        }
        String text = String.format("Тело: %.1f °C | воздух вокруг: %.1f °C | утепление: %.0f%%",
                BodyTemperature.get(p), BodyTemperature.effectiveAmbient(p), BodyTemperature.insulation(p) * 100);
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Vec3 p = src.getPosition();
        double scale = ClimateService.scale();
        double lat = Geo.latitude(p.z, scale);
        double lon = Geo.longitude(p.x, scale);
        int band = Geo.band(lat);
        double now = ClimateService.temperature(p.x, p.z, scale);
        Forecast f = ClimateService.forecast(band);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Широта %.2f°, долгота %.2f° (%s)\n", lat, lon, ClimateZone.of(lat).title));
        sb.append(String.format("Сейчас: %.1f °C\n", now));
        if (f == null) {
            sb.append("Прогноз считается, повторите команду через секунду.");
        } else {
            sb.append("Прогноз на 6 ч:");
            LocalDateTime t = f.slotStart;
            for (int i = 0; i < Forecast.HOURS; i++) {
                sb.append(String.format(" %02d:00 %.0f°", t.plusHours(i).getHour(), f.at(i)));
            }
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
