package com.realclimate.climate;

import com.realclimate.RealClimate;
import com.realclimate.network.ModMessages;
import com.realclimate.network.PacketClimateState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Температура тела игрока. Считается на сервере раз в 40 тиков (2 с), без постоянной нагрузки.
 * Состояние хранится в persistentData игрока, поэтому переживает перезаход.
 *
 * Замерзание зависит от одежды и влаги: мокрая одежда почти не греет и на
 * морозе дополнительно вытягивает тепло. Влага держится ещё ~45 с после
 * выхода из воды — искупавшись в холодную погоду, мёрзнешь и на суше.
 * Вода следует за температурой воздуха: в жару она тёплая, в мороз — ледяная.
 * В жару наоборот — перегрев от слишком тёплой одежды.
 *
 * Клиенту каждые 40 тиков уходят готовые значения (тело, воздух, утепление),
 * а клиент плавно «догоняет» их между пакетами — HUD живой, без ступенек.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class BodyTemperature {
    public static final double NORMAL = 36.6;
    private static final String KEY = "realclimate_body";
    private static final String WET_KEY = "realclimate_wet";
    private static final int PERIOD = 40;
    private static final Map<UUID, AmbientCache> AMBIENT_CACHE = new ConcurrentHashMap<>();

    // Клиентская копия серверных значений (заполняется только из пакета).
    private static volatile double syncedBody = Double.NaN;
    private static volatile double syncedAmbient = Double.NaN;
    private static volatile double syncedInsulation = Double.NaN;
    private static volatile long syncedAtMs = Long.MIN_VALUE;

    // Клиентское сглаживание: сервер шлёт срез каждые 2 с, а отображение
    // непрерывно тянется к цели с постоянной времени ~0.3 с.
    private static double dispBody = Double.NaN;
    private static double dispAmbient = Double.NaN;
    private static double dispInsulation = Double.NaN;
    private static long dispBodyAt = 0L;
    private static long dispAmbientAt = 0L;
    private static long dispInsulationAt = 0L;

    private BodyTemperature() {}

    public static double get(Player p) {
        CompoundTag d = p.getPersistentData();
        return d.contains(KEY) ? d.getDouble(KEY) : NORMAL;
    }

    private static void set(Player p, double v) {
        p.getPersistentData().putDouble(KEY, v);
    }

    /** Влага одежды и тела: 0 — сухой, 1 — только что вышел из воды. */
    public static double wetness(Player p) {
        CompoundTag d = p.getPersistentData();
        return d.contains(WET_KEY) ? d.getDouble(WET_KEY) : 0.0;
    }

    /**
     * Обновление влаги: в воде мокнешь мгновенно, под открытым дождём — примерно
     * за минуту (крыша спасает), сохнешь ~45 с. Раз в PERIOD тиков на сервере.
     */
    private static void updateWetness(ServerPlayer p) {
        double wet = wetness(p);
        if (p.isInWater()) {
            wet = 1.0;
        } else if (p.level().isRainingAt(p.blockPosition())) {
            wet = Math.min(1.0, wet + (double) PERIOD / 2400.0);
        } else if (wet > 0.0) {
            wet = Math.max(0.0, wet - (double) PERIOD / 1800.0);
        }
        if (wet != wetness(p)) p.getPersistentData().putDouble(WET_KEY, wet);
    }

    /** Приём пакета на клиенте: сервер прислал свежие значения для HUD. */
    public static void applyClientSync(double body, double ambient, double insulation) {
        syncedBody = body;
        syncedAmbient = ambient;
        syncedInsulation = insulation;
        syncedAtMs = System.currentTimeMillis();
    }

    /** Синхронизация ещё живая? (пакет каждые 2 с, запас — 10 с) */
    private static boolean syncFresh() {
        return !Double.isNaN(syncedBody) && System.currentTimeMillis() - syncedAtMs < 10_000;
    }

    /** Плавное «догоняние» целевого значения между серверными пакетами. */
    private static double chase(double current, double target, long lastAt, long now) {
        if (Double.isNaN(current) || lastAt == 0L) return target;
        double dt = Math.min(0.5, Math.max(0.0, (now - lastAt) / 1000.0));
        double alpha = 1.0 - Math.exp(-dt / 0.30);
        return current + (target - current) * alpha;
    }

    /** Температура тела для отображения: серверная цель + клиентское сглаживание. */
    public static double bodyDisplay(Player p) {
        double target = syncFresh() ? syncedBody : get(p);
        long now = System.currentTimeMillis();
        dispBody = chase(dispBody, target, dispBodyAt, now);
        dispBodyAt = now;
        return dispBody;
    }

    /** Воздух для отображения: серверная цель (с инерцией помещения) + сглаживание. */
    public static double ambientDisplay(Player p) {
        double target = syncFresh() ? syncedAmbient : effectiveAmbient(p);
        long now = System.currentTimeMillis();
        dispAmbient = chase(dispAmbient, target, dispAmbientAt, now);
        dispAmbientAt = now;
        return dispAmbient;
    }

    /** Утепление одежды для отображения. */
    public static double insulationDisplay(Player p) {
        double target = syncFresh() ? syncedInsulation : insulation(p);
        long now = System.currentTimeMillis();
        dispInsulation = chase(dispInsulation, target, dispInsulationAt, now);
        dispInsulationAt = now;
        return dispInsulation;
    }

    /** Температура воздуха в точке игрока с учётом погоды, укрытия, воды и тепла рядом. */
    public static double effectiveAmbient(Player p) {
        Level lv = p.level();
        BlockPos pos = p.blockPosition();
        long gameTime = lv.getGameTime();
        AmbientCache cached = AMBIENT_CACHE.get(p.getUUID());
        if (cached != null && cached.dimension.equals(lv.dimension()) && cached.pos.equals(pos)
                && gameTime >= cached.gameTime && gameTime - cached.gameTime < 20) {
            return cached.temperature;
        }

        double t = ClimateService.temperature(p.getX(), p.getZ(), p.getY());
        if (Double.isNaN(t) || Double.isInfinite(t)) t = 20.0;
        if (lv.isRaining()) {
            // Дождь холодит пропорционально открытости небу: у края крыши или
            // под листвой нет резкого скачка на −3 °C в один блок.
            double exposure = lv.getBrightness(LightLayer.SKY, pos) / 15.0;
            t -= 3.0 * exposure;
        }
        if (!lv.canSeeSky(pos) && pos.getY() < lv.getSeaLevel() - 8) {
            // глубокие пещеры стабилизируются около +10 °C
            t = t * 0.4 + 6.0;
        }
        double heat = heatNearby(lv, pos);
        double vent = VentilationService.temperatureOffset(lv, pos, t);
        double instant = t + heat + vent;

        double result = instant;
        double shelter = IndoorClimate.shelter(lv, pos);
        double draft = IndoorClimate.draftFactor(lv, pos);
        double air = IndoorClimate.getAir(p);
        if (Double.isNaN(air)) air = t;
        if (shelter > 0.01) {
            // В помещении температура — это инерционная воздушная масса (IndoorClimate),
            // плюс слабый локальный градиент у самих источников.
            double indoor = air + heat * 0.25 + vent * 0.5;
            double mixed = instant * (1.0 - shelter * 0.9) + indoor * (shelter * 0.9);
            // Сквозняк у открытых дверей и щелей продувает в обе стороны:
            // уличный воздух мягко заходит в помещение — резких перепадов у проёма нет.
            result = mixed + (instant - mixed) * Math.min(1.0, draft * 0.6);
        } else if (draft > 0.0) {
            // Снаружи у открытой двери/окна комнатный воздух выходит наружу:
            // в проёме на улице заметно теплее (или прохладнее от кондиционера),
            // и обе стороны смешиваются мягко, без скачка.
            result = instant + (air - instant) * Math.min(1.0, draft * 0.6);
        }

        if (p.isInWater()) {
            // Вода следует за воздухом, но с инерцией водоёма: не ниже +1 °C
            // (жидкая вода), в жару — чуть прохладнее воздуха, в мороз — ледяная.
            result = Math.max(1.0, result * 0.8 + 4.0);
        } else {
            // Мокрая одежда после купания или дождя: испарение вытягивает тепло.
            // На холоде — сильный холод, в жару — лёгкая приятная прохлада.
            double wet = wetness(p);
            if (wet > 0.01) {
                double chillScale = Math.max(0.12, Math.min(1.0, (24.0 - result) / 24.0));
                result -= wet * 10.0 * chillScale;
            }
        }
        AMBIENT_CACHE.put(p.getUUID(), new AmbientCache(lv.dimension(), pos.immutable(), gameTime, result));
        return result;
    }

    private record AmbientCache(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos,
                                long gameTime, double temperature) {}

    /** Heat and cold travel only through connected air cells, not through solid walls. */
    public static double heatNearby(Level lv, BlockPos c) {
        double heat = 0;
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Map<BlockPos, Integer> distances = new java.util.HashMap<>();
        java.util.Set<BlockPos> countedSources = new java.util.HashSet<>();
        BlockPos start = c.immutable();
        queue.add(start);
        distances.put(start, 0);

        while (!queue.isEmpty() && distances.size() < 4096) {
            BlockPos airPos = queue.removeFirst();
            int distance = distances.get(airPos);

            for (Direction direction : Direction.values()) {
                BlockPos neighbor = airPos.relative(direction);
                BlockState state = lv.getBlockState(neighbor);
                double source = sourceStrength(lv, neighbor, state);
                if (source != 0.0 && countedSources.add(neighbor)) {
                    heat += source * (1.0 - Math.min(1.0, (distance + 1) / 16.0));
                }

                if (distance >= 15 || distances.containsKey(neighbor)
                    || !MiniBlockIntegration.allowsAirPassage(lv, neighbor, direction)) continue;
                distances.put(neighbor, distance + 1);
                queue.addLast(neighbor);
            }
        }
        return Math.max(-100.0, Math.min(100.0, heat));
    }

    public static double sourceStrength(Level level, BlockPos pos, BlockState state) {
        boolean enclosed = !level.canSeeSky(pos);
        if (state.is(Blocks.LAVA)) return enclosed ? 8.0 : 4.0;
        if (state.is(BlockTags.FIRE)) return enclosed ? 6.0 : 3.0;
        if (state.is(BlockTags.CAMPFIRES) && hasBooleanProperty(state, "lit", true)) return enclosed ? 7.0 : 4.0;
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && hasBooleanProperty(state, "lit", true)) return enclosed ? 5.0 : 2.0;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return enclosed ? 1.0 : 0.5;

        // Холодные источники — симметрично тёплым: сугроб морозит окрестность
        // так же, как факел её греет.
        if (state.is(Blocks.SNOW_BLOCK)) return enclosed ? -1.5 : -0.8;
        if (state.is(Blocks.POWDER_SNOW)) return enclosed ? -2.0 : -1.2;
        if (state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE)
                || state.is(Blocks.FROSTED_ICE)) return enclosed ? -1.0 : -0.5;
        if (state.is(Blocks.SNOW)) {
            int layers = state.getValue(SnowLayerBlock.LAYERS);
            return (enclosed ? -0.10 : -0.05) * layers;
        }

        if (state.getBlock() instanceof com.realclimate.block.RadiatorBlock
                && level.getBlockEntity(pos) instanceof com.realclimate.tileentity.RadiatorBlockEntity radiator
                && radiator.isEnabled()) {
            return Math.max(0.0, radiator.getTargetTemp() - ClimateService.getTemperature(level, pos)) * 0.45;
        }
        if (state.getBlock() instanceof com.realclimate.block.ACIndoorBlock
                && state.hasProperty(com.realclimate.block.ACIndoorBlock.ACTIVE)
                && state.getValue(com.realclimate.block.ACIndoorBlock.ACTIVE)
                && level.getBlockEntity(pos) instanceof com.realclimate.tileentity.ACIndoorBlockEntity indoor
                && indoor.isEnabled()) {
            // Один наружный блок = одна система: мощность делится поровну между
            // всеми подключёнными внутренними блоками, уставка не суммируется.
            int units = 1;
            if (indoor.getLinkedOutdoor() != null
                    && level.getBlockEntity(indoor.getLinkedOutdoor()) instanceof com.realclimate.tileentity.ACOutdoorBlockEntity outdoor) {
                units = Math.max(1, outdoor.getLinkedIndoors().size());
            }
            return (indoor.getTargetTemp() - ClimateService.getTemperature(level, pos)) / units;
        }
        if (state.getBlock() instanceof com.realclimate.block.GrilleBlock
                && level.getBlockEntity(pos) instanceof com.realclimate.tileentity.GrilleBlockEntity grille
                && grille.isOpen() && VentilationService.hasActiveAirflow(level, pos)) {
            double airflowTemp = VentilationService.airflowTemperature(level, pos, grille.getTargetTemp());
            // Мощность вентсистемы делится между всеми её решётками: N решёток в
            // одной системе дают в сумме эффект одной, а не N-кратный перегруз.
            double share = 1.0 / VentilationService.grilleSystemSize(level, pos);
            return (airflowTemp - ClimateService.getTemperature(level, pos)) * share;
        }
        return 0.0;
    }

    private static boolean hasBooleanProperty(BlockState state, String name, boolean expected) {
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            if (!property.getName().equals(name)) continue;
            Object value = state.getValue(property);
            return String.valueOf(value).equals(expected ? "true" : "false");
        }
        return false;
    }

    /** Теплоизоляция сухой одежды: 0..1. */
    public static double insulation(Player p) {
        double sum = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getItemBySlot(slot);
            if (st.isEmpty() || !(st.getItem() instanceof ArmorItem a)) continue;
            double w = 0.15;
            if (a.getMaterial() == ArmorMaterials.LEATHER) w = 0.25;
            else if (a.getMaterial() == ArmorMaterials.IRON || a.getMaterial() == ArmorMaterials.CHAIN) w = 0.10;
            if (slot == EquipmentSlot.CHEST) w *= 1.6;
            sum += w;
        }
        return Math.min(1.0, sum);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % PERIOD != 0) return;

        // Воздушная масса помещения и влага обновляются всегда — даже в креативе.
        // Иначе HUD в креативе показывает «уличную» температуру внутри дома
        // и навсегда расходится с термодатчиками.
        IndoorClimate.updatePlayer(p);
        updateWetness(p);

        if (p.isCreative() || p.isSpectator()) {
            set(p, NORMAL);
            // В креативе тело не замерзает, но HUD должен показывать честный климат.
            ModMessages.sendToPlayer(p, new PacketClimateState(NORMAL, effectiveAmbient(p), insulation(p)));
            return;
        }

        double amb = effectiveAmbient(p);
        double ins = insulation(p);
        double wet = wetness(p);
        // Мокрая одежда почти не держит тепло — пропорционально влаге.
        if (wet > 0.01) ins *= (1.0 - 0.65 * wet);
        double delta = amb - 20.0;
        if (delta < 0) delta *= (1.0 - 0.8 * ins);   // броня защищает от холода
        else delta *= (1.0 + 0.3 * ins);              // и немного греет в жару
        // Перегрев от слишком тёплой одежды: чем жарче и теплее одет, тем хуже.
        if (amb > 26.0 && ins > 0.5) {
            delta += (ins - 0.5) * (amb - 26.0) * 0.4;
        }
        // Мокрые вещи на холоде дополнительно вытягивают тепло.
        if (wet > 0.05 && amb < 15.0) {
            delta -= (15.0 - amb) * 0.25 * wet;
        }
        double target = NORMAL + Math.max(-9.0, Math.min(6.0, delta * 0.12));

        double cur = get(p);
        cur += (target - cur) * 0.05;
        set(p, cur);
        applyEffects(p, cur);
        // HUD показывает ровно эти значения — с инерцией помещения и мокрой одеждой.
        ModMessages.sendToPlayer(p, new PacketClimateState(cur, amb, ins));
    }

    private static void applyEffects(ServerPlayer p, double t) {
        final int dur = PERIOD + 20;
        if (t < 35.0) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, t < 33.0 ? 1 : 0, true, false));
        }
        if (t < 33.0) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, dur, 0, true, false));
            p.setTicksFrozen(Math.min(p.getTicksRequiredToFreeze() + 20, p.getTicksFrozen() + 40));
        }
        if (t < 31.0) p.hurt(p.damageSources().freeze(), 1.0f);
        if (t > 38.0) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 0, true, false));
            p.causeFoodExhaustion(0.4f);
        }
        if (t > 40.0) p.hurt(p.damageSources().onFire(), 1.0f);
    }
}
