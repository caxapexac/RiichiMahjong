package com.riichimahjongforge.client;

import com.riichimahjongforge.mahjongcore.MahjongTileItems;
import com.riichimahjongforge.RiichiMahjongForgeMod;
import com.riichimahjongforge.mahjongtools.MahjongRiichiStickEntity;
import com.riichimahjongforge.yakugenerator.client.YakuGeneratorScreen;
import com.riichimahjongforge.mahjongaltar.client.MahjongAltarBlockEntityRenderer;
import com.riichimahjongforge.mahjongsolitaire.client.MahjongSolitaireHoverHintClient;
import com.riichimahjongforge.mahjongsolitaire.client.MahjongSolitaireRenderer;
import com.riichimahjongforge.mahjongtable.client.MahjongTableHoverHintClient;
import com.riichimahjongforge.mahjongtable.client.MahjongTableRenderer;
import com.riichimahjongforge.mahjongtable.client.MahjongTableScreen;
import com.riichimahjongforge.mahjongtable.client.MahjongTableSettingsScreen;
import java.util.ArrayList;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
                    RiichiMahjongForgeMod.MAHJONG_TABLE_MENU.get(), MahjongTableScreen::new);
            MenuScreens.register(
                    RiichiMahjongForgeMod.MAHJONG_TABLE_SETTINGS_MENU.get(), MahjongTableSettingsScreen::new);
            MahjongTableHoverHintClient.register();
            MahjongSolitaireHoverHintClient.register();
            MenuScreens.register(
                    RiichiMahjongForgeMod.YAKU_GENERATOR_MENU.get(), YakuGeneratorScreen::new);
            ArrayList<Block> tileBlocks = new ArrayList<>(MahjongTileItems.allTileBlocks());
            for (Block block : tileBlocks) {
                ItemBlockRenderTypes.setRenderLayer(block, RenderType.cutout());
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                RiichiMahjongForgeMod.MAHJONG_TABLE_BLOCK_ENTITY.get(), MahjongTableRenderer::new);
        event.registerBlockEntityRenderer(
                RiichiMahjongForgeMod.MAHJONG_ALTAR_BLOCK_ENTITY.get(), MahjongAltarBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                RiichiMahjongForgeMod.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(), MahjongSolitaireRenderer::new);
        event.registerEntityRenderer(
                RiichiMahjongForgeMod.MAHJONG_RIICHI_STICK_ENTITY.get(),
                (EntityRendererProvider<MahjongRiichiStickEntity>) ThrownItemRenderer::new);
    }
}
