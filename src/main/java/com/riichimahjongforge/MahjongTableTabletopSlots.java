package com.riichimahjongforge;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * World-space positions and ray-picks for tabletop hand and discard slots. Uses {@link MahjongTableBlockEntity} for
 * inventory indices only; all tabletop offsets live in this file (no duplicated magic numbers elsewhere).
 */
public final class MahjongTableTabletopSlots {

    private MahjongTableTabletopSlots() {}

    public record HandSlotPick(int seat, int slotIndex) {}

    public record DiscardSlotPick(int seat, int slotIndex) {}

    public record WallSlotPick(boolean deadWall, int stackIndex) {}

    public record MeldSlotPick(int seat, int slotIndex) {}

    public record ActionChipPick(int seat, int actionIndex) {}

    public record SurfacePickCandidates(
            @Nullable HandSlotPick handPick,
            int handInv,
            @Nullable DiscardSlotPick discardPick,
            int discardInv,
            @Nullable MeldSlotPick meldPick,
            int meldInv,
            @Nullable WallSlotPick wallPick,
            int wallInv) {}

    public enum SurfaceInteractionKind {
        HAND,
        DISCARD,
        MELD,
        WALL,
        ACTION_CHIP
    }

    public record ResolvedSurfaceInteraction(
            SurfaceInteractionKind kind, int seat, int slotIndex, boolean deadWall, int invSlot) {}

    public static SurfacePickCandidates emptySurfacePickCandidates() {
        return new SurfacePickCandidates(null, -1, null, -1, null, -1, null, -1);
    }

    /** Shared render/layout scale for table tiles. */
    public static final double TILE_RENDER_SCALE = 0.100;

    /** Typical center-to-center horizontal spacing between adjacent flat tiles on one row. */
    public static final double HORIZONTAL_TILE_STEP = TILE_RENDER_SCALE * 0.72;

    /** Typical center-to-center inward spacing between adjacent discard rows. */
    public static final double DISCARD_ROW_STEP_INWARD = TILE_RENDER_SCALE * 0.935;

    /** Typical vertical offset between stacked wall/dead-wall tile layers. */
    public static final double STACK_VERTICAL_STEP = TILE_RENDER_SCALE * 0.56;

    public static double spanForTileCount(int count) {
        if (count <= 1) {
            return 0.0;
        }
        return HORIZONTAL_TILE_STEP * (count - 1);
    }

    // --- Hand band (must stay disjoint from discard band in inward coordinate) ---

    private static final double HAND_Y = 0.62;

    private static final double HAND_INWARD = 0.86;

    // Keep hand picking close to the rendered tile footprint (avoid oversized hover bands).
    private static final double HAND_INWARD_TOLERANCE = 0.06;

    private static final double HAND_TANGENT_SPAN = spanForTileCount(MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT);

    private static final double HAND_ALONG_TOLERANCE = 0.038;

    // --- Discard river grid ---

    private static final double DISCARD_PICK_MAX_DY = 0.11;

    private static final double DISCARD_PICK_MAX_DIST_SQ = 0.052 * 0.052;

    /** Nearest row of discard slots to table center (blocks); must stay below hand pick band (~HAND_INWARD - tol). */
    private static final double DISCARD_INWARD_BASE = 0.26;

    private static final double DISCARD_INWARD_PER_ROW = DISCARD_ROW_STEP_INWARD;

    // Keep horizontal spacing close to hand-slot spacing so rivers don't look over-stretched.
    private static final double DISCARD_ALONG_SPAN = spanForTileCount(MahjongTableBlockEntity.DISCARD_GRID_COLS);

    private static final double DISCARD_Y_BASE = 0.615;

    private static final double DISCARD_Y_PER_ROW = 0.0;

    // --- Wall / dead wall stacks ---

    private static final int LIVE_WALL_STACKS_PER_EDGE = 19;
    private static final int DEAD_WALL_GAP_STACKS = 1;
    private static final double LIVE_WALL_INWARD = 0.74;
    private static final double DEAD_WALL_INWARD = LIVE_WALL_INWARD;
    private static final double DEAD_WALL_START_GAP_STACKS = 1.25;
    private static final double WALL_Y_BASE = 0.62;
    private static final double WALL_PICK_MAX_DY = 0.16;
    private static final double WALL_PICK_MAX_DIST_SQ = 0.085 * 0.085;

    // --- Open meld display ---

    private static final int OPEN_MELD_COLS = MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT;
    private static final int OPEN_MELD_ROWS = 1;
    private static final double OPEN_MELD_INWARD_BASE = 0.90 - HORIZONTAL_TILE_STEP * 0.30;
    private static final double OPEN_MELD_INWARD_PER_ROW = HORIZONTAL_TILE_STEP * 0.95;
    private static final double OPEN_MELD_Y = 0.62;
    private static final double OPEN_MELD_ALONG_SPAN = spanForTileCount(OPEN_MELD_COLS);
    private static final double OPEN_MELD_RIGHT_ANCHOR = 0.90;
    private static final double OPEN_MELD_PICK_MAX_DY = 0.13;
    private static final double OPEN_MELD_PICK_MAX_DIST_SQ = 0.065 * 0.065;

    // --- In-world action chips (riichi/tsumo/ron/etc.) ---
    // Positioned between hand band (0.86) and discard band (~0.26), close to wall line (~0.74).
    private static final double ACTION_CHIP_Y = 0.675;
    private static final double ACTION_CHIP_INWARD = 0.75;
    // Keep visuals unchanged, but nudge click area inward to reduce overlap with hand-tile interactions.
    private static final double ACTION_CHIP_PICK_INWARD = ACTION_CHIP_INWARD - 0.06;
    private static final double ACTION_CHIP_GAP = 0.36;
    private static final double ACTION_CHIP_PICK_MAX_DY = 0.45;
    private static final double ACTION_CHIP_PICK_MAX_DIST_SQ = 0.14 * 0.14;

    @Nullable
    public static HandSlotPick pickAnyHandSlot(MahjongTableBlockEntity table, Vec3 hitWorldPos) {
        BlockPos master = table.getBlockPos();
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            int idx = pickHandSlotIndex(master, seat, hitWorldPos);
            if (idx >= 0) {
                return new HandSlotPick(seat, idx);
            }
        }
        return null;
    }

    public static Vec3 worldPosForHandSlot(BlockPos masterPos, int seat, int slotIndex) {
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        int n = MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT;
        double half = HAND_TANGENT_SPAN / 2.0;
        double dt = n <= 1 ? 0.0 : HAND_TANGENT_SPAN / (n - 1);
        double alongM = half - dt * slotIndex;
        double ix = edge.getStepX() * HAND_INWARD + along.getStepX() * alongM;
        double iz = edge.getStepZ() * HAND_INWARD + along.getStepZ() * alongM;
        return new Vec3(cx + ix, masterPos.getY() + HAND_Y, cz + iz);
    }

    public static Vec3 worldPosForOpenMeldSlot(BlockPos masterPos, int seat, int slotIndex) {
        int col = Math.floorMod(slotIndex, OPEN_MELD_COLS);
        int row = Math.floorDiv(Math.max(0, slotIndex), OPEN_MELD_COLS);
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double dtAlong = OPEN_MELD_COLS <= 1 ? 0.0 : OPEN_MELD_ALONG_SPAN / (OPEN_MELD_COLS - 1.0);
        // Open meld strip grows from the seat-right side toward the seat-left side.
        double alongM = -OPEN_MELD_RIGHT_ANCHOR + dtAlong * col;
        double inwardM = OPEN_MELD_INWARD_BASE - OPEN_MELD_INWARD_PER_ROW * Math.min(OPEN_MELD_ROWS - 1, row);
        double ix = edge.getStepX() * inwardM + along.getStepX() * alongM;
        double iz = edge.getStepZ() * inwardM + along.getStepZ() * alongM;
        return new Vec3(cx + ix, masterPos.getY() + OPEN_MELD_Y, cz + iz);
    }

    @Nullable
    public static MeldSlotPick pickAnyOpenMeldSlot(MahjongTableBlockEntity table, Vec3 hitWorldPos) {
        BlockPos master = table.getBlockPos();
        double best = Double.MAX_VALUE;
        int bestSeat = -1;
        int bestSlot = -1;
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            for (int s = 0; s < MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT; s++) {
                Vec3 c = worldPosForOpenMeldSlot(master, seat, s);
                double dy = Math.abs(hitWorldPos.y - c.y);
                if (dy > OPEN_MELD_PICK_MAX_DY) {
                    continue;
                }
                double dx = hitWorldPos.x - c.x;
                double dz = hitWorldPos.z - c.z;
                double d2 = dx * dx + dz * dz;
                if (d2 < best) {
                    best = d2;
                    bestSeat = seat;
                    bestSlot = s;
                }
            }
        }
        if (bestSeat < 0 || best > OPEN_MELD_PICK_MAX_DIST_SQ) {
            return null;
        }
        return new MeldSlotPick(bestSeat, bestSlot);
    }

    public static int openMeldInventorySlot(MahjongTableBlockEntity table, @Nullable MeldSlotPick pick) {
        if (pick == null || !table.isSeatEnabled(pick.seat())) {
            return -1;
        }
        if (pick.slotIndex() < 0 || pick.slotIndex() >= MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT) {
            return -1;
        }
        return MahjongTableBlockEntity.INV_OPEN_MELD_START
                + pick.seat() * MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT
                + pick.slotIndex();
    }

    public static int pickHandSlotIndex(BlockPos masterPos, int seat, Vec3 hitWorldPos) {
        // TODO(table-hitboxes): replace tolerance-based hand picking with explicit per-slot AABB tests
        // so hover/click bounds exactly match visible tile/button footprints in 3D.
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double vx = hitWorldPos.x - cx;
        double vz = hitWorldPos.z - cz;
        double inwardCoord = vx * edge.getStepX() + vz * edge.getStepZ();
        double alongCoord = vx * along.getStepX() + vz * along.getStepZ();

        if (Math.abs(inwardCoord - HAND_INWARD) > HAND_INWARD_TOLERANCE) {
            return -1;
        }

        double half = HAND_TANGENT_SPAN / 2.0;
        int n = MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT;
        double dt = n <= 1 ? 1.0 : HAND_TANGENT_SPAN / (n - 1);
        int idx = (int) Math.round((half - alongCoord) / dt);
        if (idx < 0 || idx >= n) {
            return -1;
        }
        double expected = half - dt * idx;
        if (Math.abs(alongCoord - expected) > HAND_ALONG_TOLERANCE) {
            return -1;
        }
        return idx;
    }

    public static int handInventorySlot(MahjongTableBlockEntity table, @Nullable HandSlotPick pick) {
        if (pick == null || !table.isSeatEnabled(pick.seat())) {
            return -1;
        }
        return MahjongTableBlockEntity.playerZoneAbsolute(pick.seat(), pick.slotIndex());
    }

    public static Vec3 worldPosForActionChip(BlockPos masterPos, int seat, int actionIndex, int actionCount) {
        int clampedCount = Math.max(1, actionCount);
        int clampedIndex = Math.max(0, Math.min(clampedCount - 1, actionIndex));
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double totalSpan = ACTION_CHIP_GAP * Math.max(0, clampedCount - 1);
        double start = totalSpan * 0.5;
        double alongM = start - ACTION_CHIP_GAP * clampedIndex;
        double ix = edge.getStepX() * ACTION_CHIP_INWARD + along.getStepX() * alongM;
        double iz = edge.getStepZ() * ACTION_CHIP_INWARD + along.getStepZ() * alongM;
        return new Vec3(cx + ix, masterPos.getY() + ACTION_CHIP_Y, cz + iz);
    }

    private static Vec3 worldPosForActionChipPick(BlockPos masterPos, int seat, int actionIndex, int actionCount) {
        int clampedCount = Math.max(1, actionCount);
        int clampedIndex = Math.max(0, Math.min(clampedCount - 1, actionIndex));
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double totalSpan = ACTION_CHIP_GAP * Math.max(0, clampedCount - 1);
        double start = totalSpan * 0.5;
        double alongM = start - ACTION_CHIP_GAP * clampedIndex;
        double ix = edge.getStepX() * ACTION_CHIP_PICK_INWARD + along.getStepX() * alongM;
        double iz = edge.getStepZ() * ACTION_CHIP_PICK_INWARD + along.getStepZ() * alongM;
        return new Vec3(cx + ix, masterPos.getY() + ACTION_CHIP_Y, cz + iz);
    }

    @Nullable
    public static ActionChipPick pickSeatActionChip(
            MahjongTableBlockEntity table, int seat, Vec3 hitWorldPos, int actionCount) {
        if (seat < 0 || seat >= MahjongTableBlockEntity.SEAT_COUNT || actionCount <= 0) {
            return null;
        }
        if (!table.isSeatEnabled(seat)) {
            return null;
        }
        BlockPos master = table.getBlockPos();
        double best = Double.MAX_VALUE;
        int bestIdx = -1;
        for (int i = 0; i < actionCount; i++) {
            Vec3 c = worldPosForActionChipPick(master, seat, i, actionCount);
            double dy = Math.abs(hitWorldPos.y - c.y);
            if (dy > ACTION_CHIP_PICK_MAX_DY) {
                continue;
            }
            double dx = hitWorldPos.x - c.x;
            double dz = hitWorldPos.z - c.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                bestIdx = i;
            }
        }
        if (bestIdx < 0 || best > ACTION_CHIP_PICK_MAX_DIST_SQ) {
            return null;
        }
        return new ActionChipPick(seat, bestIdx);
    }

    @Nullable
    public static DiscardSlotPick pickAnyDiscardSlot(MahjongTableBlockEntity table, Vec3 hitWorldPos) {
        BlockPos master = table.getBlockPos();
        double best = Double.MAX_VALUE;
        int bestSeat = -1;
        int bestSlot = -1;
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            for (int s = 0; s < MahjongTableBlockEntity.DISCARDS_SLOTS_PER_SEAT; s++) {
                Vec3 c = worldPosForDiscardSlot(master, seat, s);
                double dy = Math.abs(hitWorldPos.y - c.y);
                if (dy > DISCARD_PICK_MAX_DY) {
                    continue;
                }
                double dx = hitWorldPos.x - c.x;
                double dz = hitWorldPos.z - c.z;
                double d2 = dx * dx + dz * dz;
                if (d2 < best) {
                    best = d2;
                    bestSeat = seat;
                    bestSlot = s;
                }
            }
        }
        if (bestSeat < 0 || best > DISCARD_PICK_MAX_DIST_SQ) {
            return null;
        }
        return new DiscardSlotPick(bestSeat, bestSlot);
    }

    public static Vec3 worldPosForDiscardSlot(BlockPos masterPos, int seat, int slotIndex) {
        int col = slotIndex % MahjongTableBlockEntity.DISCARD_GRID_COLS;
        int row = slotIndex / MahjongTableBlockEntity.DISCARD_GRID_COLS;
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double y = masterPos.getY() + DISCARD_Y_BASE + row * DISCARD_Y_PER_ROW;
        double inwardM = DISCARD_INWARD_BASE + DISCARD_INWARD_PER_ROW * row;
        double along0 = -DISCARD_ALONG_SPAN / 2.0;
        int cols = MahjongTableBlockEntity.DISCARD_GRID_COLS;
        double dtAlong = cols <= 1 ? 0.0 : DISCARD_ALONG_SPAN / (cols - 1);
        // Fill river rows from seat-left to seat-right (left-to-right to the player).
        int visualCol = Math.max(0, cols - 1 - col);
        double alongM = along0 + dtAlong * visualCol;
        double ix = edge.getStepX() * inwardM + along.getStepX() * alongM;
        double iz = edge.getStepZ() * inwardM + along.getStepZ() * alongM;
        return new Vec3(cx + ix, y, cz + iz);
    }

    public static int discardInventorySlot(MahjongTableBlockEntity table, @Nullable DiscardSlotPick pick) {
        if (pick == null || !table.isSeatEnabled(pick.seat())) {
            return -1;
        }
        return MahjongTableBlockEntity.discardAbsolute(pick.seat(), pick.slotIndex());
    }

    public static SurfacePickCandidates collectSurfacePickCandidates(MahjongTableBlockEntity table, Vec3 hitWorldPos) {
        HandSlotPick handPick = pickAnyHandSlot(table, hitWorldPos);
        DiscardSlotPick discardPick = pickAnyDiscardSlot(table, hitWorldPos);
        MeldSlotPick meldPick = pickAnyOpenMeldSlot(table, hitWorldPos);
        WallSlotPick wallPick = pickAnyWallStackTop(table, hitWorldPos);
        int handInv = handInventorySlot(table, handPick);
        int discardInv = discardInventorySlot(table, discardPick);
        int meldInv = openMeldInventorySlot(table, meldPick);
        int wallInv = wallInventorySlotForTopOfStack(
                table, wallPick != null && wallPick.deadWall(), wallPick != null ? wallPick.stackIndex() : -1);
        return new SurfacePickCandidates(
                handPick, handInv, discardPick, discardInv, meldPick, meldInv, wallPick, wallInv);
    }

    @Nullable
    public static ResolvedSurfaceInteraction resolveSurfaceInteraction(
            MahjongTableBlockEntity table, SurfacePickCandidates candidates, boolean takingFromTable) {
        HandSlotPick handPick = candidates.handPick();
        if (handPick != null && candidates.meldPick() != null && takingFromTable) {
            boolean handHasTile = candidates.handInv() >= 0 && !table.getItem(candidates.handInv()).isEmpty();
            boolean meldHasTile = candidates.meldInv() >= 0 && !table.getItem(candidates.meldInv()).isEmpty();
            if (!handHasTile && meldHasTile) {
                handPick = null;
            }
        }
        boolean handHasTile = candidates.handInv() >= 0 && !table.getItem(candidates.handInv()).isEmpty();
        if (handPick != null) {
            if (takingFromTable && !handHasTile) {
                handPick = null;
            } else {
            return new ResolvedSurfaceInteraction(
                    SurfaceInteractionKind.HAND, handPick.seat(), handPick.slotIndex(), false, candidates.handInv());
            }
        }
        if (candidates.discardPick() != null) {
            boolean discardHasTile = candidates.discardInv() >= 0 && !table.getItem(candidates.discardInv()).isEmpty();
            if (takingFromTable && !discardHasTile) {
                // Empty discard slots should not consume a click intended for center start.
            } else {
            return new ResolvedSurfaceInteraction(
                    SurfaceInteractionKind.DISCARD,
                    candidates.discardPick().seat(),
                    candidates.discardPick().slotIndex(),
                    false,
                    candidates.discardInv());
            }
        }
        if (candidates.meldPick() != null) {
            boolean meldHasTile = candidates.meldInv() >= 0 && !table.getItem(candidates.meldInv()).isEmpty();
            if (takingFromTable && !meldHasTile) {
                // Ignore empty meld slots for pickup interactions.
            } else {
            return new ResolvedSurfaceInteraction(
                    SurfaceInteractionKind.MELD,
                    candidates.meldPick().seat(),
                    candidates.meldPick().slotIndex(),
                    false,
                    candidates.meldInv());
            }
        }
        if (candidates.wallPick() != null) {
            boolean wallHasTile = candidates.wallInv() >= 0 && !table.getItem(candidates.wallInv()).isEmpty();
            if (takingFromTable && !wallHasTile) {
                // Ignore empty wall stacks for pickup interactions.
            } else {
            return new ResolvedSurfaceInteraction(
                    SurfaceInteractionKind.WALL,
                    -1,
                    candidates.wallPick().stackIndex(),
                    candidates.wallPick().deadWall(),
                    candidates.wallInv());
            }
        }
        return null;
    }

    public static int dealerSeatForWallLayout(MahjongTableBlockEntity table) {
        int dealerSeat = 0;
        if (!table.isInMatch()) {
            return dealerSeat;
        }
        var order = table.deterministicPlayOrder();
        if (order.isEmpty()) {
            return dealerSeat;
        }
        int seat = order.get(0);
        if (seat >= 0 && seat < MahjongTableBlockEntity.SEAT_COUNT) {
            dealerSeat = seat;
        }
        return dealerSeat;
    }

    public static int deadWallSeatFromDealer(int dealerSeat) {
        return (dealerSeat + 1) % MahjongTableBlockEntity.SEAT_COUNT;
    }

    public static int seatForLiveWallEdgeCcwFromDealer(int dealerSeat, int edgeIndex) {
        return (dealerSeat - edgeIndex + MahjongTableBlockEntity.SEAT_COUNT * 4) % MahjongTableBlockEntity.SEAT_COUNT;
    }

    public static int liveWallStackCount() {
        return MahjongTableBlockEntity.WALL_SLOTS / 2;
    }

    public static int liveWallStacksPerEdge() {
        return LIVE_WALL_STACKS_PER_EDGE;
    }

    public static int deadWallStackCount() {
        return MahjongTableBlockEntity.DEAD_WALL_SLOTS / 2;
    }

    public static int wallInventorySlotForTopOfStack(
            MahjongTableBlockEntity table, boolean deadWall, int stackIndex) {
        int start = deadWall ? MahjongTableBlockEntity.INV_DEAD_WALL_START : MahjongTableBlockEntity.INV_WALL_START;
        int slotCount = deadWall ? MahjongTableBlockEntity.DEAD_WALL_SLOTS : MahjongTableBlockEntity.WALL_SLOTS;
        int stacks = slotCount / 2;
        if (stackIndex < 0 || stackIndex >= stacks) {
            return -1;
        }
        int top = start + stackIndex * 2;
        int bottom = top + 1;
        if (!table.getItem(top).isEmpty()) {
            return top;
        }
        if (bottom < start + slotCount && !table.getItem(bottom).isEmpty()) {
            return bottom;
        }
        return top;
    }

    @Nullable
    public static WallSlotPick pickAnyWallStackTop(MahjongTableBlockEntity table, Vec3 hitWorldPos) {
        BlockPos master = table.getBlockPos();
        int dealerSeat = dealerSeatForWallLayout(table);
        double best = Double.MAX_VALUE;
        boolean bestDeadWall = false;
        int bestStack = -1;

        for (int stack = 0; stack < liveWallStackCount(); stack++) {
            int layer = topLayerForStack(table, false, stack);
            Vec3 c = worldPosForLiveWallStackLayer(master, dealerSeat, stack, layer);
            double dy = Math.abs(hitWorldPos.y - c.y);
            if (dy > WALL_PICK_MAX_DY) {
                continue;
            }
            double dx = hitWorldPos.x - c.x;
            double dz = hitWorldPos.z - c.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                bestDeadWall = false;
                bestStack = stack;
            }
        }

        for (int stack = 0; stack < deadWallStackCount(); stack++) {
            int layer = topLayerForStack(table, true, stack);
            Vec3 c = worldPosForDeadWallStackLayer(master, dealerSeat, stack, layer);
            double dy = Math.abs(hitWorldPos.y - c.y);
            if (dy > WALL_PICK_MAX_DY) {
                continue;
            }
            double dx = hitWorldPos.x - c.x;
            double dz = hitWorldPos.z - c.z;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
                bestDeadWall = true;
                bestStack = stack;
            }
        }

        if (bestStack < 0 || best > WALL_PICK_MAX_DIST_SQ) {
            return null;
        }
        return new WallSlotPick(bestDeadWall, bestStack);
    }

    public static Vec3 worldPosForLiveWallStackLayer(
            BlockPos masterPos, int dealerSeat, int stackIndex, int layer) {
        LiveWallStackPos pos = liveWallStackPosForStack(stackIndex);
        int edge = pos.edge();
        int along = pos.along();
        int seat = seatForLiveWallEdgeCcwFromDealer(dealerSeat, edge);
        Direction edgeDir = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction alongDir = edgeDir.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double spanUse = spanForTileCount(LIVE_WALL_STACKS_PER_EDGE);
        double t0u = spanUse / 2.0;
        double dtu = spanUse / (LIVE_WALL_STACKS_PER_EDGE - 1.0);
        double alongM = t0u - dtu * along;
        double ix = edgeDir.getStepX() * LIVE_WALL_INWARD + alongDir.getStepX() * alongM;
        double iz = edgeDir.getStepZ() * LIVE_WALL_INWARD + alongDir.getStepZ() * alongM;
        double y = masterPos.getY() + WALL_Y_BASE + Math.max(0, layer) * STACK_VERTICAL_STEP;
        return new Vec3(cx + ix, y, cz + iz);
    }

    public static int liveWallSeatForStack(int dealerSeat, int stackIndex) {
        return seatForLiveWallEdgeCcwFromDealer(dealerSeat, liveWallStackPosForStack(stackIndex).edge());
    }

    public static Vec3 worldPosForDeadWallStackLayer(
            BlockPos masterPos, int dealerSeat, int stackIndex, int layer) {
        int seat = deadWallSeatFromDealer(dealerSeat);
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = edge.getClockWise();
        double cx = masterPos.getX() + 0.5;
        double cz = masterPos.getZ() + 0.5;
        double dt = HORIZONTAL_TILE_STEP;
        int deadCount = deadWallStackCount();
        int local = LIVE_WALL_STACKS_PER_EDGE - 1 - Math.max(0, Math.min(deadCount - 1, stackIndex));
        // Dead wall occupies the opposite end of the dealer-right edge, with a visible break from live wall.
        int liveOnDeadEdge = liveWallStacksOnDeadWallEdge();
        int deadStart = liveOnDeadEdge + DEAD_WALL_GAP_STACKS;
        local = deadStart + (deadCount - 1 - Math.max(0, Math.min(deadCount - 1, stackIndex)));
        double liveEdgeHalfSpan = spanForTileCount(LIVE_WALL_STACKS_PER_EDGE) / 2.0;
        double alongM = liveEdgeHalfSpan - dt * local;
        double ix = edge.getStepX() * DEAD_WALL_INWARD + along.getStepX() * alongM;
        double iz = edge.getStepZ() * DEAD_WALL_INWARD + along.getStepZ() * alongM;
        double y = masterPos.getY() + WALL_Y_BASE + Math.max(0, layer) * STACK_VERTICAL_STEP;
        return new Vec3(cx + ix, y, cz + iz);
    }

    private static int topLayerForStack(MahjongTableBlockEntity table, boolean deadWall, int stackIndex) {
        int start = deadWall ? MahjongTableBlockEntity.INV_DEAD_WALL_START : MahjongTableBlockEntity.INV_WALL_START;
        int top = start + stackIndex * 2;
        int bottom = top + 1;
        if (!table.getItem(top).isEmpty()) {
            return 1;
        }
        if (!table.getItem(bottom).isEmpty()) {
            return 0;
        }
        return 1;
    }

    private record LiveWallStackPos(int edge, int along) {}

    private static int liveWallStacksOnDeadWallEdge() {
        return LIVE_WALL_STACKS_PER_EDGE - deadWallStackCount() - DEAD_WALL_GAP_STACKS;
    }

    private static LiveWallStackPos liveWallStackPosForStack(int stackIndex) {
        int s = Math.max(0, stackIndex);
        int fullEdges = MahjongTableBlockEntity.SEAT_COUNT - 1;
        int fullCapacity = fullEdges * LIVE_WALL_STACKS_PER_EDGE;
        if (s < fullCapacity) {
            return new LiveWallStackPos(s / LIVE_WALL_STACKS_PER_EDGE, s % LIVE_WALL_STACKS_PER_EDGE);
        }
        int onDeadEdge = Math.min(liveWallStacksOnDeadWallEdge(), s - fullCapacity);
        return new LiveWallStackPos(3, onDeadEdge);
    }

    static {
        if (OPEN_MELD_COLS * OPEN_MELD_ROWS != MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT) {
            throw new IllegalStateException("open meld grid must match OPEN_MELD_SLOTS_PER_SEAT");
        }
    }
}
