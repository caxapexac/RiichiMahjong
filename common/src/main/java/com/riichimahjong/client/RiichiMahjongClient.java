package com.riichimahjong.client;

import com.riichimahjong.common.client.HoverHintOverlay;
import com.riichimahjong.cuterenderer.CuteClickInputHandler;
import com.riichimahjong.cuterenderer.editor.CuteEditorKeyHandler;
import com.riichimahjong.mahjongaltar.client.MahjongAltarBlockEntityRenderer;
import com.riichimahjong.mahjongsolitaire.client.MahjongSolitaireHoverHintClient;
import com.riichimahjong.mahjongsolitaire.client.MahjongSolitaireRenderer;
import com.riichimahjong.mahjongtable.client.MahjongTableHoverHintClient;
import com.riichimahjong.mahjongtable.client.MahjongTableRenderer;
import com.riichimahjong.mahjongtable.client.MahjongTableScreen;
import com.riichimahjong.mahjongtable.client.MahjongTableSettingsScreen;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModEntities;
import com.riichimahjong.registry.ModItems;
import com.riichimahjong.registry.ModMenus;
import dev.architectury.registry.client.rendering.ColorHandlerRegistry;
import dev.architectury.registry.client.rendering.BlockEntityRendererRegistry;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Cross-loader client-side init. Called from each loader's client entrypoint
 * ({@code RiichiMahjongFabricClient.onInitializeClient}, NeoForge's client setup).
 *
 * <p>Architectury Loom strips this and its callers from the dedicated server jar
 * via the {@code @Environment} annotation.
 */
@Environment(EnvType.CLIENT)
public final class RiichiMahjongClient {

    private RiichiMahjongClient() {}

    public static void initClient() {
        CuteClickInputHandler.register();
        CuteEditorKeyHandler.register();
        HoverHintOverlay.registerTickHandler();
        MahjongTableHoverHintClient.register();
        MahjongSolitaireHoverHintClient.register();

        // Liquid XP bucket: re-uses vanilla water_bucket model (blue appearance).
        // 1.21 vanilla bucket sprites are single-baked (no separate "_overlay" layer),
        // so a custom green-tinted bucket needs a custom PNG. Punted for now — the
        // bucket is functional, just not on-brand.
    }

    /**
     * BlockEntityRenderer registrations. Must run after the BlockEntityType registry
     * is populated — call from each loader's appropriate lifecycle event (Fabric:
     * directly from ClientModInitializer; NeoForge: from {@code EntityRenderersEvent.RegisterRenderers}).
     */
    public static void registerBlockEntityRenderers() {
        BlockEntityRendererRegistry.register(
                ModBlockEntities.MAHJONG_TABLE_BLOCK_ENTITY.get(),
                MahjongTableRenderer::new);
        BlockEntityRendererRegistry.register(
                ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),
                MahjongSolitaireRenderer::new);
        BlockEntityRendererRegistry.register(
                ModBlockEntities.MAHJONG_ALTAR_BLOCK_ENTITY.get(),
                MahjongAltarBlockEntityRenderer::new);

        // Entity renderers share the BER lifecycle (NeoForge fires the same RegisterRenderers
        // event for both). The riichi stick uses vanilla ThrownItemRenderer (renders the item
        // sprite as a billboard), same as snowballs.
        EntityRendererRegistry.register(
                ModEntities.MAHJONG_RIICHI_STICK_ENTITY,
                ThrownItemRenderer::new);
    }

    /**
     * Menu → Screen factory registrations. Same lifecycle constraint as BERs (MenuType
     * registry must be populated). Call from each loader's appropriate lifecycle hook.
     */
    public static void registerScreens() {
        MenuRegistry.registerScreenFactory(ModMenus.MAHJONG_TABLE_MENU.get(), MahjongTableScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.MAHJONG_TABLE_SETTINGS_MENU.get(), MahjongTableSettingsScreen::new);
        MenuRegistry.registerScreenFactory(ModMenus.YAKU_GENERATOR_MENU.get(),
                com.riichimahjong.yakugenerator.client.YakuGeneratorScreen::new);
    }
}
