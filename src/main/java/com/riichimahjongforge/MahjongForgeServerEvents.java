package com.riichimahjongforge;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MahjongForgeServerEvents {

    private MahjongForgeServerEvents() {}

    /**
     * While sneaking with an item, vanilla can skip block activation and prioritize item use (e.g. block placement).
     * For mahjong table interactions, force block-use and deny item-use so shift+RMB always reaches
     * {@link MahjongTableBlock#use} without placing held blocks.
     */
    @SubscribeEvent
    public static void onRightClickBlockSneakForTable(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getEntity().isShiftKeyDown()) {
            return;
        }
        var st = event.getLevel().getBlockState(event.getPos());
        if (st.getBlock() instanceof MahjongTableBlock) {
            event.setUseBlock(Event.Result.ALLOW);
            event.setUseItem(Event.Result.DENY);
        }
    }

}
