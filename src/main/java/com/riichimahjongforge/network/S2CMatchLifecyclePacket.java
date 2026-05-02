package com.riichimahjongforge.network;

import com.riichimahjongforge.MatchAbortReason;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server → client: match started (stub) or match aborted back to lobby. Indices 0–31 table/lobby band.
 */
public record S2CMatchLifecyclePacket(
        ResourceKey<Level> tableDimension,
        BlockPos tablePos,
        boolean started,
        MatchAbortReason abortReason,
        int roundPlaceholder) {

    private static volatile Consumer<S2CMatchLifecyclePacket> clientHandler;

    /** Called from client mod setup only; avoids loading client classes on dedicated server. */
    public static void registerClientHandler(Consumer<S2CMatchLifecyclePacket> handler) {
        clientHandler = handler;
    }

    public static void encode(S2CMatchLifecyclePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.tableDimension.location());
        buf.writeBlockPos(msg.tablePos);
        buf.writeBoolean(msg.started);
        buf.writeEnum(msg.abortReason);
        buf.writeVarInt(msg.roundPlaceholder);
    }

    public static S2CMatchLifecyclePacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimId = buf.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos pos = buf.readBlockPos();
        boolean started = buf.readBoolean();
        MatchAbortReason reason = buf.readEnum(MatchAbortReason.class);
        int round = buf.readVarInt();
        return new S2CMatchLifecyclePacket(dim, pos, started, reason, round);
    }

    public static void handle(S2CMatchLifecyclePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            Consumer<S2CMatchLifecyclePacket> h = clientHandler;
            if (h != null) {
                h.accept(msg);
            }
        });
        ctx.setPacketHandled(true);
    }
}
