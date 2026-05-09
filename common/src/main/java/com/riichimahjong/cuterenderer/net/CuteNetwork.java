package com.riichimahjong.cuterenderer.net;

import com.mojang.logging.LogUtils;
import com.riichimahjong.cuterenderer.CuteClickHandler;
import dev.architectury.networking.NetworkManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

/**
 * Cute renderer's client→server packet channel. Carries {@link C2SCuteClickPacket}.
 *
 * <p>Replaces the 1.20.1 Forge SimpleChannel registration with Architectury's
 * {@link NetworkManager#registerReceiver} on top of vanilla 1.21's
 * {@code CustomPacketPayload} system. Call {@link #register()} once from common init.
 */
public final class CuteNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CuteNetwork() {}

    public static void register() {
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                C2SCuteClickPacket.TYPE,
                C2SCuteClickPacket.STREAM_CODEC,
                (msg, context) -> {
                    if (!(context.getPlayer() instanceof ServerPlayer sender)) return;
                    context.queue(() -> {
                        ServerLevel level = sender.serverLevel();
                        if (!level.dimension().equals(msg.dim())) return;
                        BlockEntity be = level.getBlockEntity(msg.masterPos());
                        if (be instanceof CuteClickHandler handler) {
                            handler.onCuteClick(sender, msg.key());
                        } else {
                            LOGGER.debug("Cute click target at {} is not a CuteClickHandler", msg.masterPos());
                        }
                    });
                });
    }
}
