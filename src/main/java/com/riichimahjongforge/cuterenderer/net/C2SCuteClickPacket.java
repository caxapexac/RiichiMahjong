package com.riichimahjongforge.cuterenderer.net;

import com.mojang.logging.LogUtils;
import com.riichimahjongforge.cuterenderer.CuteClickHandler;
import com.riichimahjongforge.cuterenderer.InteractKey;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

/**
 * Client → server: "the local player just clicked an interactive node".
 *
 * <p>This handler validates only transport-level concerns: sender dimension
 * matches the packet, and the BE at {@code masterPos} implements
 * {@link CuteClickHandler}. <b>Game-state validation — including "is the player
 * close enough / allowed to act on this?" — is the BE's responsibility</b>;
 * see {@link CuteClickHandler}'s javadoc. Stale clicks across the hover→click
 * latency window are normal and must be tolerated by the handler.
 */
public record C2SCuteClickPacket(
        ResourceKey<Level> dim,
        BlockPos masterPos,
        InteractKey key) {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void encode(C2SCuteClickPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dim.location());
        buf.writeBlockPos(msg.masterPos);
        InteractKey.write(buf, msg.key);
    }

    public static C2SCuteClickPacket decode(FriendlyByteBuf buf) {
        ResourceLocation dimId = buf.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimId);
        BlockPos pos = buf.readBlockPos();
        InteractKey key = InteractKey.read(buf);
        return new C2SCuteClickPacket(dim, pos, key);
    }

    public static void handle(C2SCuteClickPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null) return;
            ServerLevel level = sender.serverLevel();
            if (!level.dimension().equals(msg.dim)) return;
            BlockEntity be = level.getBlockEntity(msg.masterPos);
            if (be instanceof CuteClickHandler handler) {
                handler.onCuteClick(sender, msg.key);
            } else {
                LOGGER.debug("Cute click target at {} is not a CuteClickHandler", msg.masterPos);
            }
        });
        ctx.setPacketHandled(true);
    }
}
