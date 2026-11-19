package com.realclimate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realclimate.block.ControlPanelBlock;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;

public class ControlPanelRenderer implements BlockEntityRenderer<ControlPanelBlockEntity> {
    private final Font font;

    public ControlPanelRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ControlPanelBlockEntity panel, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Direction facing = panel.getBlockState().getValue(ControlPanelBlock.FACING);
        String text = String.format(java.util.Locale.ROOT, "%.1f C", panel.getTargetTemp());

        pose.pushPose();
        double centerX = 0.5;
        double centerY = 0.70;
        double faceZ = 0.12;

        switch (facing) {
            case NORTH -> pose.translate(centerX, centerY, 1.0 - faceZ);
            case SOUTH -> {
                pose.translate(centerX, centerY, faceZ);
                pose.mulPose(new Quaternionf().rotateY((float) Math.PI));
            }
            case EAST -> {
                pose.translate(1.0 - faceZ, centerY, centerX);
                pose.mulPose(new Quaternionf().rotateY((float) (-Math.PI / 2.0)));
            }
            case WEST -> {
                pose.translate(faceZ, centerY, centerX);
                pose.mulPose(new Quaternionf().rotateY((float) (Math.PI / 2.0)));
            }
            default -> {
            }
        }

        pose.scale(0.008f, -0.008f, 0.008f);
        float width = font.width(text);
        font.drawInBatch(Component.literal(text), -width / 2.0f, 0.0f, 0xFFFFFFFF,
                false, pose.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, packedLight);
        pose.popPose();
    }
}
