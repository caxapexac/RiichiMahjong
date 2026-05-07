package com.riichimahjongforge.cuterenderer.net;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;

/** Cute renderer's client→server packet channel. Carries {@link C2SCuteClickPacket}. */
@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CuteNetwork {

    // No per-packet or channel-level protocol version: Forge already rejects
    // mismatched mod versions at handshake, so any peer that gets this far has
    // identical packet formats by construction.
    private static final String PROTOCOL = "1";

    private static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(RiichiMahjongForgeMod.MODID, "cute");

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.ChannelBuilder.named(CHANNEL_NAME)
                    .networkProtocolVersion(() -> PROTOCOL)
                    .clientAcceptedVersions(PROTOCOL::equals)
                    .serverAcceptedVersions(PROTOCOL::equals)
                    .simpleChannel();

    private static int nextId;

    private CuteNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(
                    nextId++,
                    C2SCuteClickPacket.class,
                    C2SCuteClickPacket::encode,
                    C2SCuteClickPacket::decode,
                    C2SCuteClickPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
        });
    }
}
