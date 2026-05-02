package com.riichimahjongforge.client;

import com.riichimahjongforge.MahjongTableBlock;
import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.TableMatchPhase;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-side action bar hint for seat join/leave on table edge hover. */
@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MahjongTableSeatHoverHintClient {

    private static final String KEY_SHIFT_JOIN = "riichi_mahjong_forge.hint.lobby.shift_join_with_wrench";
    private static final String KEY_SHIFT_LEAVE = "riichi_mahjong_forge.hint.lobby.shift_leave_with_wrench";
    private static final String KEY_CENTER_ACTIONS = "riichi_mahjong_forge.hint.lobby.center_actions";

    private static String activeHintKey;

    private MahjongTableSeatHoverHintClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            clearHint(mc);
            return;
        }
        String nextHint = resolveSeatHoverHint(mc);
        if (Objects.equals(activeHintKey, nextHint)) {
            return;
        }
        if (nextHint == null) {
            clearHint(mc);
            return;
        }
        mc.gui.setOverlayMessage(Component.translatable(nextHint), false);
        activeHintKey = nextHint;
    }

    private static void clearHint(Minecraft mc) {
        if (activeHintKey == null) {
            return;
        }
        mc.gui.setOverlayMessage(Component.empty(), false);
        activeHintKey = null;
    }

    private static String resolveSeatHoverHint(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockState hitState = mc.level.getBlockState(bhr.getBlockPos());
        if (!(hitState.getBlock() instanceof MahjongTableBlock)) {
            return null;
        }
        BlockPos masterPos = MahjongTableBlock.masterBlockPos(bhr.getBlockPos(), hitState);
        BlockEntity be = mc.level.getBlockEntity(masterPos);
        if (!(be instanceof MahjongTableBlockEntity table)) {
            return null;
        }
        if (table.getMatchPhase() != TableMatchPhase.WAITING) {
            return null;
        }
        if (!table.allowGameplay()) {
            return null;
        }
        int px = hitState.getValue(MahjongTableBlock.PART_X);
        int pz = hitState.getValue(MahjongTableBlock.PART_Z);
        if (px == 1 && pz == 1) {
            return KEY_CENTER_ACTIONS;
        }
        int seat = seatFromClickedPart(hitState, bhr.getDirection());
        if (seat < 0 || !table.isSeatEnabled(seat)) {
            return null;
        }
        return mc.player.getUUID().equals(table.occupantAt(seat)) ? KEY_SHIFT_LEAVE : KEY_SHIFT_JOIN;
    }

    /**
     * Mirrors server click seat resolution so client hints match exactly.
     */
    private static int seatFromClickedPart(BlockState state, Direction face) {
        int px = state.getValue(MahjongTableBlock.PART_X);
        int pz = state.getValue(MahjongTableBlock.PART_Z);
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
}
