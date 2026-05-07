package com.riichimahjongforge.gimmicks;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.mahjongcore.MahjongTileItems;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 50/50 chance an EnderMan spawns carrying a 4-Man mahjong tile instead of grass. */
@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EndermanCarriesMahjongTile {

    private EndermanCarriesMahjongTile() {}

    @SubscribeEvent
    public static void onEndermanFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof EnderMan enderMan)) {
            return;
        }

        Block man4Block = MahjongTileItems.blockForCode(MahjongTileItems.CODE_MAN_4);
        if (man4Block == null) {
            enderMan.setCarriedBlock(Blocks.GRASS_BLOCK.defaultBlockState());
            return;
        }

        BlockState man4State = man4Block.defaultBlockState();
        BlockState carriedState = enderMan.getRandom().nextBoolean()
                ? Blocks.GRASS_BLOCK.defaultBlockState()
                : man4State;
        enderMan.setCarriedBlock(carriedState);
    }
}
