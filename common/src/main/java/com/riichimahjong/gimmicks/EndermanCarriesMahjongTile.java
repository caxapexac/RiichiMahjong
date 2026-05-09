package com.riichimahjong.gimmicks;

import com.riichimahjong.mahjongcore.MahjongTileItems;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 50/50 chance an Enderman spawns carrying a 4-Man mahjong tile instead of grass.
 *
 * <p>Hook is loader-specific (Architectury has no finalize-spawn event):
 * NeoForge subscribes to {@code FinalizeSpawnEvent} on the game bus; Fabric uses a
 * mixin into {@code Mob.finalizeSpawn}. Both call into {@link #onFinalizeSpawn} —
 * the actual logic lives here in common so behaviour stays in lockstep.
 */
public final class EndermanCarriesMahjongTile {

    private EndermanCarriesMahjongTile() {}

    /**
     * Called once per Mob.finalizeSpawn — i.e. once per genuine spawn, never on
     * chunk-load. Each loader's hook routes here.
     */
    public static void onFinalizeSpawn(Mob mob) {
        if (!(mob instanceof EnderMan enderMan)) return;
        Block man4Block = MahjongTileItems.blockForCode(MahjongTileItems.CODE_MAN_4);
        if (man4Block == null) {
            enderMan.setCarriedBlock(Blocks.GRASS_BLOCK.defaultBlockState());
            return;
        }
        BlockState carriedState = enderMan.getRandom().nextBoolean()
                ? Blocks.GRASS_BLOCK.defaultBlockState()
                : man4Block.defaultBlockState();
        enderMan.setCarriedBlock(carriedState);
    }
}
