package com.riichimahjongforge.network;

import com.riichimahjongforge.RiichiMahjongForgeMod;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Custom play packets. One {@link SimpleChannel} with grouped message indices (documented here):
 * <ul>
 *   <li>0–31: table / lobby (ping, match lifecycle, future seat sync, etc.)</li>
 *   <li>32–63: game intents (reserved for Phase 5+)</li>
 *   <li>64–95: auxiliary sync (reserved)</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MahjongNetwork {

    /** Bump when breaking wire format; checked in each C2S payload where relevant. */
    public static final int PROTOCOL_VERSION = 6;

    private static final String PROTOCOL = String.valueOf(PROTOCOL_VERSION);

    private static final ResourceLocation CHANNEL_NAME =
            ResourceLocation.fromNamespaceAndPath(RiichiMahjongForgeMod.MODID, "play");

    public static final SimpleChannel CHANNEL =
            NetworkRegistry.ChannelBuilder.named(CHANNEL_NAME)
                    .networkProtocolVersion(() -> PROTOCOL)
                    .clientAcceptedVersions(PROTOCOL::equals)
                    .serverAcceptedVersions(PROTOCOL::equals)
                    .simpleChannel();

    private static int nextId;

    private MahjongNetwork() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.registerMessage(
                    nextId++,
                    S2CMatchLifecyclePacket.class,
                    S2CMatchLifecyclePacket::encode,
                    S2CMatchLifecyclePacket::decode,
                    S2CMatchLifecyclePacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            CHANNEL.registerMessage(
                    nextId++,
                    C2SGameClaimPacket.class,
                    C2SGameClaimPacket::encode,
                    C2SGameClaimPacket::decode,
                    C2SGameClaimPacket::handle,
                    Optional.of(NetworkDirection.PLAY_TO_SERVER));
        });
    }
}
