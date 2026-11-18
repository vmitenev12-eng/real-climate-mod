package com.realclimate.climate;

import com.mojang.brigadier.context.CommandContext;
import com.realclimate.RealClimate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
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
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        ClimateService.stop();
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("climate").executes(ClimateEvents::info));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Vec3 p = src.getPosition();
        double scale = ClimateConfig.scale();
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
