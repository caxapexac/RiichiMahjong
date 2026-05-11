package com.riichimahjong.yakugenerator;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.riichimahjong.registry.ModBlockEntities;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
public class YakuGeneratorBlock extends BaseEntityBlock {

    public enum Tier {
        TIER_1(1, 24, 5),
        TIER_2(2, 40, 10),
        TIER_3(3, 70, 14);

        private final int index;
        private final int drawLimit;
        private final int slotCount;

        Tier(int index, int drawLimit, int slotCount) {
            this.index = index;
            this.drawLimit = drawLimit;
            this.slotCount = slotCount;
        }

        public int index() {
            return index;
        }

        public int drawLimit() {
            return drawLimit;
        }

        public int slotCount() {
            return slotCount;
        }
    }

    public static final MapCodec<YakuGeneratorBlock> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    propertiesCodec(),
                    com.mojang.serialization.Codec.STRING.fieldOf("tier")
                            .xmap(Tier::valueOf, Tier::name)
                            .forGetter(YakuGeneratorBlock::tier)
            ).apply(instance, YakuGeneratorBlock::new));

    private static final VoxelShape SHAPE = Shapes.or(
            box(1.0, 0.0, 1.0, 15.0, 10.0, 15.0),
            box(15.0, 0.0, 2.0, 16.0, 9.0, 14.0),
            box(0.0, 0.0, 2.0, 1.0, 9.0, 14.0),
            box(2.0, 0.0, 15.0, 14.0, 9.0, 16.0),
            box(2.0, 0.0, 0.0, 14.0, 9.0, 1.0),
            box(5.5, 10.0, 4.5, 10.5, 12.0, 11.5));

    private final Tier tier;

    public YakuGeneratorBlock(Properties properties, Tier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    protected MapCodec<YakuGeneratorBlock> codec() {
        return CODEC;
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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return openMenu(level, pos, player) ? ItemInteractionResult.CONSUME : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit) {
        return openMenu(level, pos, player) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    private boolean openMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return true;
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof YakuGeneratorBlockEntity machine)) return false;
        MenuRegistry.openExtendedMenu(
                serverPlayer,
                new SimpleMenuProvider(
                        (windowId, inventory, p) -> new YakuGeneratorMenu(windowId, inventory, machine),
                        Component.translatable("riichi_mahjong.screen.yaku_generator.title")),
                buf -> buf.writeBlockPos(pos));
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new YakuGeneratorBlockEntity(pos, state);
    }

    public Tier tier() {
        return tier;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof YakuGeneratorBlockEntity machine) {
            return machine.getComparatorSignal();
        }
        return 0;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(
                type,
                ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get(),
                YakuGeneratorBlockEntity::serverTick);
    }
}
