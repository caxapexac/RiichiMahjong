package com.riichimahjongforge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.MahjongTableTabletopSlots;

import java.util.UUID;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only drawing for in-world hand slots; geometry and picks live in {@link MahjongTableTabletopSlots}.
 */
public final class MahjongTableHandSlotsRenderer {

    private static final float TENPAI_DUST_SCALE = 0.1f;
    private static final double TENPAI_Y_OFFSET = 0.015;
    private static final double TENPAI_RISE_SPEED = 0.02;
    private static final double TENPAI_Z_OFFSET = 0.0;
    private static final double TENPAI_RANDOM_RADIUS = 0.015;
    private static final int TENPAI_LIFETIME_TICKS = 10;
    private static final float WIN_DUST_SCALE = 0.1f;
    private static final float WIN_SPAWN_CHANCE = 1.0f;
    private static final double WIN_Y_OFFSET = 0.015;
    private static final double WIN_RISE_SPEED = 0.02;
    private static final double WIN_Z_OFFSET = 0.0;
    private static final double WIN_RANDOM_RADIUS = 0.015;
    private static final int WIN_LIFETIME_TICKS = 10;
    private static final float RIICHI_DUST_SCALE = 0.1f;
    private static final float RIICHI_SPAWN_CHANCE = 1.0f;
    private static final double RIICHI_Y_OFFSET = 0.015;
    private static final double RIICHI_RISE_SPEED = 0.02;
    private static final double RIICHI_Z_OFFSET = 0.0;
    private static final double RIICHI_RANDOM_RADIUS = 0.015;
    private static final int RIICHI_LIFETIME_TICKS = 10;
    private static final Vector3f DUST_COLOR_WIN = new Vector3f(1.0f, 0.20f, 0.20f);
    private static final Vector3f DUST_COLOR_TENPAI = new Vector3f(0.92f, 0.98f, 1.0f);
    private static final Vector3f DUST_COLOR_RIICHI = new Vector3f(1.0f, 0.58f, 0.16f);

    private MahjongTableHandSlotsRenderer() {}

    private enum SeatParticleMode {
        NONE,
        TENPAI,
        RIICHI,
        WIN
    }

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
        long tick = mc.level != null ? mc.level.getGameTime() : 0L;

        for (int seat = 0; seat < MahjongTableBlockEntity.SEAT_COUNT; seat++) {
            if (!table.isSeatEnabled(seat)) {
                continue;
            }
            MahjongTableBlockEntity.ClientSeatHintState hintState = table.clientSeatHintState(seat, tick);
            UUID owner = table.occupantAt(seat);
            boolean isOwnerView =
                    viewer != null && owner != null && owner.equals(viewer.getUUID());
            boolean ownerWinAction = isOwnerView && hintState.tsumoActionAvailable();
            boolean ownerOnlyTenpai = isOwnerView && !ownerWinAction && hintState.tenpai();
            boolean riichiActive = hintState.riichiAvailable();
            SeatParticleMode particleMode = resolveSeatParticleMode(ownerOnlyTenpai, riichiActive, ownerWinAction);
            boolean emitParticles = particleMode != SeatParticleMode.NONE;
            boolean emitThisSeatTick = hintState.emitParticlesThisTick();
            float yaw = MahjongTableSurfacePlacements.yawHandTilesAtSeat(seat);
            boolean revealWinningHandFlat =
                    table.isInHandResultPhase() && table.handResultWinnerSeat() == seat;
            int baseInv = MahjongTableBlockEntity.playerZoneBase(seat);
            for (int i = 0; i < MahjongTableBlockEntity.PLAYER_ZONE_SLOTS_PER_SEAT; i++) {
                ItemStack st = table.getItem(baseInv + i);
                if (st.isEmpty()) {
                    continue;
                }
                var p = MahjongTableTabletopSlots.worldPosForHandSlot(table.getBlockPos(), seat, i);
                boolean hovered = (baseInv + i) == hoveredInv;
                if (revealWinningHandFlat) {
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
                } else {
                    MahjongTableSurfacePlacements.renderTileOnTableHovered(
                            mc,
                            poseStack,
                            buffers,
                            table.getLevel(),
                            table.getBlockPos(),
                            p,
                            yaw,
                            st,
                            packedLight,
                            packedOverlay,
                            hovered);
                }
                if (emitParticles
                        && mc.level != null
                        && !mc.isPaused()
                        && emitThisSeatTick) {
                    // TODO: move particle spawning out of BER render() into a dedicated client tick/animate path.
                    // Renderers should only draw; tick-driven emission will match vanilla-style behavior better.
                    // Owner action layer: tenpai uses dust hint, win uses only selected win particle type.
                    if (particleMode == SeatParticleMode.TENPAI && (tick + seat) % 5L == 0L) {
                        double tenpaiDx = (mc.level.random.nextDouble() - 0.5) * (2.0 * TENPAI_RANDOM_RADIUS);
                        double tenpaiDz = (mc.level.random.nextDouble() - 0.5) * (2.0 * TENPAI_RANDOM_RADIUS);
                        Particle tenpaiParticle = mc.particleEngine.createParticle(
                                new DustParticleOptions(DUST_COLOR_TENPAI, TENPAI_DUST_SCALE),
                                p.x + tenpaiDx,
                                p.y + TENPAI_Y_OFFSET,
                                p.z + tenpaiDz + TENPAI_Z_OFFSET,
                                0.0,
                                TENPAI_RISE_SPEED,
                                0.0);
                        if (tenpaiParticle != null) {
                            tenpaiParticle.setParticleSpeed(0.0, TENPAI_RISE_SPEED, 0.0);
                            tenpaiParticle.setLifetime(TENPAI_LIFETIME_TICKS);
                        }
                    }
                    // Riichi layer: same particle style as win condition, but orange-tinted.
                    if (particleMode == SeatParticleMode.RIICHI
                            && (tick + seat) % 2L == 0L
                            && mc.level.random.nextFloat() < RIICHI_SPAWN_CHANCE) {
                        double riichiDx = (mc.level.random.nextDouble() - 0.5) * (2.0 * RIICHI_RANDOM_RADIUS);
                        double riichiDz = (mc.level.random.nextDouble() - 0.5) * (2.0 * RIICHI_RANDOM_RADIUS);
                        Particle riichiParticle = mc.particleEngine.createParticle(
                                new DustParticleOptions(DUST_COLOR_RIICHI, RIICHI_DUST_SCALE),
                                p.x + riichiDx,
                                p.y + RIICHI_Y_OFFSET,
                                p.z + RIICHI_Z_OFFSET + riichiDz,
                                0.0,
                                RIICHI_RISE_SPEED,
                                0.0);
                        if (riichiParticle != null) {
                            riichiParticle.setParticleSpeed(0.0, RIICHI_RISE_SPEED, 0.0);
                            riichiParticle.setLifetime(RIICHI_LIFETIME_TICKS);
                        }
                    }
                    if (particleMode == SeatParticleMode.WIN
                            && (tick + seat) % 2L == 0L
                            && mc.level.random.nextFloat() < WIN_SPAWN_CHANCE) {
                        double fireDx = (mc.level.random.nextDouble() - 0.5) * (2.0 * WIN_RANDOM_RADIUS);
                        double fireDz = (mc.level.random.nextDouble() - 0.5) * (2.0 * WIN_RANDOM_RADIUS);
                        double baseX = p.x + fireDx;
                        double baseY = p.y + WIN_Y_OFFSET;
                        double baseZ = p.z + WIN_Z_OFFSET + fireDz;
                        double vy = WIN_RISE_SPEED;
                        // Spawn win particle via particle engine so we can adjust lifetime.
                        Particle created = mc.particleEngine.createParticle(
                                new DustParticleOptions(DUST_COLOR_WIN, WIN_DUST_SCALE),
                                baseX,
                                baseY,
                                baseZ,
                                0.0,
                                vy,
                                0.0);
                        if (created != null) {
                            created.setParticleSpeed(0.0, vy, 0.0);
                            created.setLifetime(WIN_LIFETIME_TICKS);
                        }
                    }
                }
            }
        }

        if (MahjongTableSurfacePlacements.shouldRenderResolvedPlaceHint(
                table, viewer, resolvedSurface, MahjongTableTabletopSlots.SurfaceInteractionKind.HAND)) {
            BlockPos bp = table.getBlockPos();
            var p = MahjongTableTabletopSlots.worldPosForHandSlot(bp, resolvedSurface.seat(), resolvedSurface.slotIndex());
            MahjongTableSurfacePlacements.renderEmptySlotWireHint(
                    poseStack, buffers, bp, p, MahjongTableSurfacePlacements.yawHandTilesAtSeat(resolvedSurface.seat()));
        }
    }

    private static SeatParticleMode resolveSeatParticleMode(
            boolean ownerOnlyTenpai, boolean riichiActive, boolean ownerWinAction) {
        if (ownerWinAction) {
            return SeatParticleMode.WIN;
        }
        if (riichiActive) {
            return SeatParticleMode.RIICHI;
        }
        if (ownerOnlyTenpai) {
            return SeatParticleMode.TENPAI;
        }
        return SeatParticleMode.NONE;
    }

}
