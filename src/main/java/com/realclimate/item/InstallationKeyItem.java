package com.realclimate.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class InstallationKeyItem extends Item {
    public InstallationKeyItem(Properties props) { super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            // Открываем экран привязки (клиентская часть)
            net.minecraftforge.network.NetworkDirection dir = null;
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}
