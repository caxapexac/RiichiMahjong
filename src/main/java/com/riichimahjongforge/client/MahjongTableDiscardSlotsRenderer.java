package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTableTabletopSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only drawing for in-world discard slots; geometry and picks live in {@link MahjongTableTabletopSlots}.
 */
public final class MahjongTableDiscardSlotsRenderer {

    private MahjongTableDiscardSlotsRenderer() {}

    public static void render(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Minecraft mc,
            Player viewer,
            @Nullable MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolvedSurface) {
        int hoveredInv = resolvedSurface != null ? resolvedSurface.invSlot() : -1;

        int lastDiscardSeat = table.lastDiscardSeat();
        int lastDiscardSlot = table.lastDiscardSlotIndex();
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            float yaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(seat);
            int baseInv = MahjongTableBlockEntity.discardBase(seat);
            for (int i = 0; i < MahjongTableBlockEntity.DISCARDS_SLOTS_PER_SEAT; i++) {
                ItemStack st = table.getItem(baseInv + i);
                if (st.isEmpty()) {
                    continue;
                }
                var p = MahjongTableTabletopSlots.worldPosForDiscardSlot(table.getBlockPos(), seat, i);
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
                if (seat == lastDiscardSeat && i == lastDiscardSlot) {
                    MahjongTableSurfacePlacements.renderLastDiscardWireHighlight(
                            poseStack, buffers, table.getBlockPos(), p, yaw);
                }
            }
        }

        if (MahjongTableSurfacePlacements.shouldRenderResolvedPlaceHint(
                table, viewer, resolvedSurface, MahjongTableTabletopSlots.SurfaceInteractionKind.DISCARD)) {
            BlockPos bp = table.getBlockPos();
            var p = MahjongTableTabletopSlots.worldPosForDiscardSlot(bp, resolvedSurface.seat(), resolvedSurface.slotIndex());
            float hintYaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(resolvedSurface.seat());
            MahjongTableSurfacePlacements.renderEmptySlotWireHint(
                    poseStack, buffers, bp, p, hintYaw, MahjongTableSurfacePlacements.HintStyle.FLAT);
        }
    }
}
