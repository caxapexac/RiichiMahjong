package com.riichimahjongforge.mahjongtable;

import com.mojang.logging.LogUtils;
import com.riichimahjongforge.mahjongtable.record.MahjongTableRecordItem;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.common.BaseMultipartBlock;

import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

/**
 * 3×3 multipart host for the mahjong table. Geometry, placement, and breakage
 * lifecycle live in {@link BaseMultipartBlock}; this class wires the BE/ticker,
 * dispatches right-clicks by (wrench × shift × cell), and forwards redstone pulses.
 *
 * <p><b>Idle interactions:</b>
 * <ul>
 *   <li>Wrench + RMB on centre → open inventory menu (settings tab will live here).</li>
 *   <li>Wrench + RMB on edge cell → cycle that seat between BOT and HUMAN, kicking any occupant.</li>
 *   <li>Shift + RMB on centre → start the match. With zero humans seated, also auto-seats
 *       up to {@link MahjongTableBlockEntity#DEFAULT_SEAT_COUNT} nearby players first.</li>
 *   <li>Shift + RMB on edge cell → claim that seat for the clicker.</li>
 * </ul>
 *
 * <p><b>Game interactions:</b> none yet — driver-state inputs (discard, pass, win) are
 * the next step.
 */
@SuppressWarnings("deprecation")
public class MahjongTableBlock extends BaseMultipartBlock {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Conventional Forge wrench tag — used by most 1.20.1 Forge mods (Mekanism, etc.). */
    private static final TagKey<Item> WRENCH_TAG_FORGE =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench"));
    /** Cross-loader common tag — gaining adoption from Fabric-side. */
    private static final TagKey<Item> WRENCH_TAG_COMMON =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    /** Tabletop model AABBs in model-space pixels, master-cell relative. */
    private static final List<double[]> MODEL_BOXES = List.of(
            new double[] {0, 0, 0, 16, 8, 16},
            new double[] {-8, 8, -8, 24, 9, 24},
            new double[] {23, 9, -8, 24, 10, 24},
            new double[] {-8, 9, -8, -7, 10, 24},
            new double[] {-7, 9, 23, 23, 10, 24},
            new double[] {-7, 9, -8, 23, 10, -7},
            new double[] {6, 8.5, 6, 10, 9.5, 10});

    public MahjongTableBlock(Properties properties) {
        super(properties, MODEL_BOXES);
    }

    @Override
    protected boolean isRotatable() {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new MahjongTableBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || !isMaster(state)) {
            return null;
        }
        return MahjongTableBlockEntity.serverTicker();
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
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        // Record-item dispatch: empty record + IDLE → save current BE state into the
        // stack. Filled record + IDLE → load it back. Predefined-fixture records
        // (subclasses of MahjongTableRecordItem) also enter via this path; their
        // applyToTable injects a hardcoded match. Reject record use during GAME.
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof MahjongTableRecordItem record && player instanceof ServerPlayer sp) {
            if (table.state() != MahjongTableBlockEntity.State.IDLE) {
                sp.displayClientMessage(
                        Component.literal("End the current match before using a table record."), true);
                return InteractionResult.CONSUME;
            }
            boolean filled = record.isPredefinedFixtureForRouting()
                    || MahjongTableRecordItem.hasRecordedTableState(held);
            if (filled) {
                record.applyToTable(sp, table, held);
            } else {
                record.recordFromTable(sp, table, held);
            }
            return InteractionResult.CONSUME;
        }

        if (table.state() == MahjongTableBlockEntity.State.GAME) {
            // Result screen up after a round ends? Centre RMB advances to the
            // next round; clicks on edge cells are ignored. Manual advance is
            // by design — we don't auto-roll into the next round so players
            // can read the result.
            if (table.isInResultPhase()) {
                if (player instanceof ServerPlayer sp && !holdsWrench(player)) {
                    if (isMaster(state)) {
                        table.advanceRoundAfterResult();
                    } else {
                        sp.displayClientMessage(
                                Component.literal("Click the table centre to start the next round."),
                                true);
                    }
                }
                return InteractionResult.CONSUME;
            }
            // Plain or sneak RMB on the table block (no cute interactive hit) →
            // contextual default: discard drawn tile during AwaitingDiscard,
            // pass during AwaitingClaims. Wrench still takes precedence for
            // settings/end-game flows once those land in GAME state.
            if (!holdsWrench(player) && player instanceof ServerPlayer sp) {
                table.onTableRightClick(sp);
            }
            return InteractionResult.CONSUME;
        }

        int partX = state.getValue(PART_X);
        int partZ = state.getValue(PART_Z);
        boolean center = isMaster(state);
        boolean wrench = holdsWrench(player);
        boolean shift = player.isShiftKeyDown();

        if (wrench) {
            if (center) {
                if (player instanceof ServerPlayer serverPlayer) {
                    NetworkHooks.openScreen(serverPlayer, table, master);
                }
                return InteractionResult.CONSUME;
            }
            OptionalInt seat = seatForCell(partX, partZ, state.getValue(FACING));
            if (seat.isPresent()) {
                table.toggleSeatEnabled(seat.getAsInt());
            }
            return InteractionResult.CONSUME;
        }

        if (shift) {
            if (center) {
                MahjongTableBlockEntity.StartMatchResult result =
                        table.tryStartMatchWithAutoSeating(new Random().nextLong());
                Component warning = warningFor(result, table);
                if (warning != null) {
                    player.sendSystemMessage(warning);
                }
                return InteractionResult.CONSUME;
            }
            OptionalInt seat = seatForCell(partX, partZ, state.getValue(FACING));
            if (seat.isPresent()) {
                table.claimSeat(seat.getAsInt(), player.getUUID());
            }
            return InteractionResult.CONSUME;
        }

        // Plain right-click in IDLE state — no action; tooltip overlay handles discovery.
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        if (!isMaster(state)) {
            return List.of();
        }
        ItemStack stack = new ItemStack(this);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MahjongTableBlockEntity) {
            CompoundTag beTag = be.saveWithoutMetadata();
            if (!beTag.isEmpty()) {
                stack.getOrCreateTag().put("BlockEntityTag", beTag);
            }
        }
        return List.of(stack);
    }

    @Override
    public void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, fromBlock, fromPos, isMoving);
        if (level.isClientSide()) {
            return;
        }
        BlockPos master = masterPos(pos, state);
        if (!(level.getBlockEntity(master) instanceof MahjongTableBlockEntity table)) {
            return;
        }
        table.onRedstone(anyPartPowered(level, master));
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

    public static boolean holdsWrench(Player player) {
        return isWrench(player.getMainHandItem()) || isWrench(player.getOffhandItem());
    }

    private static boolean isWrench(ItemStack stack) {
        return stack.is(WRENCH_TAG_FORGE) || stack.is(WRENCH_TAG_COMMON);
    }

    @Nullable
    private static Component warningFor(
            MahjongTableBlockEntity.StartMatchResult result,
            MahjongTableBlockEntity table) {
        return switch (result) {
            case STARTED, NOT_IDLE -> null;
            case SEATS_MISMATCH_PRESET -> Component.translatable(
                    "riichi_mahjong_forge.warn.table.seats_mismatch_preset",
                    Component.translatable(table.effectivePreset().langKey()));
        };
    }

    /**
     * Maps an edge cell to a seat index, respecting the table's facing.
     * Convention: the cell on the {@code FACING} side of the table is seat 0 (East,
     * the dealer in mahjong); subsequent seats increase counter-clockwise (East →
     * South → West → North), matching standard riichi turn order. Returns empty for
     * the centre and the four corners.
     */
    public static OptionalInt seatForCell(int partX, int partZ, Direction facing) {
        Direction cellDir = directionForCell(partX, partZ);
        if (cellDir == null) return OptionalInt.empty();
        return OptionalInt.of(seatForDirection(cellDir, facing));
    }

    /** Same convention as {@link #seatForCell}; takes the cell's world direction directly. */
    public static int seatForDirection(Direction cellDir, Direction facing) {
        return Math.floorMod(ccwIndex(cellDir) - ccwIndex(facing), 4);
    }

    @Nullable
    public static Direction directionForCell(int partX, int partZ) {
        if (partX == CENTER && partZ == 0) return Direction.NORTH;
        if (partX == GRID_SIZE - 1 && partZ == CENTER) return Direction.EAST;
        if (partX == CENTER && partZ == GRID_SIZE - 1) return Direction.SOUTH;
        if (partX == 0 && partZ == CENTER) return Direction.WEST;
        return null;
    }

    private static int ccwIndex(Direction d) {
        return switch (d) {
            case NORTH -> 0;
            case WEST -> 1;
            case SOUTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /**
     * Forces {@link #use(BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult)}
     * to fire on shift+RMB regardless of what's held. Without this, vanilla's
     * {@code useItemOn} skips {@code block.use()} under sneak unless the held item
     * declares {@code doesSneakBypassUse=true} — which means shift+RMB with a wrench
     * (third-party wrenches don't always set the flag) or with a tile in hand would
     * silently do nothing. We always route shift-clicks on our block through {@code use()}.
     */
    @Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID)
    public static final class SneakBypass {
        private SneakBypass() {}

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            if (!player.isSecondaryUseActive()) return;
            BlockState state = event.getLevel().getBlockState(event.getPos());
            if (!(state.getBlock() instanceof MahjongTableBlock block)) return;
            InteractionResult result = block.use(
                    state, event.getLevel(), event.getPos(), player, event.getHand(), event.getHitVec());
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
