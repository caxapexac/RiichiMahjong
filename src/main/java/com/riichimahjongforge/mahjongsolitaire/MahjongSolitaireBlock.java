package com.riichimahjongforge.mahjongsolitaire;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.common.BaseMultipartBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 3×3 multipart pair-the-tiles solitaire table.
 *
 * <p>The same model AABBs as the mahjong table but flatter — the playing
 * surface is a 32×32 px slab rimmed with a 1 px lip (matches
 * {@code mahjong-solitaire.json}). Tile clicks flow through the
 * {@link com.riichimahjongforge.cuterenderer.CuteRenderer} pipeline, not this
 * block's {@link #use}; {@link #use} only handles board reset.
 *
 * <p>Reset triggers:
 * <ul>
 *   <li>Shift+RMB on the master cell with an empty hand <i>and</i> the board is
 *       empty (or no board loaded yet).</li>
 *   <li>Redstone rising edge on any cell.</li>
 * </ul>
 *
 * <p>Carry On compatibility: this block is registered with Carry On's IMC blacklist
 * (see {@code RiichiMahjongForgeMod.onInterModEnqueue}) so shift+RMB on it is not
 * intercepted by Carry On's pickup behaviour — same approach used by
 * {@code mahjong_table}.
 */
@SuppressWarnings("deprecation")
public class MahjongSolitaireBlock extends BaseMultipartBlock {

    /** Tabletop AABBs from {@code mahjong-solitaire.json}, master-cell relative. */
    private static final List<double[]> MODEL_BOXES = List.of(
            new double[] {-8, 0, -8, 24, 1, 24},
            new double[] {23, 1, -8, 24, 2, 24},
            new double[] {-8, 1, -8, -7, 2, 24},
            new double[] {-7, 1, 23, 23, 2, 24},
            new double[] {-7, 1, -8, 23, 2, -7});

    public MahjongSolitaireBlock(Properties properties) {
        super(properties, MODEL_BOXES);
    }

    @Override
    protected boolean isRotatable() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new MahjongSolitaireBlockEntity(pos, state) : null;
    }

    /**
     * Comparator output: scales linearly with the number of tiles still on the
     * board. Empty board emits 0; a fully populated board emits 15. Any non-zero
     * remaining count emits at least 1 so a comparator can detect "still
     * playing" vs. "cleared" reliably.
     */
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongSolitaireBlockEntity table)) return 0;
        int total = table.slotCount();
        int remaining = table.remainingTiles();
        if (total == 0 || remaining == 0) return 0;
        return Math.max(1, Math.min(15, (15 * remaining) / total));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongSolitaireBlockEntity table)) {
            return InteractionResult.PASS;
        }
        boolean shift = player.isShiftKeyDown();
        boolean emptyHand = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
        if (shift && emptyHand && isMaster(state) && table.isBoardEmpty()) {
            table.resetBoard();
            return InteractionResult.CONSUME;
        }
        // Tile clicks are handled by CuteRenderer's input handler; non-reset RMBs
        // on this block fall through silently.
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (!isMaster(state)) {
            return List.of();
        }
        ItemStack stack = new ItemStack(this);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MahjongSolitaireBlockEntity) {
            CompoundTag beTag = be.saveWithoutMetadata();
            if (!beTag.isEmpty()) {
                stack.getOrCreateTag().put("BlockEntityTag", beTag);
            }
        }
        return List.of(stack);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block fromBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, fromBlock, fromPos, isMoving);
        if (level.isClientSide()) return;
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongSolitaireBlockEntity table)) {
            return;
        }
        // Rising-edge detection mirrors the mahjong table; pulled into the BE so
        // the lastPowered bit is part of the synced state.
        boolean nowPowered = anyPartPowered(level, master);
        if (nowPowered && !table.wasPowered()) {
            table.resetBoard();
        }
        table.setPowered(nowPowered);
    }

    private static boolean anyPartPowered(Level level, BlockPos master) {
        for (int dx = -CENTER; dx <= CENTER; dx++) {
            for (int dz = -CENTER; dz <= CENTER; dz++) {
                if (level.hasNeighborSignal(master.offset(dx, 0, dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Note: no SneakBypass override here, unlike the mahjong table. Solitaire
    // is played with empty hands anyway — vanilla already routes shift+RMB
    // through block.use() when no item is held, so the empty-board reset still
    // works. Without the override, players holding a block while shift-clicking
    // can place blocks on/around the table normally, which is the natural
    // expectation.
}
