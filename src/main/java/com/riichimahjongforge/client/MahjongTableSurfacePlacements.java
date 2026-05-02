package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTableTabletopSlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared client rendering for mahjong items placed on the table surface: scale, pose, empty-slot wire hints, and
 * small helpers used by {@link MahjongTableHandSlotsRenderer}, {@link MahjongTableDiscardSlotsRenderer}, and wall
 * layout.
 */
public final class MahjongTableSurfacePlacements {

    private MahjongTableSurfacePlacements() {}

    public enum HintStyle {
        STANDING,
        FLAT
    }

    /** Uniform scale for every table-surface tile item (hands, wall, dead wall, future melds). */
    public static final float TILE_RENDER_SCALE = (float) MahjongTableTabletopSlots.TILE_RENDER_SCALE;

    /** Multiplier on {@link #TILE_RENDER_SCALE} for hover feedback (e.g. held hand slot). */
    public static final float HOVER_SCALE_MULTIPLIER = 1.18f;
    public static final double HOVER_LIFT_BLOCKS = 0.02;
    private static final double PITCHED_TILE_SURFACE_Y_ADJUST = -0.03;

    /**
     * Empty-slot wire hint: XZ footprint and Y extent are derived from {@link #TILE_RENDER_SCALE} so the frame tracks
     * the same {@link ItemDisplayContext#FIXED} item size as {@link #renderTileOnTable}.
     */
    private static final float HINT_BOX_XZ_HALF_LONG = TILE_RENDER_SCALE * 0.40f;

    private static final float HINT_BOX_XZ_HALF_SHORT = TILE_RENDER_SCALE * 0.28f;

    private static final float HINT_BOX_Y_HALF = TILE_RENDER_SCALE * 0.48f;
    // For flat tiles, use the standing box dimensions rotated conceptually onto the table plane:
    // x keeps the long side, z uses the former standing height, y uses former standing thickness.
    private static final float HINT_FLAT_XZ_HALF_LONG = HINT_BOX_XZ_HALF_LONG;
    private static final float HINT_FLAT_XZ_HALF_SHORT = HINT_BOX_Y_HALF;
    private static final float HINT_FLAT_Y_HALF = HINT_BOX_XZ_HALF_SHORT;
    // The flat tile item model is not perfectly centered on the anchor; shift the hint box toward the player.
    private static final float HINT_FLAT_Z_OFFSET = TILE_RENDER_SCALE * 0.04f;

    /**
     * Yaw (degrees) for in-world hand tiles at a seat, consistent with {@link #renderEmptySlotWireHint} for that seat.
     */
    public static float yawHandTilesAtSeat(int seat) {
        Direction edge = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        return switch (edge) {
            case NORTH -> 0.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    /**
     * Renders one {@link ItemStack} lying on the table: block-entity–relative translate, Y rotation, uniform scale,
     * {@link ItemDisplayContext#FIXED}. Call from BE renderer with the stack already resolved (face-up tile, back,
     * etc.).
     */
    public static void renderTileOnTable(
            Minecraft mc,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Level level,
            BlockPos tableAnchorPos,
            Vec3 worldPosition,
            float yawDegrees,
            ItemStack stack,
            int packedLight,
            int packedOverlay,
            float uniformScale,
            double liftYBeforeScale) {
        renderTileOnTable(
                mc,
                poseStack,
                buffers,
                level,
                tableAnchorPos,
                worldPosition,
                yawDegrees,
                0.0f,
                stack,
                packedLight,
                packedOverlay,
                uniformScale,
                liftYBeforeScale);
    }

    public static void renderTileOnTable(
            Minecraft mc,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Level level,
            BlockPos tableAnchorPos,
            Vec3 worldPosition,
            float yawDegrees,
            float pitchDegrees,
            ItemStack stack,
            int packedLight,
            int packedOverlay,
            float uniformScale,
            double liftYBeforeScale) {
        poseStack.pushPose();
        poseStack.translate(
                worldPosition.x - tableAnchorPos.getX(),
                worldPosition.y - tableAnchorPos.getY(),
                worldPosition.z - tableAnchorPos.getZ());
        if (pitchDegrees != 0.0f) {
            // FIXED item models rendered flat (pitched +/-90) sit slightly above the tabletop.
            // Apply one shared correction so discard/meld/wall flat tiles all align consistently.
            poseStack.translate(0.0, PITCHED_TILE_SURFACE_Y_ADJUST, 0.0);
        }
        // Apply lift in world space so hover always moves tiles upward, even for pitched/flat tiles.
        if (liftYBeforeScale != 0.0) {
            poseStack.translate(0.0, liftYBeforeScale, 0.0);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));
        if (pitchDegrees != 0.0f) {
            poseStack.mulPose(Axis.XP.rotationDegrees(pitchDegrees));
        }
        poseStack.scale(uniformScale, uniformScale, uniformScale);
        mc.getItemRenderer()
                .renderStatic(
                        stack,
                        ItemDisplayContext.FIXED,
                        packedLight,
                        packedOverlay,
                        poseStack,
                        buffers,
                        level,
                        0);
        poseStack.popPose();
    }

    public static float hoveredScale(boolean hovered) {
        return TILE_RENDER_SCALE;
    }

    public static double hoveredLift(boolean hovered) {
        return hovered ? HOVER_LIFT_BLOCKS : 0.0;
    }

    public static void renderTileOnTableHovered(
            Minecraft mc,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Level level,
            BlockPos tableAnchorPos,
            Vec3 worldPosition,
            float yawDegrees,
            ItemStack stack,
            int packedLight,
            int packedOverlay,
            boolean hovered) {
        renderTileOnTable(
                mc,
                poseStack,
                buffers,
                level,
                tableAnchorPos,
                worldPosition,
                yawDegrees,
                stack,
                packedLight,
                packedOverlay,
                hoveredScale(hovered),
                hoveredLift(hovered));
    }

    public static void renderTileOnTableHovered(
            Minecraft mc,
            PoseStack poseStack,
            MultiBufferSource buffers,
            Level level,
            BlockPos tableAnchorPos,
            Vec3 worldPosition,
            float yawDegrees,
            float pitchDegrees,
            ItemStack stack,
            int packedLight,
            int packedOverlay,
            boolean hovered) {
        renderTileOnTable(
                mc,
                poseStack,
                buffers,
                level,
                tableAnchorPos,
                worldPosition,
                yawDegrees,
                pitchDegrees,
                stack,
                packedLight,
                packedOverlay,
                hoveredScale(hovered),
                hoveredLift(hovered));
    }

    /**
     * White wireframe for an empty placement slot, in local space (yaw = how the slot faces on the rim).
     */
    public static void renderEmptySlotWireHint(
            PoseStack poseStack, MultiBufferSource buffers, BlockPos tableAnchorPos, Vec3 worldPosition, float yawDegrees) {
        AABB footprint =
                new AABB(
                        -HINT_BOX_XZ_HALF_LONG,
                        -HINT_BOX_Y_HALF,
                        -HINT_BOX_XZ_HALF_SHORT,
                        HINT_BOX_XZ_HALF_LONG,
                        HINT_BOX_Y_HALF,
                        HINT_BOX_XZ_HALF_SHORT);
        poseStack.pushPose();
        poseStack.translate(
                worldPosition.x - tableAnchorPos.getX(),
                worldPosition.y - tableAnchorPos.getY(),
                worldPosition.z - tableAnchorPos.getZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, footprint, 0.88f, 0.96f, 1f, 1f);
        poseStack.popPose();
    }

    /**
     * White wireframe for an empty slot where tiles lie flat on the table.
     */
    public static void renderEmptySlotWireHintFlat(
            PoseStack poseStack, MultiBufferSource buffers, BlockPos tableAnchorPos, Vec3 worldPosition, float yawDegrees) {
        AABB footprint =
                new AABB(
                        -HINT_FLAT_XZ_HALF_LONG,
                        -HINT_FLAT_Y_HALF,
                        -HINT_FLAT_XZ_HALF_SHORT,
                        HINT_FLAT_XZ_HALF_LONG,
                        HINT_FLAT_Y_HALF,
                        HINT_FLAT_XZ_HALF_SHORT);
        poseStack.pushPose();
        poseStack.translate(
                worldPosition.x - tableAnchorPos.getX(),
                worldPosition.y - tableAnchorPos.getY(),
                worldPosition.z - tableAnchorPos.getZ());
        // Keep flat-slot hint aligned with flat FIXED item rendering, which applies this shared Y correction.
        poseStack.translate(0.0, PITCHED_TILE_SURFACE_Y_ADJUST, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));
        poseStack.translate(0.0, 0.0, -HINT_FLAT_Z_OFFSET);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, footprint, 0.88f, 0.96f, 1f, 1f);
        poseStack.popPose();
    }

    public static void renderLastDiscardWireHighlight(
            PoseStack poseStack, MultiBufferSource buffers, BlockPos tableAnchorPos, Vec3 worldPosition, float yawDegrees) {
        AABB footprint = new AABB(
                -HINT_FLAT_XZ_HALF_LONG, -HINT_FLAT_Y_HALF, -HINT_FLAT_XZ_HALF_SHORT,
                HINT_FLAT_XZ_HALF_LONG,   HINT_FLAT_Y_HALF,  HINT_FLAT_XZ_HALF_SHORT);
        poseStack.pushPose();
        poseStack.translate(
                worldPosition.x - tableAnchorPos.getX(),
                worldPosition.y - tableAnchorPos.getY(),
                worldPosition.z - tableAnchorPos.getZ());
        poseStack.translate(0.0, PITCHED_TILE_SURFACE_Y_ADJUST, 0.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));
        poseStack.translate(0.0, 0.0, -HINT_FLAT_Z_OFFSET);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, footprint, 1.0f, 0.85f, 0.1f, 0.9f);
        poseStack.popPose();
    }

    public static void renderEmptySlotWireHint(
            PoseStack poseStack,
            MultiBufferSource buffers,
            BlockPos tableAnchorPos,
            Vec3 worldPosition,
            float yawDegrees,
            HintStyle hintStyle) {
        if (hintStyle == HintStyle.FLAT) {
            renderEmptySlotWireHintFlat(poseStack, buffers, tableAnchorPos, worldPosition, yawDegrees);
            return;
        }
        renderEmptySlotWireHint(poseStack, buffers, tableAnchorPos, worldPosition, yawDegrees);
    }

    public static boolean shouldRenderResolvedPlaceHint(
            MahjongTableBlockEntity table,
            @Nullable Player viewer,
            @Nullable MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolved,
            MahjongTableTabletopSlots.SurfaceInteractionKind expectedKind) {
        if (viewer == null || resolved == null || resolved.kind() != expectedKind) {
            return false;
        }
        return viewerMayPlaceInInventorySlot(table, resolved.invSlot(), viewer);
    }

    /** True when {@code invSlot} is empty and something in the viewer’s hands {@link MahjongTableBlockEntity#canPlaceItem can be placed} there. */
    public static boolean viewerMayPlaceInInventorySlot(MahjongTableBlockEntity table, int invSlot, Player viewer) {
        if (invSlot < 0 || invSlot >= table.getContainerSize()) {
            return false;
        }
        if (!table.getItem(invSlot).isEmpty()) {
            return false;
        }
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack st = viewer.getItemInHand(h);
            if (st.isEmpty()) {
                continue;
            }
            if (table.canPlaceItem(invSlot, st)) {
                return true;
            }
        }
        return false;
    }
}
