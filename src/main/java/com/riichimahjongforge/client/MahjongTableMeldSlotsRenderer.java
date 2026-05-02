package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTableTabletopSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only drawing for open meld slots shown on table edges.
 */
public final class MahjongTableMeldSlotsRenderer {

    private MahjongTableMeldSlotsRenderer() {}

    public static void render(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Minecraft mc,
            @Nullable Player viewer,
            @Nullable MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolvedSurface) {
        int hoveredInv = resolvedSurface != null ? resolvedSurface.invSlot() : -1;
        int lastMeldSeat = table.lastMeldSeat();
        int lastMeldClaimedSlotIndex = table.lastMeldClaimedSlotIndex();

        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            float yaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(seat);
            int baseInv = MahjongTableBlockEntity.INV_OPEN_MELD_START + seat * MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT;
            for (int i = 0; i < MahjongTableBlockEntity.OPEN_MELD_SLOTS_PER_SEAT; i++) {
                ItemStack st = table.getItem(baseInv + i);
                if (st.isEmpty()) {
                    continue;
                }
                var p = MahjongTableTabletopSlots.worldPosForOpenMeldSlot(table.getBlockPos(), seat, i);
                boolean hovered = (baseInv + i) == hoveredInv;
                MahjongTableSurfacePlacements.renderTileOnTableHovered(
                        mc,
                        poseStack,
                        buffers,
                        table.getLevel(),
                        table.getBlockPos(),
                        p,
                        yaw,
                        90.0f,
                        st,
                        packedLight,
                        packedOverlay,
                        hovered);
                if (seat == lastMeldSeat && i == lastMeldClaimedSlotIndex) {
                    MahjongTableSurfacePlacements.renderLastDiscardWireHighlight(
                            poseStack, buffers, table.getBlockPos(), p, yaw);
                }
            }
        }

        if (MahjongTableSurfacePlacements.shouldRenderResolvedPlaceHint(
                table, viewer, resolvedSurface, MahjongTableTabletopSlots.SurfaceInteractionKind.MELD)) {
                var p = MahjongTableTabletopSlots.worldPosForOpenMeldSlot(
                        table.getBlockPos(), resolvedSurface.seat(), resolvedSurface.slotIndex());
                float hintYaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(resolvedSurface.seat());
                MahjongTableSurfacePlacements.renderEmptySlotWireHint(
                        poseStack, buffers, table.getBlockPos(), p, hintYaw, MahjongTableSurfacePlacements.HintStyle.FLAT);
        }
    }
}
