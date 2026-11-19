package com.realclimate.client;

import com.realclimate.RealClimate;
import com.realclimate.climate.BodyTemperature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.ACIndoorBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class KeyOverlayRenderer {
    private KeyOverlayRenderer() {}

    @SubscribeEvent
    public static void onLinkLines(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        var held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!"real_climate:installation_key".equals(itemId)) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        double cx = event.getCamera().getPosition().x;
        double cy = event.getCamera().getPosition().y;
        double cz = event.getCamera().getPosition().z;
        pose.translate(-cx, -cy, -cz);

        var buffer = mc.renderBuffers().bufferSource();
        VertexConsumer line = buffer.getBuffer(RenderType.LINES);

        BlockPos center = mc.player.blockPosition();
        for (int dx = -64; dx <= 64; dx++) {
            for (int dz = -64; dz <= 64; dz++) {
                int chunkX = center.getX() + dx * 16;
                int chunkZ = center.getZ() + dz * 16;
                var chunk = mc.level.getChunkSource().getChunk(chunkX >> 4, chunkZ >> 4, false);
                if (chunk == null) continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ACOutdoorBlockEntity outdoor)) continue;
                    BlockPos from = outdoor.getBlockPos();
                    for (BlockPos to : outdoor.getLinkedIndoors()) {
                        if (!(mc.level.getBlockEntity(to) instanceof ACIndoorBlockEntity)) continue;
                        line.vertex(pose.last().pose(), from.getX() + 0.5f, from.getY() + 0.5f, from.getZ() + 0.5f)
                                .color(255, 255, 255, 255).normal(pose.last().normal(), 0.0f, 1.0f, 0.0f).endVertex();
                        line.vertex(pose.last().pose(), to.getX() + 0.5f, to.getY() + 0.5f, to.getZ() + 0.5f)
                                .color(255, 255, 255, 255).normal(pose.last().normal(), 0.0f, 1.0f, 0.0f).endVertex();
                    }
                }
            }
        }
        buffer.endBatch(RenderType.LINES);
        pose.popPose();
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        double body = BodyTemperature.get(mc.player);
        double ambient = BodyTemperature.effectiveAmbient(mc.player);
        double insulation = BodyTemperature.insulation(mc.player);

        int h = e.getGuiGraphics().guiHeight();
        e.getGuiGraphics().drawString(mc.font,
                Component.literal(String.format("Тело: %.1f°C  |  Воздух: %.1f°C  |  Утепление: %.0f%%",
                        body, ambient, insulation * 100.0)),
                10, h - 18, 0xFFFFFFFF, true);

        var held = mc.player.getMainHandItem();
        if (held.isEmpty()) return;
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!"real_climate:installation_key".equals(itemId)) return;

        e.getGuiGraphics().drawString(mc.font,
                Component.literal("ПКМ: выбрать наружный и внутренний блок; Shift+ПКМ: разорвать связь"),
                10, h - 34, 0xFFFFFFFF, true);
    }
}
