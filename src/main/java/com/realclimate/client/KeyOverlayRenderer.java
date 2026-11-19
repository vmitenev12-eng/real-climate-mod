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
import com.realclimate.tileentity.ControlPanelBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = RealClimate.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class KeyOverlayRenderer {
    private static final int LINK_SCAN_RADIUS_CHUNKS = 2;
    private static List<LinkLine> cachedLinkLines = List.of();
    private static net.minecraft.client.multiplayer.ClientLevel cachedLevel;
    private static long lastLinkScan = Long.MIN_VALUE;

    private KeyOverlayRenderer() {}

    private record LinkLine(BlockPos from, BlockPos to) {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !isHoldingInstallationKey(mc)) {
            cachedLinkLines = List.of();
            cachedLevel = mc.level;
            return;
        }

        long gameTime = mc.level.getGameTime();
        if (cachedLevel == mc.level && gameTime - lastLinkScan < 100) return;
        cachedLevel = mc.level;
        lastLinkScan = gameTime;

        List<LinkLine> lines = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        for (int chunkX = centerChunkX - LINK_SCAN_RADIUS_CHUNKS;
             chunkX <= centerChunkX + LINK_SCAN_RADIUS_CHUNKS; chunkX++) {
            for (int chunkZ = centerChunkZ - LINK_SCAN_RADIUS_CHUNKS;
                 chunkZ <= centerChunkZ + LINK_SCAN_RADIUS_CHUNKS; chunkZ++) {
                var chunk = mc.level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) continue;
                for (var blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof ACOutdoorBlockEntity outdoor)) continue;
                    BlockPos from = outdoor.getBlockPos();
                    for (BlockPos indoorPos : outdoor.getLinkedIndoors()) {
                        if (mc.level.getBlockEntity(indoorPos) instanceof ACIndoorBlockEntity) {
                            lines.add(new LinkLine(from, indoorPos));
                        }
                    }
                    for (BlockPos panelPos : outdoor.getLinkedPanels()) {
                        if (mc.level.getBlockEntity(panelPos) instanceof ControlPanelBlockEntity panel
                                && panel.getLinkedOutdoors().contains(from)) {
                            lines.add(new LinkLine(from, panelPos));
                        }
                    }
                }
            }
        }
        cachedLinkLines = List.copyOf(lines);
    }

    @SubscribeEvent
    public static void onLinkLines(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!isHoldingInstallationKey(mc)) return;

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        double cx = event.getCamera().getPosition().x;
        double cy = event.getCamera().getPosition().y;
        double cz = event.getCamera().getPosition().z;
        pose.translate(-cx, -cy, -cz);

        var buffer = mc.renderBuffers().bufferSource();
        VertexConsumer line = buffer.getBuffer(RenderType.LINES);

        for (LinkLine link : cachedLinkLines) {
            line.vertex(pose.last().pose(), link.from.getX() + 0.5f, link.from.getY() + 0.5f, link.from.getZ() + 0.5f)
                    .color(255, 255, 255, 255).normal(pose.last().normal(), 0.0f, 1.0f, 0.0f).endVertex();
            line.vertex(pose.last().pose(), link.to.getX() + 0.5f, link.to.getY() + 0.5f, link.to.getZ() + 0.5f)
                    .color(255, 255, 255, 255).normal(pose.last().normal(), 0.0f, 1.0f, 0.0f).endVertex();
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
        int w = e.getGuiGraphics().guiWidth();
        if (w < 320) {
            e.getGuiGraphics().drawString(mc.font, Component.literal(String.format("Тело %.1f°C", body)),
                    6, h - 32, 0xFFFFFFFF, true);
            e.getGuiGraphics().drawString(mc.font, Component.literal(String.format("Воздух %.1f°C", ambient)),
                    6, h - 22, 0xFFFFFFFF, true);
            e.getGuiGraphics().drawString(mc.font, Component.literal(String.format("Тепло %.0f%%", insulation * 100.0)),
                    6, h - 12, 0xFFFFFFFF, true);
        } else {
            e.getGuiGraphics().drawString(mc.font,
                    Component.literal(String.format("Тело: %.1f°C  |  Воздух: %.1f°C  |  Утепление: %.0f%%",
                            body, ambient, insulation * 100.0)),
                    10, h - 18, 0xFFFFFFFF, true);
        }

        if (!isHoldingInstallationKey(mc)) return;

        String keyHint = w < 320
                ? "Shift+ПКМ: разорвать"
                : "ПКМ: связать; Shift+ПКМ: разорвать";
        e.getGuiGraphics().drawString(mc.font, Component.literal(keyHint),
                6, w < 320 ? h - 42 : h - 34, 0xFFFFFFFF, true);
    }

    private static boolean isHoldingInstallationKey(Minecraft mc) {
        var held = mc.player.getMainHandItem();
        return !held.isEmpty() && "real_climate:installation_key".equals(
                BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
    }
}
