package com.realclimate.climate;

import com.realclimate.RealClimate;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Температура тела игрока. Считается на сервере раз в 40 тиков (2 с), без постоянной нагрузки.
 * Состояние хранится в persistentData игрока, поэтому переживает перезаход.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID)
public final class BodyTemperature {
    public static final double NORMAL = 36.6;
    private static final String KEY = "realclimate_body";
    private static final int PERIOD = 40;

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
        double scale = ClimateConfig.scale();
        double t = ClimateService.temperature(p.getX(), p.getZ(), p.getY());
        if (Double.isNaN(t) || Double.isInfinite(t)) t = 20.0;
        Level lv = p.level();
        BlockPos pos = p.blockPosition();
        if (lv.isRainingAt(pos)) t -= 3.0;
        if (p.isInWater()) t -= 6.0;
        if (!lv.canSeeSky(pos) && pos.getY() < lv.getSeaLevel() - 8) {
            // глубокие пещеры стабилизируются около +10 °C
            t = t * 0.4 + 6.0;
        }
        return t + heatNearby(lv, pos);
    }

    /** Тепло от открытого огня, лавы, печей рядом (радиус 3). */
    private static double heatNearby(Level lv, BlockPos c) {
        double heat = 0;
        boolean enclosed = !lv.canSeeSky(c);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -15; dx <= 15; dx++)
            for (int dy = -8; dy <= 8; dy++)
                for (int dz = -15; dz <= 15; dz++) {
                    int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (manhattan > 15) continue;
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState s = lv.getBlockState(m);
                    if (s.isAir()) continue;

                    double w = 0;
                    if (s.is(Blocks.LAVA)) w = enclosed ? 8 : 4;
                    else if (s.is(BlockTags.FIRE)) w = enclosed ? 6 : 3;
                    else if (s.is(BlockTags.CAMPFIRES) && s.hasProperty(net.minecraft.world.level.block.CampfireBlock.LIT)
                            && s.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) w = enclosed ? 7 : 4;
                    else if ((s.is(Blocks.FURNACE) || s.is(Blocks.BLAST_FURNACE) || s.is(Blocks.SMOKER))
                            && s.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)
                            && s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) w = enclosed ? 5 : 2;
                    else if (s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH)) w = enclosed ? 1.0 : 0.5;
                    else if (s.is(com.realclimate.registry.ModBlocks.AC_INDOOR.get())) {
                        net.minecraft.world.level.block.entity.BlockEntity entity = lv.getBlockEntity(m);
                        if (entity instanceof com.realclimate.tileentity.ACIndoorBlockEntity indoor
                                && indoor.isEnabled() && stateValue(s, com.realclimate.block.ACIndoorBlock.ACTIVE)) {
                            w = (indoor.getTargetTemp() - 20.0) * 0.35 * (enclosed ? 1.0 : 0.5);
                        }
                    }
                    else if (s.is(com.realclimate.registry.ModBlocks.GRILLE_BLOCK.get())) {
                        net.minecraft.world.level.block.entity.BlockEntity entity = lv.getBlockEntity(m);
                        if (entity instanceof com.realclimate.tileentity.GrilleBlockEntity grille
                                && grille.isOpen()) {
                            w = (grille.getTargetTemp() - 20.0) * 0.28 * (enclosed ? 1.0 : 0.4);
                        }
                    }
                    else if (s.is(com.realclimate.registry.ModBlocks.RADIATOR.get())
                            && s.hasProperty(com.realclimate.block.RadiatorBlock.ACTIVE)
                            && s.getValue(com.realclimate.block.RadiatorBlock.ACTIVE)) w = enclosed ? 12 : 5;

                    if (w > 0) {
                        heat += w * (1.0 - Math.min(1.0, manhattan / 16.0));
                    }
                }
        return Math.max(-25.0, Math.min(25.0, heat));
    }

    private static boolean stateValue(BlockState state, net.minecraft.world.level.block.state.properties.BooleanProperty property) {
        return state.hasProperty(property) && state.getValue(property);
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
