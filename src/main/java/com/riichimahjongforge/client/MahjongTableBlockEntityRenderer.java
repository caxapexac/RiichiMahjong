package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mahjongcore.rules.ClaimLegalityRules;
import com.riichimahjongforge.MahjongTableBlock;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTableTabletopSlots;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.TableMatchPhase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.mojang.math.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Locale;

/** Client-only: seat labels and concealed-hand item stacks on the mahjong table. */
public final class MahjongTableBlockEntityRenderer implements BlockEntityRenderer<MahjongTableBlockEntity> {

    private static final float TEXT_SCALE = 0.0105f;
    private static final int TEXT_COLOR = 0xE8E8E8;
    private static final int TURN_COLOR = 0xFFF0D040;
    private static final int TEXT_BG = 0x80101010;
    private static final double MAX_RENDER_DIST_SQ = 24.0 * 24.0;
    private static final float LABEL_Y_OFFSET_TEXT_HEIGHT_RATIO = 0.60f;
    private static final float RESULT_TEXT_SCALE = 0.0100f;
    private static final float ACTION_TEXT_SCALE = 0.0078f;
    private static final float ACTION_TEXT_Y_OFFSET_TEXT_HEIGHT_RATIO = 0.34f;
    private static final int ACTION_DEFAULT_COLOR = 0xFFE8E8E8;
    private static final int ACTION_RON_COLOR = 0xFFFF5757;
    private static final int ACTION_TSUMO_COLOR = 0xFFFF6A6A;
    private static final int ACTION_RIICHI_COLOR = 0xFFFFD05C;
    private static final int ACTION_PASS_COLOR = 0xFFBFC6D2;
    private static final int ACTION_BG_DEFAULT = 0xB0444A58;
    private static final int ACTION_BG_RON = 0xC05A2222;
    private static final int ACTION_BG_TSUMO = 0xC05A2626;
    private static final int ACTION_BG_RIICHI = 0xC05D4A1F;
    private static final int ACTION_BG_PASS = 0xB0393D48;
    private static final int ACTION_SIDE_DARKEN = 52;
    private static final float ACTION_HOVER_SCALE = 1.14f;
    private static final double ACTION_TOP_WORLD_LIFT = 0.010;
    private static final float ACTION_PRISM_DEPTH = 2.1f;
    private static final float ACTION_PLATE_PAD_X = 3.0f;
    private static final float ACTION_PLATE_PAD_Y = 1.2f;
    private static final double ACTION_TEXT_WORLD_LIFT = 0.0008;
    private static final int KAN_OPTION_MIN_LABEL_WIDTH = 31;
    private static final float KAN_OPTION_TILE_SCALE_FACTOR = 0.75f;
    private static String lastHoveredTileKey = null;
    private static String lastHoveredActionKey = null;

    private final Font font;

    public MahjongTableBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {
        this.font = ctx.getFont();
    }

    @Override
    public void render(
            MahjongTableBlockEntity table,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        Player viewer = mc.player;
        if (viewer == null) {
            return;
        }
        if (viewer.distanceToSqr(
                        table.getBlockPos().getX() + 0.5,
                        table.getBlockPos().getY() + 0.5,
                        table.getBlockPos().getZ() + 0.5)
                > MAX_RENDER_DIST_SQ) {
            return;
        }
        if (mc.getEntityRenderDispatcher().camera == null) {
            return;
        }
        int turnSeat = -1;
        if (table.isInMatch()) {
            var gs = table.gameStateOrNull();
            if (gs != null) {
                turnSeat = gs.currentTurnSeat;
            }
        }
        int viewerSeat = -1;
        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (viewer.getUUID().equals(table.occupantAt(seat))) {
                viewerSeat = seat;
                break;
            }
        }
        List<MahjongTableBlockEntity.SeatAction> viewerActions =
                viewerSeat >= 0 ? table.visibleSeatActionRow(viewerSeat) : List.of();
        int hoveredActionIndex = resolveHoveredActionIndex(table, mc, viewerSeat, viewerActions.size());

        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            Component label = seatLabel(table, seat);
            if (label == null) {
                continue;
            }
            boolean isTurn = turnSeat == seat;
            int color = isTurn ? TURN_COLOR : TEXT_COLOR;
            renderLabelAtSeatEdge(poseStack, buffers, packedLight, mc, seat, label, color);
        }
        renderSeatActionRow(
                table,
                poseStack,
                buffers,
                packedLight,
                packedOverlay,
                mc,
                viewerSeat,
                viewerActions,
                hoveredActionIndex);
        renderHandResultOverlay(table, poseStack, buffers, packedLight, mc);

        MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolvedSurface = null;
        MahjongTableTabletopSlots.SurfacePickCandidates surfaceCandidates =
                MahjongTableTabletopSlots.emptySurfacePickCandidates();
        boolean viewerLookingAtThisTableTop = false;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bhr && table.getLevel() != null) {
            if (bhr.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = bhr.getBlockPos();
                BlockState hitState = table.getLevel().getBlockState(hitPos);
                if (hitState.getBlock() instanceof MahjongTableBlock) {
                    BlockPos master = MahjongTableBlock.masterBlockPos(hitPos, hitState);
                    if (master.equals(table.getBlockPos())) {
                        if (bhr.getDirection() == Direction.UP) {
                            viewerLookingAtThisTableTop = true;
                            surfaceCandidates = MahjongTableTabletopSlots.collectSurfacePickCandidates(table, bhr.getLocation());
                            if (table.isInMatch()) {
                                MahjongTableTabletopSlots.HandSlotPick handPick = surfaceCandidates.handPick();
                                if (hoveredActionIndex >= 0) {
                                    // Action chips have higher click priority than hand tiles; mirror that in hover.
                                    resolvedSurface = null;
                                } else if (handPick != null
                                        && viewerSeat >= 0
                                        && handPick.seat() == viewerSeat
                                        && surfaceCandidates.handInv() >= 0
                                        && !table.getItem(surfaceCandidates.handInv()).isEmpty()) {
                                    resolvedSurface = new MahjongTableTabletopSlots.ResolvedSurfaceInteraction(
                                            MahjongTableTabletopSlots.SurfaceInteractionKind.HAND,
                                            handPick.seat(),
                                            handPick.slotIndex(),
                                            false,
                                            surfaceCandidates.handInv());
                                }
                            } else {
                                boolean takingFromTable = viewer.getMainHandItem().isEmpty() && viewer.getOffhandItem().isEmpty();
                                resolvedSurface =
                                        MahjongTableTabletopSlots.resolveSurfaceInteraction(
                                                table, surfaceCandidates, takingFromTable);
                            }
                        }
                    }
                }
            }
        }
        maybePlayHoverSounds(mc, table, viewerLookingAtThisTableTop, hoveredActionIndex, resolvedSurface);

        MahjongTableHandSlotsRenderer.render(table, poseStack, buffers, packedLight, packedOverlay, mc, viewer, resolvedSurface);
        MahjongTableDiscardSlotsRenderer.render(
                table, poseStack, buffers, packedLight, packedOverlay, mc, viewer, resolvedSurface);
        MahjongTableMeldSlotsRenderer.render(
                table, poseStack, buffers, packedLight, packedOverlay, mc, viewer, resolvedSurface);
        MahjongTableWallSlotsRenderer.render(table, poseStack, buffers, packedLight, packedOverlay, mc, resolvedSurface);
    }

    private static void maybePlayHoverSounds(
            Minecraft mc,
            MahjongTableBlockEntity table,
            boolean viewerLookingAtThisTableTop,
            int hoveredActionIndex,
            MahjongTableTabletopSlots.ResolvedSurfaceInteraction resolvedSurface) {
        if (mc.player == null || !viewerLookingAtThisTableTop) {
            return;
        }
        int hoveredTileInvSlot = resolvedSurface == null ? -1 : resolvedSurface.invSlot();
        boolean hoveredTileHasStack =
                hoveredTileInvSlot >= 0 && hoveredTileInvSlot < table.getContainerSize()
                        && !table.getItem(hoveredTileInvSlot).isEmpty();
        String tileKey =
                hoveredTileHasStack ? (table.getBlockPos() + "#tile:" + hoveredTileInvSlot) : null;
        String actionKey = hoveredActionIndex < 0 ? null : table.getBlockPos() + "#action:" + hoveredActionIndex;

        if (tileKey == null) {
            lastHoveredTileKey = null;
        } else if (!tileKey.equals(lastHoveredTileKey)) {
            mc.player.playSound(RiichiMahjongForgeMod.TILE_HOVER_TILE_SOUND.get(), 0.55f, 1.0f);
            lastHoveredTileKey = tileKey;
        }

        if (actionKey == null) {
            lastHoveredActionKey = null;
        } else if (!actionKey.equals(lastHoveredActionKey)) {
            mc.player.playSound(RiichiMahjongForgeMod.TILE_HOVER_ACTION_SOUND.get(), 0.55f, 1.0f);
            lastHoveredActionKey = actionKey;
        }
    }

    private void renderSeatActionRow(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Minecraft mc,
            int viewerSeat,
            List<MahjongTableBlockEntity.SeatAction> actions,
            int hoveredAction) {
        if (viewerSeat < 0 || actions.isEmpty()) {
            return;
        }

        for (int i = 0; i < actions.size(); i++) {
            MahjongTableBlockEntity.SeatAction action = actions.get(i);
            var p = MahjongTableTabletopSlots.worldPosForActionChip(table.getBlockPos(), viewerSeat, i, actions.size());
            Direction dir = MahjongTableBlockEntity.tableEdgeFromSeat(viewerSeat);
            float yawDeg = switch (dir) {
                case NORTH -> 0.0f;
                case SOUTH -> 180.0f;
                case WEST -> 90.0f;
                case EAST -> -90.0f;
                default -> 0.0f;
            };
            Component text = Component.literal(actionLabel(action));
            boolean hovered = hoveredAction == i;
            int textColor = hovered ? 0xFFFFFFFF : actionTextColor(action);
            float pulse = winActionPulse(table, action, viewerSeat, i);
            boolean pulsingWinAction = action == MahjongTableBlockEntity.SeatAction.RON
                    || action == MahjongTableBlockEntity.SeatAction.TSUMO
                    || action == MahjongTableBlockEntity.SeatAction.CHANKAN;
            int bg = actionBgColor(action, hovered, pulse);
            float pulseScale = pulsingWinAction ? (1.0f + 0.10f * pulse) : 1.0f;
            float pulseDepthScale = pulsingWinAction ? (1.0f + 0.18f * pulse) : 1.0f;
            double pulseTopLift = ACTION_TOP_WORLD_LIFT * (pulsingWinAction ? (1.0 + 0.20 * pulse) : 1.0);
            double pulseTextLift = ACTION_TEXT_WORLD_LIFT * (pulsingWinAction ? (1.0 + 0.20 * pulse) : 1.0);
            float actionScale = ACTION_TEXT_SCALE * (hovered ? ACTION_HOVER_SCALE : 1.0f) * pulseScale;
            int w = font.width(text);
            if (isKanOptionAction(action)) {
                w = Math.max(w, KAN_OPTION_MIN_LABEL_WIDTH);
            }
            float plateHalfW = (w * 0.5f) + ACTION_PLATE_PAD_X;
            float plateHalfH = (font.lineHeight * 0.5f) + ACTION_PLATE_PAD_Y;
            float y0 = -font.lineHeight * ACTION_TEXT_Y_OFFSET_TEXT_HEIGHT_RATIO;
            org.joml.Matrix4f topPose =
                    actionPlatePose(
                            poseStack,
                            table,
                            p.x,
                            p.y + pulseTopLift,
                            p.z,
                            yawDeg,
                            actionScale);
            renderActionPrism(
                    buffers,
                    topPose,
                    plateHalfW,
                    plateHalfH,
                    ACTION_PRISM_DEPTH * pulseDepthScale,
                    bg,
                    darkenArgb(bg, ACTION_SIDE_DARKEN));
            if (isKanOptionAction(action)) {
                renderKanOptionGhostTiles(
                        table,
                        poseStack,
                        buffers,
                        packedLight,
                        packedOverlay,
                        mc,
                        viewerSeat,
                        action,
                        hovered,
                        p.x,
                        p.y,
                        p.z);
            } else if (isChiOptionAction(action)) {
                renderChiOptionGhostTiles(
                        table,
                        poseStack,
                        buffers,
                        packedLight,
                        packedOverlay,
                        mc,
                        viewerSeat,
                        action,
                        hovered,
                        p.x,
                        p.y,
                        p.z);
            } else {
                font.drawInBatch(
                        text,
                        -w / 2.0f,
                        y0,
                        textColor,
                        false,
                        raisedTextPose(topPose, pulseTextLift / actionScale),
                        buffers,
                        Font.DisplayMode.NORMAL,
                        0,
                        packedLight);
            }
        }
    }

    private static boolean isKanOptionAction(MahjongTableBlockEntity.SeatAction action) {
        return action == MahjongTableBlockEntity.SeatAction.KAN_OPTION_1
                || action == MahjongTableBlockEntity.SeatAction.KAN_OPTION_2
                || action == MahjongTableBlockEntity.SeatAction.KAN_OPTION_3;
    }

    private static int kanOptionIndex(MahjongTableBlockEntity.SeatAction action) {
        return switch (action) {
            case KAN_OPTION_1 -> 0;
            case KAN_OPTION_2 -> 1;
            case KAN_OPTION_3 -> 2;
            default -> -1;
        };
    }

    private static boolean isChiOptionAction(MahjongTableBlockEntity.SeatAction action) {
        return action == MahjongTableBlockEntity.SeatAction.CHI_OPTION_1
                || action == MahjongTableBlockEntity.SeatAction.CHI_OPTION_2;
    }

    private static int chiOptionIndex(MahjongTableBlockEntity.SeatAction action) {
        return switch (action) {
            case CHI_OPTION_1 -> 0;
            case CHI_OPTION_2 -> 1;
            default -> -1;
        };
    }

    private static void renderKanOptionGhostTiles(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Minecraft mc,
            int seat,
            MahjongTableBlockEntity.SeatAction action,
            boolean hovered,
            double baseX,
            double baseY,
            double baseZ) {
        if (table.getLevel() == null) {
            return;
        }
        int optionIndex = kanOptionIndex(action);
        if (optionIndex < 0) {
            return;
        }
        List<Integer> candidates = table.visibleKanCandidateCodes(seat);
        if (optionIndex >= candidates.size()) {
            return;
        }
        var item = com.riichimahjongforge.MahjongTileItems.itemForCode(candidates.get(optionIndex));
        if (item == null) {
            return;
        }
        ItemStack tileStack = new ItemStack(item);
        Direction dir = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = dir.getClockWise();
        double tileStep = MahjongTableTabletopSlots.HORIZONTAL_TILE_STEP * 0.88;
        double start = tileStep * 1.5;
        double tileY = baseY + 0.072 + (hovered ? 0.008 : 0.0);
        float yaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(seat);
        float scale = MahjongTableSurfacePlacements.TILE_RENDER_SCALE * KAN_OPTION_TILE_SCALE_FACTOR * (hovered ? 1.05f : 1.0f);
        for (int i = 0; i < 4; i++) {
            double alongM = start - tileStep * i;
            Vec3 tilePos =
                    new Vec3(
                            baseX + along.getStepX() * alongM,
                            tileY,
                            baseZ + along.getStepZ() * alongM);
            MahjongTableSurfacePlacements.renderTileOnTable(
                    mc,
                    poseStack,
                    buffers,
                    table.getLevel(),
                    table.getBlockPos(),
                    tilePos,
                    yaw,
                    90.0f,
                    tileStack,
                    packedLight,
                    packedOverlay,
                    scale,
                    0.0);
        }
    }

    private static void renderChiOptionGhostTiles(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay,
            Minecraft mc,
            int seat,
            MahjongTableBlockEntity.SeatAction action,
            boolean hovered,
            double baseX,
            double baseY,
            double baseZ) {
        if (table.getLevel() == null) {
            return;
        }
        int optionIndex = chiOptionIndex(action);
        if (optionIndex < 0) {
            return;
        }
        List<ClaimLegalityRules.ChiPair> pairs = table.visibleChiCandidatePairs(seat);
        if (optionIndex >= pairs.size()) {
            return;
        }
        ClaimLegalityRules.ChiPair pair = pairs.get(optionIndex);
        int[] tileCodes = {pair.tileA(), pair.tileB()};
        Direction dir = MahjongTableBlockEntity.tableEdgeFromSeat(seat);
        Direction along = dir.getClockWise();
        double tileStep = MahjongTableTabletopSlots.HORIZONTAL_TILE_STEP * 0.88;
        double start = tileStep * 0.5;
        double tileY = baseY + 0.072 + (hovered ? 0.008 : 0.0);
        float yaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(seat);
        float scale = MahjongTableSurfacePlacements.TILE_RENDER_SCALE * KAN_OPTION_TILE_SCALE_FACTOR * (hovered ? 1.05f : 1.0f);
        for (int i = 0; i < tileCodes.length; i++) {
            var item = com.riichimahjongforge.MahjongTileItems.itemForCode(tileCodes[i]);
            if (item == null) {
                continue;
            }
            ItemStack tileStack = new ItemStack(item);
            double alongM = start - tileStep * i;
            Vec3 tilePos = new Vec3(
                    baseX + along.getStepX() * alongM,
                    tileY,
                    baseZ + along.getStepZ() * alongM);
            MahjongTableSurfacePlacements.renderTileOnTable(
                    mc,
                    poseStack,
                    buffers,
                    table.getLevel(),
                    table.getBlockPos(),
                    tilePos,
                    yaw,
                    90.0f,
                    tileStack,
                    packedLight,
                    packedOverlay,
                    scale,
                    0.0);
        }
    }

    private static int resolveHoveredActionIndex(
            MahjongTableBlockEntity table, Minecraft mc, int viewerSeat, int actionCount) {
        if (viewerSeat < 0 || actionCount <= 0 || table.getLevel() == null) {
            return -1;
        }
        HitResult hr = mc.hitResult;
        if (!(hr instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            return -1;
        }
        BlockState hitState = table.getLevel().getBlockState(bhr.getBlockPos());
        if (!(hitState.getBlock() instanceof MahjongTableBlock)) {
            return -1;
        }
        BlockPos master = MahjongTableBlock.masterBlockPos(bhr.getBlockPos(), hitState);
        if (!master.equals(table.getBlockPos())) {
            return -1;
        }
        MahjongTableTabletopSlots.ActionChipPick pick =
                MahjongTableTabletopSlots.pickSeatActionChip(table, viewerSeat, bhr.getLocation(), actionCount);
        return pick != null ? pick.actionIndex() : -1;
    }

    private org.joml.Matrix4f actionPlatePose(
            PoseStack poseStack,
            MahjongTableBlockEntity table,
            double wx,
            double wy,
            double wz,
            float yawDeg,
            float actionScale) {
        poseStack.pushPose();
        poseStack.translate(
                wx - table.getBlockPos().getX(),
                wy - table.getBlockPos().getY(),
                wz - table.getBlockPos().getZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.translate(0.0, 0.0, -0.0045);
        poseStack.scale(-actionScale, -actionScale, actionScale);
        org.joml.Matrix4f out = new org.joml.Matrix4f(poseStack.last().pose());
        poseStack.popPose();
        return out;
    }

    private static String actionLabel(MahjongTableBlockEntity.SeatAction action) {
        return switch (action) {
            case RIICHI -> "RIICHI";
            case TSUMO -> "TSUMO";
            case ANKAN -> "KAN";
            case KAN_OPTION_1, KAN_OPTION_2, KAN_OPTION_3 -> "KAN";
            case CANCEL -> "CANCEL";
            case RON -> "RON";
            case CHANKAN -> "CHANKAN";
            case PASS -> "PASS";
            case PON -> "PON";
            case DAIMIN_KAN -> "KAN";
            case CHI, CHI_OPTION_1, CHI_OPTION_2 -> "CHI";
        };
    }

    private static int actionTextColor(MahjongTableBlockEntity.SeatAction action) {
        return switch (action) {
            case RON, CHANKAN -> ACTION_RON_COLOR;
            case TSUMO -> ACTION_TSUMO_COLOR;
            case RIICHI -> ACTION_RIICHI_COLOR;
            case PASS, CANCEL -> ACTION_PASS_COLOR;
            case ANKAN, KAN_OPTION_1, KAN_OPTION_2, KAN_OPTION_3,
                    PON, DAIMIN_KAN, CHI, CHI_OPTION_1, CHI_OPTION_2 -> ACTION_DEFAULT_COLOR;
        };
    }

    private static int actionBgColor(MahjongTableBlockEntity.SeatAction action, boolean hovered, float pulse) {
        int base = switch (action) {
            case RON, CHANKAN -> ACTION_BG_RON;
            case TSUMO -> ACTION_BG_TSUMO;
            case RIICHI -> ACTION_BG_RIICHI;
            case PASS, CANCEL -> ACTION_BG_PASS;
            case ANKAN, KAN_OPTION_1, KAN_OPTION_2, KAN_OPTION_3,
                    PON, DAIMIN_KAN, CHI, CHI_OPTION_1, CHI_OPTION_2 -> ACTION_BG_DEFAULT;
        };
        if (action == MahjongTableBlockEntity.SeatAction.RON
                || action == MahjongTableBlockEntity.SeatAction.TSUMO
                || action == MahjongTableBlockEntity.SeatAction.CHANKAN) {
            base = brightenArgb(base, Math.round(50.0f * pulse));
        }
        if (!hovered) {
            return base;
        }
        return brightenArgb(base, 90);
    }

    private static float winActionPulse(
            MahjongTableBlockEntity table,
            MahjongTableBlockEntity.SeatAction action,
            int seat,
            int actionIndex) {
        if (action != MahjongTableBlockEntity.SeatAction.RON
                && action != MahjongTableBlockEntity.SeatAction.TSUMO
                && action != MahjongTableBlockEntity.SeatAction.CHANKAN) {
            return 0.0f;
        }
        long tick = table.getLevel() != null ? table.getLevel().getGameTime() : 0L;
        double phase = (tick * 0.38) + (seat * 0.85) + (actionIndex * 0.45);
        return (float) ((Math.sin(phase) + 1.0) * 0.5);
    }

    private static int brightenArgb(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, ((color >>> 16) & 0xFF) + delta);
        int g = Math.min(255, ((color >>> 8) & 0xFF) + delta);
        int b = Math.min(255, (color & 0xFF) + delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darkenArgb(int color, int delta) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.max(0, ((color >>> 16) & 0xFF) - delta);
        int g = Math.max(0, ((color >>> 8) & 0xFF) - delta);
        int b = Math.max(0, (color & 0xFF) - delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    private static org.joml.Matrix4f raisedTextPose(org.joml.Matrix4f topPose, double localZOffset) {
        return new org.joml.Matrix4f(topPose).translate(0.0f, 0.0f, (float) -localZOffset);
    }

    private static void renderActionPrism(
            MultiBufferSource buffers,
            org.joml.Matrix4f pose,
            float halfW,
            float halfH,
            float depth,
            int topColor,
            int sideColor) {
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        float zTop = 0.0f;
        float zBottom = depth;
        emitQuad(quads, pose, -halfW, -halfH, zTop, halfW, -halfH, zTop, halfW, halfH, zTop, -halfW, halfH, zTop, topColor);
        emitQuad(quads, pose, -halfW, halfH, zBottom, halfW, halfH, zBottom, halfW, -halfH, zBottom, -halfW, -halfH, zBottom, sideColor);
        emitQuad(quads, pose, -halfW, -halfH, zBottom, halfW, -halfH, zBottom, halfW, -halfH, zTop, -halfW, -halfH, zTop, sideColor);
        emitQuad(quads, pose, -halfW, halfH, zTop, halfW, halfH, zTop, halfW, halfH, zBottom, -halfW, halfH, zBottom, sideColor);
        emitQuad(quads, pose, -halfW, -halfH, zTop, -halfW, halfH, zTop, -halfW, halfH, zBottom, -halfW, -halfH, zBottom, sideColor);
        emitQuad(quads, pose, halfW, -halfH, zBottom, halfW, halfH, zBottom, halfW, halfH, zTop, halfW, -halfH, zTop, sideColor);
    }

    private static void emitQuad(
            VertexConsumer vc,
            org.joml.Matrix4f pose,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        vc.vertex(pose, x1, y1, z1).color(r, g, b, a).endVertex();
        vc.vertex(pose, x2, y2, z2).color(r, g, b, a).endVertex();
        vc.vertex(pose, x3, y3, z3).color(r, g, b, a).endVertex();
        vc.vertex(pose, x4, y4, z4).color(r, g, b, a).endVertex();
    }

    private void renderHandResultOverlay(
            MahjongTableBlockEntity table,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            Minecraft mc) {
        if (!table.isInHandResultPhase()) {
            return;
        }
        java.util.ArrayList<Line> lines = new java.util.ArrayList<>();
        if (!table.handResultRoundTitle().isEmpty()) {
            String roundTitle = table.handResultRoundTitle();
            String headline = table.handResultHeadline();
            if (headline == null || !headline.startsWith(roundTitle)) {
                lines.add(new Line(Component.literal(roundTitle), 0xFFE8E8E8));
            }
        }
        if (!table.handResultHeadline().isEmpty()) {
            lines.add(new Line(Component.literal(table.handResultHeadline()), table.handResultHeadlineColor()));
        }
        for (String s : table.handResultYakuLines()) {
            lines.add(new Line(Component.literal(s), 0xFFE0E0E0));
        }
        for (String s : table.handResultDeltaLines()) {
            lines.add(new Line(Component.literal(s), 0xFFE8F7FF));
        }
        if (!table.handResultFooter().isEmpty()) {
            lines.add(new Line(Component.literal(table.handResultFooter()), table.handResultFooterColor()));
        }
        if (lines.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 1.65, 0.5);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-RESULT_TEXT_SCALE, -RESULT_TEXT_SCALE, RESULT_TEXT_SCALE);
        float y = 0.0f;
        for (Line line : lines) {
            int w = font.width(line.text());
            float x = -w / 2.0f;
            font.drawInBatch(
                    line.text(),
                    x,
                    y,
                    line.color(),
                    false,
                    poseStack.last().pose(),
                    buffers,
                    Font.DisplayMode.NORMAL,
                    TEXT_BG,
                    packedLight);
            y += font.lineHeight + 1.0f;
        }
        poseStack.popPose();
    }

    private void renderLabelAtSeatEdge(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            Minecraft mc,
            int seat,
            Component label,
            int color) {
        Direction dir = MahjongTableBlockEntity.tableEdgeFromSeat(seat);

        // The master block is centered in a 3x3 multiblock. Place text further out and slightly below the tabletop.
        // Coords are within the BE block space: x/z in [0..1] span the center block.
        double x = 0.5;
        double y = 0.55;
        double z = 0.5;
        double push = 1.01;
        x += dir.getStepX() * push;
        z += dir.getStepZ() * push;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // Fixed world-space orientation (no billboarding). Face outward from the table.
        float yawDeg = switch (dir) {
            case NORTH -> 0.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        int w = font.width(label);
        float x0 = -w / 2.0f;
        float y0 = -font.lineHeight * LABEL_Y_OFFSET_TEXT_HEIGHT_RATIO;
        font.drawInBatch(
                label,
                x0,
                y0,
                color,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                TEXT_BG,
                packedLight);
        poseStack.popPose();
    }

    private static Component seatLabel(MahjongTableBlockEntity table, int seat) {
        var id = table.occupantAt(seat);
        if (id == null) {
            if (table.getMatchPhase() == TableMatchPhase.WAITING && table.allowGameplay()) {
                return Component.translatable("riichi_mahjong_forge.label.join");
            }
            return null;
        }
        String wind = Component.translatable(
                        "riichi_mahjong_forge.chat.lobby.face."
                                + MahjongTableBlockEntity.faceFromSeat(seat).getName())
                .getString()
                .toUpperCase(Locale.ROOT);
        return Component.literal(wind + ": " + table.visibleSeatPoints(seat));
    }

    private record Line(Component text, int color) {}

    @Override
    public boolean shouldRenderOffScreen(MahjongTableBlockEntity blockEntity) {
        return false;
    }
}
