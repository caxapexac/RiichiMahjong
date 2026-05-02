package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mahjongcore.rules.RoundSetupRules;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTableTabletopSlots;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client-only rendering for in-world live wall and dead wall stacks.
 */
public final class MahjongTableWallSlotsRenderer {

    private static final double OPEN_DORA_CENTER_COMPENSATION = 0.0125;

    private MahjongTableWallSlotsRenderer() {}

    public static void render(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Minecraft mc,
            @Nullable MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolvedSurface) {
        int dealerSeat = resolveDealerSeat(table);
        int hoveredInvSlot = resolvedSurface != null ? resolvedSurface.invSlot() : -1;
        @Nullable Player viewer = mc.player;

        for (int i = 0; i < MahjongTableBlockEntity.WALL_SLOTS; i++) {
            ItemStack st = table.getItem(MahjongTableBlockEntity.INV_WALL_START + i);
            if (st.isEmpty()) {
                continue;
            }
            int seat = seatIndexForLiveWallSlot(dealerSeat, i);
            float yaw = yawDegreesForSeatEdge(seat);
            int stackIndex = i / 2;
            int layer = (i % 2 == 0) ? 1 : 0;
            Vec3 p =
                    MahjongTableTabletopSlots.worldPosForLiveWallStackLayer(
                            table.getBlockPos(), dealerSeat, stackIndex, layer);
            boolean hovered = (MahjongTableBlockEntity.INV_WALL_START + i) == hoveredInvSlot;
            MahjongTableSurfacePlacements.renderTileOnTableHovered(
                    mc,
                    poseStack,
                    buffers,
                    table.getLevel(),
                    table.getBlockPos(),
                    p,
                    yaw,
                    -90.0f,
                    st,
                    packedLight,
                    packedOverlay,
                    hovered);
        }

        for (int i = 0; i < MahjongTableBlockEntity.DEAD_WALL_SLOTS; i++) {
            ItemStack st = table.getItem(MahjongTableBlockEntity.INV_DEAD_WALL_START + i);
            if (st.isEmpty()) {
                continue;
            }
            int deadSeat = MahjongTableTabletopSlots.deadWallSeatFromDealer(dealerSeat);
            float yaw = yawDegreesForSeatEdge(deadSeat);
            int stackIndex = i / 2;
            int layer = (i % 2 == 0) ? 1 : 0;
            Vec3 p =
                    MahjongTableTabletopSlots.worldPosForDeadWallStackLayer(
                            table.getBlockPos(), dealerSeat, stackIndex, layer);
            boolean hovered = (MahjongTableBlockEntity.INV_DEAD_WALL_START + i) == hoveredInvSlot;
            boolean openedDora = isFaceUpDeadWallSlot(table, i);
            float pitch = openedDora ? 90.0f : -90.0f;
            if (openedDora) {
                // Face-up dora uses opposite pitch; compensate a little toward table center
                // because the tile model is shorter than a full voxel footprint.
                Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(deadSeat);
                p = p.add(
                        edge.getStepX() * OPEN_DORA_CENTER_COMPENSATION,
                        0.0,
                        edge.getStepZ() * OPEN_DORA_CENTER_COMPENSATION);
            }
            MahjongTableSurfacePlacements.renderTileOnTableHovered(
                    mc,
                    poseStack,
                    buffers,
                    table.getLevel(),
                    table.getBlockPos(),
                    p,
                    yaw,
                    pitch,
                    st,
                    packedLight,
                    packedOverlay,
                    hovered);
        }

        if (viewer != null
                && resolvedSurface != null
                && resolvedSurface.kind() == MahjongTableTabletopSlots.SurfaceInteractionKind.WALL
                && hoveredInvSlot >= 0) {
            int dealer = dealerSeat;
            int deadStart = MahjongTableBlockEntity.INV_DEAD_WALL_START;
            int deadEnd = deadStart + MahjongTableBlockEntity.DEAD_WALL_SLOTS;
            Vec3 hintPos = null;
            float hintYaw = 0.0f;
            int hintInvSlot = -1;
            if (hoveredInvSlot >= MahjongTableBlockEntity.INV_WALL_START
                    && hoveredInvSlot < MahjongTableBlockEntity.INV_WALL_START + MahjongTableBlockEntity.WALL_SLOTS) {
                int local = hoveredInvSlot - MahjongTableBlockEntity.INV_WALL_START;
                int stackIndex = local / 2;
                int seat = seatIndexForLiveWallSlot(dealer, local);
                hintYaw = yawDegreesForSeatEdge(seat);
                hintInvSlot = wallPlaceTargetInvSlot(table, false, stackIndex);
                int hintLayer = wallLayerForInvSlot(false, stackIndex, hintInvSlot);
                hintPos = MahjongTableTabletopSlots.worldPosForLiveWallStackLayer(
                        table.getBlockPos(), dealer, stackIndex, hintLayer);
            } else if (hoveredInvSlot >= deadStart && hoveredInvSlot < deadEnd) {
                int local = hoveredInvSlot - deadStart;
                int stackIndex = local / 2;
                int seat = MahjongTableTabletopSlots.deadWallSeatFromDealer(dealer);
                hintYaw = yawDegreesForSeatEdge(seat);
                hintInvSlot = wallPlaceTargetInvSlot(table, true, stackIndex);
                int hintLayer = wallLayerForInvSlot(true, stackIndex, hintInvSlot);
                hintPos = MahjongTableTabletopSlots.worldPosForDeadWallStackLayer(
                        table.getBlockPos(), dealer, stackIndex, hintLayer);
            }
            if (hintPos != null
                    && hintInvSlot >= 0
                    && MahjongTableSurfacePlacements.viewerMayPlaceInInventorySlot(table, hintInvSlot, viewer)) {
                MahjongTableSurfacePlacements.renderEmptySlotWireHint(
                        poseStack,
                        buffers,
                        table.getBlockPos(),
                        hintPos,
                        hintYaw,
                        MahjongTableSurfacePlacements.HintStyle.FLAT);
            }
        }
    }

    private static int resolveDealerSeat(MahjongTableBlockEntity table) {
        return MahjongTableTabletopSlots.dealerSeatForWallLayout(table);
    }

    private static int seatIndexForLiveWallSlot(int dealerSeat, int wallSlotIndex) {
        int stackIndex = Math.max(0, wallSlotIndex) / 2;
        return MahjongTableTabletopSlots.liveWallSeatForStack(dealerSeat, stackIndex);
    }

    private static float yawDegreesForSeatEdge(int seat) {
        Direction d = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        return switch (d) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case WEST -> -90.0f;
            case EAST -> 90.0f;
            default -> 0.0f;
        };
    }

    private static int wallPlaceTargetInvSlot(MahjongTableBlockEntity table, boolean deadWall, int stackIndex) {
        int start = deadWall ? MahjongTableBlockEntity.INV_DEAD_WALL_START : MahjongTableBlockEntity.INV_WALL_START;
        int slotCount = deadWall ? MahjongTableBlockEntity.DEAD_WALL_SLOTS : MahjongTableBlockEntity.WALL_SLOTS;
        int stacks = slotCount / 2;
        if (stackIndex < 0 || stackIndex >= stacks) {
            return -1;
        }
        int top = start + stackIndex * 2;
        int bottom = top + 1;
        boolean topOccupied = !table.getItem(top).isEmpty();
        boolean bottomOccupied = bottom < start + slotCount && !table.getItem(bottom).isEmpty();
        if (!topOccupied && !bottomOccupied) {
            return bottom;
        }
        if (!topOccupied && bottomOccupied) {
            return top;
        }
        if (topOccupied && !bottomOccupied) {
            return bottom;
        }
        return top;
    }

    private static int wallLayerForInvSlot(boolean deadWall, int stackIndex, int invSlot) {
        int start = deadWall ? MahjongTableBlockEntity.INV_DEAD_WALL_START : MahjongTableBlockEntity.INV_WALL_START;
        int top = start + stackIndex * 2;
        int bottom = top + 1;
        if (invSlot == top) {
            return 1;
        }
        if (invSlot == bottom) {
            return 0;
        }
        return 1;
    }

    private static boolean isFaceUpDeadWallSlot(MahjongTableBlockEntity table, int localDeadWallSlot) {
        if (localDeadWallSlot < 0 || localDeadWallSlot >= MahjongTableBlockEntity.DEAD_WALL_SLOTS) {
            return false;
        }
        if (table.isDoraHiddenDuringSetup()) {
            return false;
        }
        int kanCount = Math.max(0, table.declaredKanCountFromMelds());
        int omoteIndicatorCount = Math.min(1 + kanCount, 5);
        for (int i = 0; i < omoteIndicatorCount; i++) {
            if (localDeadWallSlot == RoundSetupRules.DORA_INDICATOR_DEAD_WALL_INDEX + (i * 2)) {
                return true;
            }
        }
        if (!table.shouldShowUraDoraIndicatorsForRender()) {
            return false;
        }
        for (int i = 0; i < omoteIndicatorCount; i++) {
            if (localDeadWallSlot == RoundSetupRules.DORA_INDICATOR_DEAD_WALL_INDEX + 1 + (i * 2)) {
                return true;
            }
        }
        return false;
    }
}
