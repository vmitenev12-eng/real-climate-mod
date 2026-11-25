package com.realclimate.block;

import com.realclimate.registry.ModBlockEntities;
import com.realclimate.tileentity.ThermometerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Термодатчик: измеряет фактическую температуру воздуха вокруг себя
 * (с инерцией помещения) и показывает её по ПКМ пустой рукой.
 * Ключом монтажника привязывается к настенному пульту для авторегулировки.
 */
public class ThermometerBlock extends FacingBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            Shapes.box(5.0 / 16.0, 4.0 / 16.0, 12.0 / 16.0, 11.0 / 16.0, 12.0 / 16.0, 1.0),
            Shapes.box(6.5 / 16.0, 6.5 / 16.0, 10.5 / 16.0, 9.5 / 16.0, 9.5 / 16.0, 12.0 / 16.0));

    public ThermometerBlock(Properties props) {
        super(props, SHAPE);
        registerDefaultState(stateDefinition.any().setValue(FACING, 0));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThermometerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.THERMOMETER.get()) return null;
        return level.isClientSide ? null : (level1, pos, state1, be) ->
                ThermometerBlockEntity.serverTick(level1, pos, state1, (ThermometerBlockEntity) be);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ThermometerBlockEntity thermometer) {
            player.displayClientMessage(Component.literal(
                    String.format("Температура: %.1f °C", thermometer.getMeasured())), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
