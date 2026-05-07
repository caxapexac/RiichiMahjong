package com.riichimahjongforge.mahjongaltar;

import com.riichimahjongforge.RiichiMahjongForgeMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class MahjongAltarBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(
            box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0),
            box(3.0, 1.0, 3.0, 13.0, 10.0, 13.0),
            box(4.0, 10.0, 4.0, 12.0, 11.0, 12.0));

    public MahjongAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MahjongAltarBlockEntity altar)) {
            return InteractionResult.PASS;
        }
        boolean changed = player.getItemInHand(hand).isEmpty()
                ? (player.isShiftKeyDown()
                        ? altar.extractAllToPlayer(serverPlayer)
                        : altar.extractOneToPlayer(serverPlayer))
                : altar.insertOneFromPlayer(serverPlayer, hand);
        return changed ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MahjongAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(
                type,
                RiichiMahjongForgeMod.MAHJONG_ALTAR_BLOCK_ENTITY.get(),
                MahjongAltarBlockEntity::serverTick);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MahjongAltarBlockEntity altar) {
                Containers.dropContents(level, pos, altar);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
