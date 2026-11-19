package com.realclimate.climate;

import com.realclimate.RealClimate;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Температура тела игрока. Считается на сервере раз в 40 тиков (2 с), без постоянной нагрузки.
 * Состояние хранится в persistentData игрока, поэтому переживает перезаход.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class BodyTemperature {
    public static final double NORMAL = 36.6;
    private static final String KEY = "realclimate_body";
    private static final int PERIOD = 40;
    private static final Map<UUID, AmbientCache> AMBIENT_CACHE = new ConcurrentHashMap<>();

    private BodyTemperature() {}

    public static double get(Player p) {
        CompoundTag d = p.getPersistentData();
        return d.contains(KEY) ? d.getDouble(KEY) : NORMAL;
    }

    private static void set(Player p, double v) {
        p.getPersistentData().putDouble(KEY, v);
    }

    /** Температура воздуха в точке игрока с учётом погоды, укрытия и тепла рядом. */
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
        if (lv.isRainingAt(pos)) t -= 3.0;
        if (p.isInWater()) t -= 6.0;
        if (!lv.canSeeSky(pos) && pos.getY() < lv.getSeaLevel() - 8) {
            // глубокие пещеры стабилизируются около +10 °C
            t = t * 0.4 + 6.0;
        }
        double result = t + heatNearby(lv, pos) + VentilationService.temperatureOffset(lv, pos, t);
        AMBIENT_CACHE.put(p.getUUID(), new AmbientCache(lv.dimension(), pos.immutable(), gameTime, result));
        return result;
    }

    private record AmbientCache(net.minecraft.resources.ResourceKey<Level> dimension, BlockPos pos,
                                long gameTime, double temperature) {}

    /** Heat and cold travel only through connected air cells, not through solid walls. */
    private static double heatNearby(Level lv, BlockPos c) {
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
        return Math.max(-25.0, Math.min(25.0, heat));
    }

    private static double sourceStrength(Level level, BlockPos pos, BlockState state) {
        boolean enclosed = !level.canSeeSky(pos);
        if (state.is(Blocks.LAVA)) return enclosed ? 8.0 : 4.0;
        if (state.is(BlockTags.FIRE)) return enclosed ? 6.0 : 3.0;
        if (state.is(BlockTags.CAMPFIRES) && state.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
                && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) return enclosed ? 7.0 : 4.0;
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                && state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) return enclosed ? 5.0 : 2.0;
        if (state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)) return enclosed ? 1.0 : 0.5;

        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof com.realclimate.tileentity.RadiatorBlockEntity radiator && radiator.isEnabled()) {
            return Math.max(0.0, radiator.getTargetTemp() - ClimateService.getTemperature(level, pos)) * 0.45;
        }
        if (entity instanceof com.realclimate.tileentity.ACIndoorBlockEntity indoor && indoor.isEnabled()
            && state.hasProperty(com.realclimate.block.ACIndoorBlock.ACTIVE)
            && state.getValue(com.realclimate.block.ACIndoorBlock.ACTIVE)) {
            return (indoor.getTargetTemp() - ClimateService.getTemperature(level, pos)) * 0.85;
        }
        if (entity instanceof com.realclimate.tileentity.GrilleBlockEntity grille && grille.isOpen()) {
            return (grille.getTargetTemp() - ClimateService.getTemperature(level, pos)) * 0.8;
        }
        return 0.0;
    }

    /** Теплоизоляция брони: 0..1. */
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
        if (p.isCreative() || p.isSpectator()) { set(p, NORMAL); return; }

        double amb = effectiveAmbient(p);
        double ins = insulation(p);
        double delta = amb - 20.0;
        if (delta < 0) delta *= (1.0 - 0.8 * ins);   // броня защищает от холода
        else delta *= (1.0 + 0.3 * ins);              // и немного греет в жару
        double target = NORMAL + Math.max(-9.0, Math.min(6.0, delta * 0.12));

        double cur = get(p);
        cur += (target - cur) * 0.05;
        set(p, cur);
        applyEffects(p, cur);
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
