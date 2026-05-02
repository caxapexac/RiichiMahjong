package com.riichimahjongforge.client;

import com.riichimahjongforge.MahjongTileItems;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.network.S2CMatchLifecyclePacket;
import java.util.ArrayList;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = RiichiMahjongForgeMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
@SuppressWarnings("removal")
public final class MahjongForgeClient {

    private MahjongForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(
                    RiichiMahjongForgeMod.MAHJONG_TABLE_SETTINGS.get(), MahjongTableSettingsScreen::new);
            MenuScreens.register(
                    RiichiMahjongForgeMod.MAHJONG_TABLE_INVENTORY_MENU.get(), MahjongTableInventoryScreen::new);
            MenuScreens.register(
                    RiichiMahjongForgeMod.YAKU_GENERATOR_MENU.get(), YakuGeneratorScreen::new);
            S2CMatchLifecyclePacket.registerClientHandler(MatchLifecycleClientHandler::handle);
            ArrayList<Block> tileBlocks = new ArrayList<>(MahjongTileItems.allTileBlocks());
            for (Block block : tileBlocks) {
                ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                RiichiMahjongForgeMod.MAHJONG_TABLE_BLOCK_ENTITY.get(), MahjongTableBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                RiichiMahjongForgeMod.MAHJONG_ALTAR_BLOCK_ENTITY.get(), MahjongAltarBlockEntityRenderer::new);
        //noinspection unchecked
        event.registerEntityRenderer(
                RiichiMahjongForgeMod.RIICHI_STICK_ENTITY.get(),
                (net.minecraft.client.renderer.entity.EntityRendererProvider) ThrownItemRenderer::new);
    }
}
