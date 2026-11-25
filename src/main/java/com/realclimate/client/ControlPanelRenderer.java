package com.realclimate.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.realclimate.block.ControlPanelBlock;
import com.realclimate.block.FacingBlock;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;

/**
 * Экран настенного пульта: крупно — заданная температура, ниже мелко —
 * фактическая (в авторежиме по привязанным термодатчикам). Текст рисуется
 * на лицевой стороне пульта (кнопки выступают на 12/16 блока).
 */
public class ControlPanelRenderer implements BlockEntityRenderer<ControlPanelBlockEntity> {
    private final Font font;

    public ControlPanelRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(ControlPanelBlockEntity panel, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Direction facing = FacingBlock.indexToDirection(
                panel.getBlockState().getValue(ControlPanelBlock.FACING));
        String setText = String.format(java.util.Locale.ROOT, "%.1f°", panel.getTargetTemp());
        double measured = panel.getLastMeasured();
        String measText = Double.isNaN(measured) ? null
                : String.format(java.util.Locale.ROOT, "%.1f°", measured);

        double setY = 0.55;
        double face = 12.0 / 16.0 - 0.004;

        pose.pushPose();
        switch (facing) {
            case NORTH -> {
                pose.translate(0.5, setY, face);
                pose.mulPose(new Quaternionf().rotateY((float) Math.PI));
            }
            case SOUTH -> pose.translate(0.5, setY, 1.0 - face);
            case EAST -> {
                pose.translate(1.0 - face, setY, 0.5);
                pose.mulPose(new Quaternionf().rotateY((float) (Math.PI / 2.0)));
            }
            case WEST -> {
                pose.translate(face, setY, 0.5);
                pose.mulPose(new Quaternionf().rotateY((float) (-Math.PI / 2.0)));
            }
            default -> {
            }
        }

        pose.scale(0.02f, -0.02f, 0.02f);
        float width = font.width(setText);
        font.drawInBatch(Component.literal(setText), -width / 2.0f, -2.0f, 0xFFFFFFFF,
                false, pose.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                0, packedLight);

        // В авторежиме под уставкой показываем фактическую температуру.
        if (measText != null && panel.isAutoMode()) {
            float mw = font.width(measText);
            font.drawInBatch(Component.literal(measText), -mw / 2.0f, 9.0f, 0xFF8CE99C,
                    false, pose.last().pose(), buffer, Font.DisplayMode.POLYGON_OFFSET,
                    0, packedLight);
        }
        pose.popPose();
    }
}
