package com.realclimate.client;

import com.realclimate.RealClimate;
import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import com.realclimate.tileentity.RadiatorBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClimateBlockInteractionHandler {
    private ClimateBlockInteractionHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || event.getEntity().isShiftKeyDown()
                || !event.getItemStack().isEmpty() || !event.getLevel().isClientSide) return;

        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        ClimateSettingsScreen screen = createScreen(event.getPos(), blockEntity);
        if (screen == null) return;

        net.minecraft.client.Minecraft.getInstance().setScreen(screen);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static ClimateSettingsScreen createScreen(net.minecraft.core.BlockPos pos, BlockEntity blockEntity) {
        if (blockEntity instanceof ControlPanelBlockEntity panel) {
            return new ClimateSettingsScreen(pos, panel.isEnabled(), panel.getTargetTemp());
        }
        if (blockEntity instanceof RadiatorBlockEntity radiator) {
            return new ClimateSettingsScreen(pos, radiator.isEnabled(), radiator.getTargetTemp(), 20.0, 90.0);
        }
        if (blockEntity instanceof ACOutdoorBlockEntity outdoor) {
            return new ClimateSettingsScreen(pos, outdoor.isEnabled(), outdoor.getTargetTemp());
        }
        return null;
    }
}
