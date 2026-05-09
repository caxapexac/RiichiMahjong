package com.riichimahjong.mahjongtable.client;

import com.riichimahjong.common.BaseMultipartBlock;
import com.riichimahjong.common.client.HoverHintOverlay;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlock;

import java.util.OptionalInt;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Action-bar hint resolver for the mahjong table. Registered via {@link HoverHintOverlay}. */
public final class MahjongTableHoverHintClient {

    private static final String K_CENTER = "riichi_mahjong.hint.table.center";
    private static final String K_EDGE_OPEN_EMPTY = "riichi_mahjong.hint.table.edge.open_empty";
    private static final String K_EDGE_OPEN_SELF = "riichi_mahjong.hint.table.edge.open_self";
    private static final String K_EDGE_OPEN_OTHER = "riichi_mahjong.hint.table.edge.open_other";
    private static final String K_EDGE_CLOSED = "riichi_mahjong.hint.table.edge.closed";

    private MahjongTableHoverHintClient() {}

    public static void register() {
        HoverHintOverlay.register(MahjongTableHoverHintClient::resolve);
    }

    @Nullable
    private static String resolve(Minecraft mc) {
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockState hitState = mc.level.getBlockState(bhr.getBlockPos());
        if (!(hitState.getBlock() instanceof MahjongTableBlock)) {
            return null;
        }
        BlockPos masterPos = BaseMultipartBlock.masterPos(bhr.getBlockPos(), hitState);
        BlockEntity be = mc.level.getBlockEntity(masterPos);
        if (!(be instanceof MahjongTableBlockEntity table)) {
            return null;
        }
        if (table.state() == MahjongTableBlockEntity.State.GAME) {
            return null;
        }
        if (BaseMultipartBlock.isMaster(hitState)) {
            return K_CENTER;
        }

        int partX = hitState.getValue(BaseMultipartBlock.PART_X);
        int partZ = hitState.getValue(BaseMultipartBlock.PART_Z);
        OptionalInt seatOpt = MahjongTableBlock.seatForCell(
                partX, partZ, hitState.getValue(BaseMultipartBlock.FACING));
        if (seatOpt.isEmpty()) return null;
        int seat = seatOpt.getAsInt();
        if (seat >= table.seats().size()) return null;
        MahjongTableBlockEntity.SeatInfo info = table.seats().get(seat);

        if (!info.enabled()) {
            return K_EDGE_CLOSED;
        }
        return info.occupant().map(uuid ->
                uuid.equals(mc.player.getUUID()) ? K_EDGE_OPEN_SELF : K_EDGE_OPEN_OTHER
        ).orElse(K_EDGE_OPEN_EMPTY);
    }
}
