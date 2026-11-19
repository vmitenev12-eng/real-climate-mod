package com.realclimate.item;

import com.realclimate.tileentity.ACIndoorBlockEntity;
import com.realclimate.tileentity.ACOutdoorBlockEntity;
import com.realclimate.tileentity.ControlPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class InstallationKeyItem extends Item {
    private static final String SELECTION_KEY = "real_climate_selected_outdoor";

    public InstallationKeyItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos pos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(pos);

        if (player.isShiftKeyDown()) {
            if (be instanceof ACOutdoorBlockEntity outdoor) {
                for (BlockPos indoorPos : new java.util.HashSet<>(outdoor.getLinkedIndoors())) {
                    if (level.getBlockEntity(indoorPos) instanceof ACIndoorBlockEntity indoor) indoor.setLinkedOutdoor(null);
                }
                for (BlockPos panelPos : new java.util.HashSet<>(outdoor.getLinkedPanels())) {
                    if (level.getBlockEntity(panelPos) instanceof ControlPanelBlockEntity panel) panel.unlinkOutdoor(pos);
                }
                outdoor.unlinkAll();
                outdoor.unlinkAllPanels();
                sync(level, pos, outdoor);
                player.displayClientMessage(Component.literal("Связи наружного блока сброшены"), true);
            } else if (be instanceof ControlPanelBlockEntity panel) {
                for (BlockPos outdoorPos : new java.util.HashSet<>(panel.getLinkedOutdoors())) {
                    if (level.getBlockEntity(outdoorPos) instanceof ACOutdoorBlockEntity outdoor) outdoor.unlinkPanel(pos);
                }
                panel.unlinkAll();
                sync(level, pos, panel);
                player.displayClientMessage(Component.literal("Связи пульта сброшены"), true);
            } else if (be instanceof ACIndoorBlockEntity indoor && indoor.getLinkedOutdoor() != null) {
                BlockPos outdoorPos = indoor.getLinkedOutdoor();
                BlockEntity outdoorBe = level.getBlockEntity(outdoorPos);
                if (outdoorBe instanceof ACOutdoorBlockEntity outdoor) {
                    outdoor.unlinkIndoor(pos);
                    sync(level, outdoorPos, outdoor);
                }
                indoor.setLinkedOutdoor(null);
                sync(level, pos, indoor);
                player.displayClientMessage(Component.literal("Связь внутреннего блока разорвана"), true);
            }
            clearSelection(player);
            return InteractionResult.CONSUME;
        }

        if (be instanceof ACOutdoorBlockEntity outdoor) {
            setSelection(player, pos);
            player.displayClientMessage(Component.literal("Наружный блок выбран. Выберите пульт или внутренний блок"), true);
            return InteractionResult.CONSUME;
        }

        if (be instanceof ControlPanelBlockEntity panel) {
            BlockPos outdoorPos = getSelection(player);
            if (outdoorPos == null) {
                player.displayClientMessage(Component.literal("Сначала ПКМ по наружному блоку кондиционера"), true);
                return InteractionResult.CONSUME;
            }
            BlockEntity outdoorBe = level.getBlockEntity(outdoorPos);
            if (!(outdoorBe instanceof ACOutdoorBlockEntity outdoor)) {
                clearSelection(player);
                player.displayClientMessage(Component.literal("Наружный блок не найден или выгружен"), true);
                return InteractionResult.CONSUME;
            }
            panel.linkOutdoor(outdoorPos);
            outdoor.linkPanel(pos);
            outdoor.setChanged();
            panel.setChanged();
            sync(level, outdoorPos, outdoor);
            sync(level, pos, panel);
            player.displayClientMessage(Component.literal("Пульт подключен к наружному кондиционеру"), true);
            clearSelection(player);
            return InteractionResult.CONSUME;
        }

        if (be instanceof ACIndoorBlockEntity indoor) {
            BlockPos outdoorPos = getSelection(player);
            if (outdoorPos == null) {
                player.displayClientMessage(Component.literal("Сначала ПКМ по наружному блоку кондиционера"), true);
                return InteractionResult.CONSUME;
            }
            BlockEntity outdoorBe = level.getBlockEntity(outdoorPos);
            if (!(outdoorBe instanceof ACOutdoorBlockEntity outdoor)) {
                clearSelection(player);
                player.displayClientMessage(Component.literal("Наружный блок не найден или выгружен"), true);
                return InteractionResult.CONSUME;
            }
            if (indoor.getLinkedOutdoor() != null && !indoor.getLinkedOutdoor().equals(outdoorPos)
                    && level.getBlockEntity(indoor.getLinkedOutdoor()) instanceof ACOutdoorBlockEntity previous) {
                previous.unlinkIndoor(pos);
            }
            if (outdoor.linkIndoor(pos)) {
                indoor.setLinkedOutdoor(outdoorPos);
                outdoor.setChanged();
                indoor.setChanged();
                sync(level, outdoorPos, outdoor);
                sync(level, pos, indoor);
                player.displayClientMessage(Component.literal("Внутренний блок подключен к наружному кондиционеру"), true);
            } else {
                player.displayClientMessage(Component.literal("Максимум: 3 внутренних блока"), true);
            }
            clearSelection(player);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static void setSelection(Player player, BlockPos pos) {
        player.getPersistentData().putLong(SELECTION_KEY, pos.asLong());
    }

    private static BlockPos getSelection(Player player) {
        return player.getPersistentData().contains(SELECTION_KEY)
                ? BlockPos.of(player.getPersistentData().getLong(SELECTION_KEY)) : null;
    }

    private static void clearSelection(Player player) {
        player.getPersistentData().remove(SELECTION_KEY);
    }

    private static void sync(Level level, BlockPos pos, BlockEntity blockEntity) {
        level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
    }
}
