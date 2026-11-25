package com.realclimate.client;

import com.realclimate.RealClimate;
import com.realclimate.climate.ThermalProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Подсказки теплосвойств: при наведении на блок в инвентаре/креативе игрок
 * видит теплопроводность и класс утепления — чтобы планировать застройку
 * до того, как дом построен и зимой в нём замёрзнут обогреватели.
 */
@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = Dist.CLIENT)
public final class ClimateTooltipHandler {

    private ClimateTooltipHandler() {}

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        BlockState state = blockItem.getBlock().defaultBlockState();
        double lambda = ThermalProperties.conductivity(state);
        if (!Double.isFinite(lambda) || lambda <= 0.0) return;

        // Сопротивление теплопередаче цельного блока (1 м): R = 1 / λ.
        double resistance = 1.0 / lambda;
        String quality = qualityWord(lambda);

        event.getToolTip().add(Component.literal(String.format(Locale.ROOT,
                "Теплопроводность: %.2f Вт/(м·К) · R цельного блока: %.2f м²·К/Вт", lambda, resistance)));
        event.getToolTip().add(Component.literal("Утепление: " + quality));
    }

    private static String qualityWord(double lambda) {
        // Подобрано под бытовые стройматериалы: шерсть 0.04 — отличный
        // утеплитель, кирпич 0.77 — средне, железо 45 — мостик холода.
        if (lambda < 0.08) return "отличный утеплитель";
        if (lambda < 0.3) return "хорошо держит тепло";
        if (lambda < 1.0) return "средняя теплозащита";
        if (lambda < 3.0) return "слабо держит тепло";
        return "проводит тепло (мостик холода)";
    }
}
