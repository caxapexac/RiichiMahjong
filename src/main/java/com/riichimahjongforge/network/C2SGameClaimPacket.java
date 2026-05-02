package com.riichimahjongforge.network;

import com.mahjongcore.MahjongGameState;
import com.mojang.logging.LogUtils;
import com.mahjongcore.rules.ClaimWindowRules;
import com.riichimahjongforge.MahjongTableBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

/** Client claim intent during {@link MahjongGameState.TurnPhase#CLAIM_WINDOW}. */
public record C2SGameClaimPacket(
        ResourceKey<Level> tableDimension,
        BlockPos tablePos,
        int protocolVersion,
        byte actionOrdinal,
        int chiTileA,
        int chiTileB) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final byte ACT_PASS = 0;
    public static final byte ACT_RON = 1;
    public static final byte ACT_PON = 2;
    public static final byte ACT_CHI = 3;
    public static final byte ACT_DAIMIN_KAN = 4;
    public static final byte ACT_CHANKAN = 5;

    public static void encode(C2SGameClaimPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.tableDimension.location());
        buf.writeBlockPos(msg.tablePos);
        buf.writeVarInt(msg.protocolVersion);
        buf.writeByte(msg.actionOrdinal);
        buf.writeVarInt(msg.chiTileA);
        buf.writeVarInt(msg.chiTileB);
    }

    public static C2SGameClaimPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimId = buf.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos pos = buf.readBlockPos();
        int ver = buf.readVarInt();
        byte act = buf.readByte();
        int a = buf.readVarInt();
        int b = buf.readVarInt();
        return new C2SGameClaimPacket(dim, pos, ver, act, a, b);
    }

    public static void handle(C2SGameClaimPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) {
                return;
            }
            if (msg.protocolVersion != MahjongNetwork.PROTOCOL_VERSION) {
                LOGGER.debug("Ignoring claim: protocol mismatch");
                return;
            }
            ClaimWindowRules.ClaimIntent intent = decodeIntent(msg.actionOrdinal);
            if (intent == null) {
                LOGGER.debug("Ignoring claim: bad action {}", msg.actionOrdinal);
                return;
            }
            ServerLevel level = sender.serverLevel();
            if (!level.dimension().equals(msg.tableDimension)) {
                return;
            }
            if (!(level.getBlockEntity(msg.tablePos) instanceof MahjongTableBlockEntity table)) {
                return;
            }
            if (sender.distanceToSqr(
                            msg.tablePos.getX() + 0.5, msg.tablePos.getY() + 0.5, msg.tablePos.getZ() + 0.5)
                    > 8.0 * 8.0) {
                return;
            }
            table.handleGameClaim(sender, intent, msg.chiTileA, msg.chiTileB);
        });
        ctx.setPacketHandled(true);
    }

    private static ClaimWindowRules.ClaimIntent decodeIntent(byte b) {
        return switch (b) {
            case ACT_PASS -> ClaimWindowRules.ClaimIntent.PASS;
            case ACT_RON -> ClaimWindowRules.ClaimIntent.RON;
            case ACT_PON -> ClaimWindowRules.ClaimIntent.PON;
            case ACT_CHI -> ClaimWindowRules.ClaimIntent.CHI;
            case ACT_DAIMIN_KAN -> ClaimWindowRules.ClaimIntent.DAIMIN_KAN;
            case ACT_CHANKAN -> ClaimWindowRules.ClaimIntent.CHANKAN;
            default -> null;
        };
    }
}
