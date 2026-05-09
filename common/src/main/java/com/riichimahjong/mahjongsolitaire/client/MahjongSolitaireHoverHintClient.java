package com.riichimahjong.mahjongsolitaire.client;

import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.common.client.HoverHintOverlay;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Action-bar hint for the solitaire table. Shown only when the table is empty
 * (no board loaded or all tiles cleared) — telling the player how to deal a new
 * board. Returns null otherwise so a non-empty board doesn't display a hint
 * suggesting reset (and the block won't react to shift+RMB anyway, by design).
 */
public final class MahjongSolitaireHoverHintClient {

    private static final String K_EMPTY = "riichi_mahjong.hint.solitaire.empty";

    private MahjongSolitaireHoverHintClient() {}

    public static void register() {
        HoverHintOverlay.register(MahjongSolitaireHoverHintClient::resolve);
    }

    @Nullable
    private static String resolve(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockState state = mc.level.getBlockState(bhr.getBlockPos());
        if (!(state.getBlock() instanceof MahjongSolitaireBlock)) return null;
        BlockPos master = BaseMultipartBlock.masterPos(bhr.getBlockPos(), state);
        BlockEntity be = mc.level.getBlockEntity(master);
        if (!(be instanceof MahjongSolitaireBlockEntity table)) return null;
        return table.isBoardEmpty() ? K_EMPTY : null;
    }
}
