package com.realclimate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realclimate.block.FacingBlock;
import com.realclimate.block.ThermometerBlock;
import com.realclimate.tileentity.ThermometerBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;

/**
 * Цифры температуры прямо на корпусе термодатчика: видно издалека,
 * кликать по блоку не нужно. Рисуется на торце выступающего сенсора.
 */
public class ThermometerRenderer implements BlockEntityRenderer<ThermometerBlockEntity> {
    private static final float SCALE = 0.008f;
    private final Font font;

    public ThermometerRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ThermometerBlockEntity thermometer, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        double measured = thermometer.getMeasured();
        if (Double.isNaN(measured)) return;
        String text = String.format(java.util.Locale.ROOT, "%.1f°", measured);

        Direction facing = FacingBlock.indexToDirection(
                thermometer.getBlockState().getValue(ThermometerBlock.FACING));
        // Сенсор выступает с лицевой стороны до z = 10.5/16.
        double y = 0.5;
        double face = 10.5 / 16.0 - 0.004;

        pose.pushPose();
        switch (facing) {
            case NORTH -> {
                pose.translate(0.5, y, face);
                pose.mulPose(new Quaternionf().rotateY((float) Math.PI));
            }
            case SOUTH -> pose.translate(0.5, y, 1.0 - face);
            case EAST -> {
                pose.translate(1.0 - face, y, 0.5);
                pose.mulPose(new Quaternionf().rotateY((float) (Math.PI / 2.0)));
            }
            case WEST -> {
                pose.translate(face, y, 0.5);
                pose.mulPose(new Quaternionf().rotateY((float) (-Math.PI / 2.0)));
            }
            default -> {
            }
        }

        pose.scale(SCALE, -SCALE, SCALE);
        float width = font.width(text);
        font.drawInBatch(Component.literal(text), -width / 2.0f, -2.0f, 0xFFFFFFFF,
                false, pose.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, packedLight);
        pose.popPose();
    }
}
