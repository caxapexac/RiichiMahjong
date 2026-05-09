package com.riichimahjong.fabric.client;

import com.riichimahjong.client.RiichiMahjongClient;
import net.fabricmc.api.ClientModInitializer;

public final class RiichiMahjongFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RiichiMahjongClient.initClient();
        RiichiMahjongClient.registerBlockEntityRenderers();
        RiichiMahjongClient.registerScreens();
    }
}
