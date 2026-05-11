package com.riichimahjong.clickers;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class TsumoClickerItem extends BlockItem {
    public TsumoClickerItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("riichi_mahjong.tooltip.tsumo_clicker.line1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("riichi_mahjong.tooltip.tsumo_clicker.line2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
