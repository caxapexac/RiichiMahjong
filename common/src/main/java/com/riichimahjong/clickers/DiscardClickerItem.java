package com.riichimahjong.clickers;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class DiscardClickerItem extends BlockItem {
    private final DiscardClickerBlock.Tier tier;

    public DiscardClickerItem(Block block, DiscardClickerBlock.Tier tier, Item.Properties properties) {
        super(block, properties);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("riichi_mahjong.tooltip.discard_clicker.line1",
                Component.literal(tier.accuracyPct() + "%").withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("riichi_mahjong.tooltip.discard_clicker.line2")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
