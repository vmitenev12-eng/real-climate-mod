package com.realclimate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realclimate.RealClimate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.awt.*;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class KeyOverlayRenderer {
    private KeyOverlayRenderer() {}

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var held = mc.player.getMainHandItem();
        if (held.isEmpty() || !held.getItem().getRegistryName().toString().equals("real_climate:installation_key")) return;

        PoseStack pose = e.getGuiGraphics().pose();
        pose.pushPose();
        // Простая надпись под прицелом
        int w = e.getGuiGraphics().guiWidth();
        int h = e.getGuiGraphics().guiHeight();
        e.getGuiGraphics().drawString(mc.font, Component.literal("Ключ設置: ПКМ — связать кондиционеры"), w / 2 - 150, h / 2 - 20, Color.WHITE.getRGB(), true);
        pose.popPose();
    }
}
