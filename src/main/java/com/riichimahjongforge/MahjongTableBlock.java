package com.riichimahjongforge;

import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.List;

import com.riichimahjongforge.nbt.MahjongMatchDefinitionNbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.riichimahjongforge.menu.MahjongTableSettingsMenu;
import com.riichimahjongforge.menu.MahjongTableInventoryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.Containers;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * In 1.20.1, Mojang deprecates several {@link net.minecraft.world.level.block.state.BlockBehaviour} overrides (including
 * {@code use} and {@code getRenderShape}) without a stable replacement; Forge blocks still implement these hooks.
 */
@SuppressWarnings("deprecation")
public class MahjongTableBlock extends BaseEntityBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static void logTableSlotClick(
            BlockPos masterPos,
            BlockState clickedState,
            Vec3 hitVec,
            String handDesc,
            String discardDesc,
            @Nullable Integer clickerSeat,
            boolean inMatch,
            boolean allowInvInMatch,
            String branch) {
        LOGGER.info(
                "[MahjongTable/slotClick] master={} clickedPart=({}, {}) hit={} | hand: {} | discard: {} |"
                        + " clickerSeat={} inMatch={} allowInvInMatch={} -> {}",
                masterPos,
                clickedState.getValue(PART_X),
                clickedState.getValue(PART_Z),
                hitVec,
                handDesc,
                discardDesc,
                clickerSeat,
                inMatch,
                allowInvInMatch,
                branch);
    }

    private static final TagKey<Item> WRENCH_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));
    /**
     * Table is a 3x3 multiblock for correct collision on the overhanging model corners.
     * The center block (part 1,1) is the master and owns the {@link MahjongTableBlockEntity}.
     */
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 2);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private static final List<double[]> MODEL_BOXES = List.of(
            new double[] {0, 0, 0, 16, 8, 16},
            new double[] {-8, 8, -8, 24, 9, 24},
            new double[] {23, 9, -8, 24, 10, 24},
            new double[] {-8, 9, -8, -7, 10, 24},
            new double[] {-7, 9, 23, 23, 10, 24},
            new double[] {-7, 9, -8, 23, 10, -7},
            new double[] {6, 8.5, 6, 10, 9.5, 10});

    private static final VoxelShape[] PART_SHAPES = buildPartShapes();

    public MahjongTableBlock(Properties properties) {
        super(properties.lightLevel(st -> st.getValue(LIT) ? 12 : 0));
        registerDefaultState(defaultBlockState().setValue(PART_X, 1).setValue(PART_Z, 1).setValue(LIT, false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        if (!isMaster(state)) {
            return RenderShape.INVISIBLE;
        }
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shapeFor(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!isMaster(state)) {
            return null;
        }
        return new MahjongTableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        if (!isMaster(state)) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof MahjongTableBlockEntity table && lvl instanceof ServerLevel sl) {
                table.serverTick(sl);
            }
        };
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos center = ctx.getClickedPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = center.offset(dx, 0, dz);
                if (!level.getBlockState(p).canBeReplaced(ctx)) {
                    return null;
                }
            }
        }
        return defaultBlockState().setValue(PART_X, 1).setValue(PART_Z, 1).setValue(LIT, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        if (!isMaster(state)) {
            return;
        }
        for (int px = 0; px <= 2; px++) {
            for (int pz = 0; pz <= 2; pz++) {
                if (px == 1 && pz == 1) {
                    continue;
                }
                BlockPos p = pos.offset(px - 1, 0, pz - 1);
                BlockState partState = state.setValue(PART_X, px).setValue(PART_Z, pz).setValue(LIT, state.getValue(LIT));
                level.setBlock(p, partState, 3);
            }
        }
        if (stack.is(RiichiMahjongForgeMod.FILLED_MAHJONG_TABLE_ITEM.get())
                && level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) {
            table.fillTableWithFullTileSet();
        }
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.CONSUME;
        }
        BlockPos masterPos = masterPos(pos, state);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(masterPos);
        if (!(be instanceof MahjongTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        boolean isCenter = isMaster(state);

        if (player.isShiftKeyDown() && holdsWrench(player) && isCenter) {
            NetworkHooks.openScreen(
                    serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new MahjongTableSettingsMenu(windowId, inv, table),
                            Component.translatable("riichi_mahjong_forge.screen.table_settings.title")),
                    buf -> {
                        buf.writeBlockPos(masterPos);
                        MahjongMatchDefinitionNbt.write(buf, table.getRules());
                    });
            return InteractionResult.CONSUME;
        }

        // Tooling actions (keep separate from gameplay click rules).
        if (!player.isShiftKeyDown() && isCenter && holdsWrench(player)) {
            NetworkHooks.openScreen(
                    serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new MahjongTableInventoryMenu(windowId, inv, table),
                            Component.translatable("riichi_mahjong_forge.screen.table_inventory.title")),
                    buf -> {
                        buf.writeBlockPos(masterPos);
                        buf.writeVarInt(MahjongTableInventoryMenu.Section.TABLE_TILES.ordinal());
                    });
            return InteractionResult.CONSUME;
        }

        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof MahjongTableRecordItem recordItem) {
            if (player.isShiftKeyDown()) {
                if (!MahjongTableRecordItem.hasRecordedTableState(held)) {
                    recordItem.tryWriteFromTable(serverPlayer, table, held);
                }
                return InteractionResult.CONSUME;
            }
            if (MahjongTableRecordItem.hasRecordedTableState(held)) {
                recordItem.tryLoadIntoTable(serverPlayer, table, held);
            }
            return InteractionResult.CONSUME;
        }

        if (hit.getDirection() == Direction.UP) {
            var hitVec = hit.getLocation();
            if (isCenter && table.getMatchPhase() == TableMatchPhase.HAND_RESULT) {
                table.tryStartNextHandFromCenter(serverPlayer);
                logTableSlotClick(
                        masterPos,
                        state,
                        hitVec,
                        "n/a",
                        "n/a",
                        table.seatIndexForPlayer(serverPlayer),
                        true,
                        table.allowInventoryEditWhileInMatch(),
                        "CENTER_NEXT_HAND_START");
                return InteractionResult.CONSUME;
            }
            MahjongTableTabletopSlots.SurfacePickCandidates candidates =
                    MahjongTableTabletopSlots.collectSurfacePickCandidates(table, hitVec);
            MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolved =
                    MahjongTableTabletopSlots.resolveSurfaceInteraction(
                            table, candidates, player.getItemInHand(hand).isEmpty());
            Integer clickerSeat = table.seatIndexForPlayer(serverPlayer);
            boolean inMatch = table.isInMatch();
            boolean allowInvInMatch = table.allowInventoryEditWhileInMatch();
            if (inMatch && !allowInvInMatch && clickerSeat != null) {
                var actions = table.visibleSeatActionRow(clickerSeat);
                var actionPick = MahjongTableTabletopSlots.pickSeatActionChip(table, clickerSeat, hitVec, actions.size());
                if (actionPick != null) {
                    MahjongTableBlockEntity.SeatAction action = actions.get(actionPick.actionIndex());
                    table.handleSeatActionButton(serverPlayer, action);
                    logTableSlotClick(
                            masterPos,
                            state,
                            hitVec,
                            "n/a",
                            "n/a",
                            clickerSeat,
                            inMatch,
                            allowInvInMatch,
                            "ACTION_CHIP_" + action);
                    return InteractionResult.CONSUME;
                }
            }
            String handDesc =
                    candidates.handPick() == null
                            ? "none"
                            : ("seat=" + candidates.handPick().seat() + " edge="
                                    + MahjongTableBlockEntity.tableEdgeFromSeat(candidates.handPick().seat()) + " slot="
                                    + candidates.handPick().slotIndex() + " inv=" + candidates.handInv());
            String discardDesc =
                    candidates.discardPick() == null
                            ? "none"
                            : ("seat=" + candidates.discardPick().seat() + " edge="
                                    + MahjongTableBlockEntity.tableEdgeFromSeat(candidates.discardPick().seat()) + " slot="
                                    + candidates.discardPick().slotIndex() + " inv=" + candidates.discardInv());

            if (resolved != null && resolved.kind() == MahjongTableTabletopSlots.SurfaceInteractionKind.HAND) {
                String branch;
                if (inMatch && !allowInvInMatch) {
                    if (clickerSeat != null && resolved.seat() == clickerSeat) {
                        branch = "HAND_MATCH_USE";
                        table.onHandSlotInteraction(serverPlayer, resolved.seat(), resolved.slotIndex());
                    } else if (clickerSeat != null) {
                        branch = "HAND_MATCH_TURN_USE";
                        table.handleInMatchUse(serverPlayer, clickerSeat);
                    } else {
                        branch = "HAND_MATCH_CONSUME_NO_EDIT";
                    }
                    logTableSlotClick(
                            masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                    return InteractionResult.CONSUME;
                }
                branch = "HAND_FREE_EDIT";
                logTableSlotClick(
                        masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                table.onHandSlotFreeEdit(serverPlayer, hand, resolved.seat(), resolved.slotIndex());
                return InteractionResult.CONSUME;
            }
            if (resolved != null && resolved.kind() == MahjongTableTabletopSlots.SurfaceInteractionKind.DISCARD) {
                String branch;
                if (inMatch && !allowInvInMatch) {
                    if (clickerSeat != null) {
                        branch = "DISCARD_MATCH_TURN_USE";
                        table.handleInMatchUse(serverPlayer, clickerSeat);
                    } else {
                        branch = "DISCARD_MATCH_CONSUME_NO_EDIT";
                    }
                    logTableSlotClick(
                            masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                    return InteractionResult.CONSUME;
                }
                branch = "DISCARD_FREE_EDIT";
                logTableSlotClick(
                        masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                table.onDiscardSlotFreeEdit(serverPlayer, hand, resolved.seat(), resolved.slotIndex());
                return InteractionResult.CONSUME;
            }
            if (resolved != null && resolved.kind() == MahjongTableTabletopSlots.SurfaceInteractionKind.MELD) {
                String branch;
                if (inMatch && !allowInvInMatch) {
                    if (clickerSeat != null) {
                        branch = "MELD_MATCH_TURN_USE";
                        table.handleInMatchUse(serverPlayer, clickerSeat);
                    } else {
                        branch = "MELD_MATCH_CONSUME_NO_EDIT";
                    }
                    logTableSlotClick(
                            masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                    return InteractionResult.CONSUME;
                }
                branch = "MELD_FREE_EDIT seat=" + resolved.seat() + " slot=" + resolved.slotIndex() + " inv=" + resolved.invSlot();
                logTableSlotClick(
                        masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                table.onOpenMeldSlotFreeEdit(serverPlayer, hand, resolved.seat(), resolved.slotIndex());
                return InteractionResult.CONSUME;
            }
            if (resolved != null && resolved.kind() == MahjongTableTabletopSlots.SurfaceInteractionKind.WALL) {
                String branch;
                if (inMatch && !allowInvInMatch) {
                    if (clickerSeat != null) {
                        branch = "WALL_MATCH_TURN_USE";
                        table.handleInMatchUse(serverPlayer, clickerSeat);
                    } else {
                        branch = "WALL_MATCH_CONSUME_NO_EDIT";
                    }
                    logTableSlotClick(
                            masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                    return InteractionResult.CONSUME;
                }
                branch =
                        resolved.deadWall()
                                ? "DEAD_WALL_FREE_EDIT stack=" + resolved.slotIndex() + " inv=" + resolved.invSlot()
                                : "WALL_FREE_EDIT stack=" + resolved.slotIndex() + " inv=" + resolved.invSlot();
                logTableSlotClick(
                        masterPos, state, hitVec, handDesc, discardDesc, clickerSeat, inMatch, allowInvInMatch, branch);
                table.onWallStackFreeEdit(serverPlayer, hand, resolved.deadWall(), resolved.slotIndex());
                return InteractionResult.CONSUME;
            }
            if (resolved == null && isCenter && table.tryPlaceHeldTileIntoTableStorage(serverPlayer, hand)) {
                logTableSlotClick(
                        masterPos,
                        state,
                        hitVec,
                        handDesc,
                        discardDesc,
                        clickerSeat,
                        inMatch,
                        allowInvInMatch,
                        "CENTER_PLACE_TO_TABLE_STORAGE");
                return InteractionResult.CONSUME;
            }
            logTableSlotClick(
                    masterPos,
                    state,
                    hitVec,
                    handDesc,
                    discardDesc,
                    clickerSeat,
                    inMatch,
                    allowInvInMatch,
                    "NO_TABLE_HAND_DISCARD_OR_WALL_SLOT");
        }

        if (table.isInMatch()) {
            if (isCenter && table.getMatchPhase() == TableMatchPhase.HAND_RESULT) {
                table.tryStartNextHandFromCenter(serverPlayer);
                return InteractionResult.CONSUME;
            }
            if (!isCenter && player.isShiftKeyDown()) {
                return InteractionResult.CONSUME;
            }
            Integer seat = table.seatIndexForPlayer(serverPlayer);
            if (seat == null) {
                serverPlayer.displayClientMessage(
                        Component.translatable("riichi_mahjong_forge.chat.game.not_seated"), true);
                return InteractionResult.CONSUME;
            }
            table.handleInMatchUse(serverPlayer, seat);
            return InteractionResult.CONSUME;
        }

        // WAITING click rules:
        // - center: start
        // - edge: join/leave only
        if (isCenter) {
            table.quickStartWithBotsFromTableTop(serverPlayer);
            return InteractionResult.CONSUME;
        }

        int seat = seatFromClickedPart(state, hit.getDirection());
        if (seat < 0) {
            serverPlayer.displayClientMessage(Component.translatable("riichi_mahjong_forge.chat.lobby.use_side"), true);
            return InteractionResult.CONSUME;
        }
        if (holdsWrench(player)) {
            table.toggleSeatOpen(serverPlayer, seat);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.CONSUME;
        }
        table.handleFaceUse(serverPlayer, seat);
        return InteractionResult.CONSUME;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos master = masterPos(pos, state);
            if (!master.equals(pos)) {
                // Break initiated on a part: break master with drops, then clear the remaining parts.
                if (level.getBlockEntity(master) instanceof MahjongTableBlockEntity table) {
                    Containers.dropContents(level, master, table);
                    table.onTableBlockRemoved();
                }
                if (level.getBlockState(master).is(this)) {
                    level.destroyBlock(master, !player.isCreative(), player);
                }
                clearRemainingParts(level, master);
                return;
            }
            if (level.getBlockEntity(master) instanceof MahjongTableBlockEntity table) {
                Containers.dropContents(level, master, table);
                table.onTableBlockRemoved();
            }
            clearRemainingParts(level, master);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** True if main or offhand holds an item in {@code c:tools/wrench}. */
    public static boolean holdsWrench(Player player) {
        return player.getMainHandItem().is(WRENCH_TAG) || player.getOffhandItem().is(WRENCH_TAG);
    }

    /**
     * Seat selection for multiblock interactions.
     *
     * <p>We choose the seat primarily by which 3x3 table part you clicked (PART_X / PART_Z), not by the clicked face.
     * This makes "click any side of the north rim" consistently mean "north seat", etc.
     *
     * <p>Corners are ambiguous (touching two rims). For corners, we fall back to clicked face direction.
     */
    private static int seatFromClickedPart(BlockState state, Direction face) {
        int px = state.getValue(PART_X);
        int pz = state.getValue(PART_Z);
        boolean edgeX = px == 0 || px == 2;
        boolean edgeZ = pz == 0 || pz == 2;
        boolean corner = edgeX && edgeZ;
        if (corner && face.getAxis().isHorizontal()) {
            return seatFromHorizontalFace(face);
        }
        if (pz == 0) {
            return 0;
        }
        if (px == 2) {
            return 1;
        }
        if (pz == 2) {
            return 2;
        }
        if (px == 0) {
            return 3;
        }
        return -1;
    }

    private static int seatFromHorizontalFace(Direction face) {
        return switch (face) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> -1;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PART_X, PART_Z, LIT);
    }

    private static boolean isMaster(BlockState state) {
        return state.getValue(PART_X) == 1 && state.getValue(PART_Z) == 1;
    }

    private static BlockPos masterPos(BlockPos pos, BlockState state) {
        int px = state.getValue(PART_X);
        int pz = state.getValue(PART_Z);
        return pos.offset(1 - px, 0, 1 - pz);
    }

    /**
     * Resolves any 3×3 multiblock part position to the master block position that owns the
     * {@link MahjongTableBlockEntity}. For non-table blocks, returns {@code partPos} unchanged.
     */
    public static BlockPos masterBlockPos(BlockPos partPos, BlockState state) {
        if (!(state.getBlock() instanceof MahjongTableBlock)) {
            return partPos;
        }
        return masterPos(partPos, state);
    }

    private static void clearRemainingParts(Level level, BlockPos masterPos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = masterPos.offset(dx, 0, dz);
                if (p.equals(masterPos)) {
                    continue;
                }
                BlockState st = level.getBlockState(p);
                if (st.is(RiichiMahjongForgeMod.MAHJONG_TABLE.get())) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 35);
                }
            }
        }
    }

    private static VoxelShape shapeFor(BlockState state) {
        int px = state.getValue(PART_X);
        int pz = state.getValue(PART_Z);
        int idx = px + pz * 3;
        return idx >= 0 && idx < PART_SHAPES.length ? PART_SHAPES[idx] : Shapes.empty();
    }

    private static VoxelShape[] buildPartShapes() {
        VoxelShape[] out = new VoxelShape[9];
        for (int px = 0; px <= 2; px++) {
            for (int pz = 0; pz <= 2; pz++) {
                out[px + pz * 3] = buildShapeForPart(px, pz);
            }
        }
        return out;
    }

    private static VoxelShape buildShapeForPart(int partX, int partZ) {
        double cellMinX = (partX - 1) * 16.0;
        double cellMinZ = (partZ - 1) * 16.0;
        double cellMaxX = cellMinX + 16.0;
        double cellMaxZ = cellMinZ + 16.0;

        ArrayList<VoxelShape> shapes = new ArrayList<>();
        for (double[] b : MODEL_BOXES) {
            double x1 = b[0];
            double y1 = b[1];
            double z1 = b[2];
            double x2 = b[3];
            double y2 = b[4];
            double z2 = b[5];

            double ix1 = Math.max(x1, cellMinX);
            double iz1 = Math.max(z1, cellMinZ);
            double ix2 = Math.min(x2, cellMaxX);
            double iz2 = Math.min(z2, cellMaxZ);
            if (ix2 <= ix1 || iz2 <= iz1 || y2 <= y1) {
                continue;
            }
            shapes.add(box(
                    ix1 - cellMinX,
                    y1,
                    iz1 - cellMinZ,
                    ix2 - cellMinX,
                    y2,
                    iz2 - cellMinZ));
        }
        if (shapes.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape s = shapes.get(0);
        for (int i = 1; i < shapes.size(); i++) {
            s = Shapes.or(s, shapes.get(i));
        }
        return s;
    }
}
