package com.realclimate.climate;

import com.mojang.brigadier.context.CommandContext;
import com.realclimate.RealClimate;
import com.realclimate.block.ACIndoorBlock;
import com.realclimate.tileentity.ACIndoorBlockEntity;
import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import com.realclimate.tileentity.GrilleBlockEntity;
import com.realclimate.tileentity.RadiatorBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.level.BlockEvent;
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
        IndoorClimate.clear();
    }

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent e) {
        e.addListener(new ThermalDataLoader());
    }

    /**
     * Изменение блоков инвалидирует кэш помещений: открытая дверь, сломанная
     * стена или новая перегородка меняют объём/утечки комнаты, и следующий
     * же замер обязан пересканировать её, а не жить по старому обходу.
     * Переключение ACTIVE внутреннего блока кондиционера пропускаем:
     * это не геометрия — вклад приборов перечитывается живьём при каждом
     * переиспользовании кэша, иначе гистерезис дующего блока сбрасывал бы
     * кэш каждые несколько секунд.
     */
    @SubscribeEvent
    public static void onBlockChanged(BlockEvent.NeighborNotifyEvent e) {
        if (!(e.getLevel() instanceof Level level)) return;
        if (e.getState().getBlock() instanceof ACIndoorBlock) return;
        IndoorClimate.invalidateRoomAt(level, e.getPos());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("climate")
                .executes(ClimateEvents::info)
                .then(Commands.literal("body").executes(ClimateEvents::body))
                .then(Commands.literal("room").executes(ClimateEvents::room))
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
        double raw = ClimateService.temperature(p.getX(), p.getZ(), p.getY());
        double eq = IndoorClimate.roomEquilibrium(p.level(), p.blockPosition(), raw);
        double air = IndoorClimate.getAir(p);
        double enc = IndoorClimate.enclosure(p);
        String text = String.format(
                "Тело: %.1f °C | воздух вокруг: %.1f °C | утепление: %.0f%%%n"
                        + "Комната: равновесие %.1f °C | масса воздуха %.1f °C | герметичность %.0f%%%n"
                        + "Скан: %s",
                BodyTemperature.get(p), BodyTemperature.effectiveAmbient(p), BodyTemperature.insulation(p) * 100,
                eq, Double.isNaN(air) ? raw : air, (Double.isNaN(enc) ? 0.0 : enc) * 100,
                IndoorClimate.roomDebug(p.level(), p.blockPosition()));
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * Диагностика микроклимата: показывает все климатические приборы в радиусе
     * 9 блоков с их уставками, струёй и состоянием связок. Если заданная
     * температура «игнорируется» — по этому выводу видно, где порвалась
     * цепочка пульт→наружный→внутренний/решётка.
     */
    private static int room(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p)) {
            src.sendFailure(Component.literal("Команда доступна только игроку."));
            return 0;
        }
        Level lv = p.level();
        BlockPos center = p.blockPosition();
        StringBuilder sb = new StringBuilder();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-9, -5, -9), center.offset(9, 5, 9))) {
            BlockEntity be = lv.getBlockEntity(pos);
            if (be instanceof ACIndoorBlockEntity in) {
                boolean active = lv.getBlockState(pos).hasProperty(ACIndoorBlock.ACTIVE)
                        && lv.getBlockState(pos).getValue(ACIndoorBlock.ACTIVE);
                sb.append(String.format("Внутренний блок [%d %d %d]: уставка %.1f°, струя %.1f°, %s, наружный %s%n",
                        pos.getX(), pos.getY(), pos.getZ(), in.getTargetTemp(),
                        Double.isNaN(in.getEmission()) ? in.getTargetTemp() : in.getEmission(),
                        active ? "ДУЕТ" : "пауза",
                        in.getLinkedOutdoor() != null ? "привязан" : "НЕ ПРИВЯЗАН"));
            } else if (be instanceof ACOutdoorBlockEntity out) {
                sb.append(String.format("Наружный блок [%d %d %d]: рабочая уставка %.1f°, внутренних %d, пультов %d, %s%n",
                        pos.getX(), pos.getY(), pos.getZ(), out.getOperatingTargetTemp(lv),
                        out.getLinkedIndoors().size(), out.getLinkedPanels().size(),
                        out.isEnabled() ? "включён" : "ВЫКЛЮЧЕН"));
            } else if (be instanceof ControlPanelBlockEntity cp) {
                sb.append(String.format("Пульт [%d %d %d]: уставка %.1f°, режим %s, замер %s, наружных %d, датчиков %d%n",
                        pos.getX(), pos.getY(), pos.getZ(), cp.getTargetTemp(),
                        cp.isAutoMode() ? "авто" : "ручной",
                        Double.isNaN(cp.getLastMeasured()) ? "—" : String.format("%.1f°", cp.getLastMeasured()),
                        cp.getLinkedOutdoors().size(), cp.getLinkedThermometers().size()));
            } else if (be instanceof GrilleBlockEntity g) {
                sb.append(String.format("Решётка [%d %d %d]: уставка %.1f°, поток %s, %s%n",
                        pos.getX(), pos.getY(), pos.getZ(), g.getTargetTemp(),
                        Double.isNaN(g.getEmission()) ? "—" : String.format("%.1f°", g.getEmission()),
                        g.isOpen() ? "открыта" : "закрыта"));
            } else if (be instanceof RadiatorBlockEntity r) {
                sb.append(String.format("Радиатор [%d %d %d]: уставка %.1f°, %s%n",
                        pos.getX(), pos.getY(), pos.getZ(), r.getTargetTemp(),
                        r.isEnabled() ? "включён" : "выключен"));
            }
        }
        if (sb.length() == 0) sb.append("Приборов в радиусе 9 блоков не найдено.");
        String text = sb.toString().trim();
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
