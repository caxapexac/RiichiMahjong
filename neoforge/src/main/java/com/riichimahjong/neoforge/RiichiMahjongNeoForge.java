package com.riichimahjong.neoforge;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.client.RiichiMahjongClient;
import com.riichimahjong.gimmicks.EndermanCarriesMahjongTile;
import com.riichimahjong.neoforge.caps.SolitaireFluidHandler;
import com.riichimahjong.neoforge.caps.SolitaireItemHandler;
import com.riichimahjong.neoforge.caps.YakuGenEnergyHandler;
import com.riichimahjong.registry.ModBlockEntities;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@Mod(RiichiMahjong.MOD_ID)
public final class RiichiMahjongNeoForge {
    public RiichiMahjongNeoForge(IEventBus modBus) {
        RiichiMahjong.init();
        // FinalizeSpawnEvent fires on the GAME bus (NeoForge.EVENT_BUS), not the mod bus.
        // Fires once per Mob.finalizeSpawn — i.e. genuine spawns, never chunk-load.
        NeoForge.EVENT_BUS.addListener((FinalizeSpawnEvent event) ->
                EndermanCarriesMahjongTile.onFinalizeSpawn(event.getEntity()));

        // Tell Carry On (if present) not to let players pick up the mahjong table or
        // the solitaire — both have BE state that wouldn't survive Carry On's
        // pickup-and-replace flow without dupes / desyncs. NeoForge-only IMC.
        // Fabric Carry On uses a config-side blacklist; users would set that manually.
        // Solitaire BE exposes its hopper-input + Liquid XP output via NeoForge BE caps.
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(
                    Capabilities.ItemHandler.BLOCK,
                    ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),
                    (be, side) -> new SolitaireItemHandler(be));
            event.registerBlockEntity(
                    Capabilities.FluidHandler.BLOCK,
                    ModBlockEntities.MAHJONG_SOLITAIRE_BLOCK_ENTITY.get(),
                    (be, side) -> new SolitaireFluidHandler(be));
            event.registerBlockEntity(
                    Capabilities.EnergyStorage.BLOCK,
                    ModBlockEntities.YAKU_GENERATOR_BLOCK_ENTITY.get(),
                    (be, side) -> new YakuGenEnergyHandler(be));
        });

        modBus.addListener((InterModEnqueueEvent event) -> {
            for (String id : new String[]{"mahjong_table_new", "mahjong_solitaire"}) {
                String fullId = ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, id).toString();
                InterModComms.sendTo("carryon", "blacklistBlock", () -> fullId);
            }
        });

        if (FMLEnvironment.dist == Dist.CLIENT) {
            RiichiMahjongClient.initClient();
            // BERs need the BlockEntityType registry to be populated; that happens
            // after the mod constructor on NeoForge. RegisterRenderers is the
            // canonical event for this.
            modBus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                    RiichiMahjongClient.registerBlockEntityRenderers());
            // Screen factories: register directly on NeoForge's RegisterMenuScreensEvent
            // rather than via Architectury's MenuRegistry.registerScreenFactory.
            // Architectury's helper defers through a listener on its OWN mod bus's
            // RegisterMenuScreensEvent, added via EventBusesHooks.whenAvailable. If
            // we call that helper from inside our mod bus's dispatch of the same
            // event (which is the only place .get() is valid + arch's listener
            // could still fire on its bus), arch's bus may have already dispatched
            // and the listener is missed. We hit exactly that race: tables
            // registered, yaku didn't. event.register is the canonical NeoForge API
            // and we're already in a NeoForge-specific entrypoint, so going direct
            // is appropriate here.
            modBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) -> {
                event.register(com.riichimahjong.registry.ModMenus.MAHJONG_TABLE_MENU.get(),
                        com.riichimahjong.mahjongtable.client.MahjongTableScreen::new);
                event.register(com.riichimahjong.registry.ModMenus.MAHJONG_TABLE_SETTINGS_MENU.get(),
                        com.riichimahjong.mahjongtable.client.MahjongTableSettingsScreen::new);
                event.register(com.riichimahjong.registry.ModMenus.YAKU_GENERATOR_MENU.get(),
                        com.riichimahjong.yakugenerator.client.YakuGeneratorScreen::new);
            });
            // FOV smoothing — NeoForge fires ComputeFov per frame on the game bus.
            // FQN-only refs to the common client class so the import doesn't trigger
            // verification on dedicated server.
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) -> {
                if (event.getCamera().getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
                    event.setFOV(com.riichimahjong.fovnormalizer.client.NormalizeFovBlend
                            .tickAndCompute(living, event.getFOV()));
                }
            });

            // Liquid XP fluid client extensions — Architectury wrapper limitation
            // workaround. See LiquidExperienceClientExtensions for details.
            com.riichimahjong.neoforge.caps.LiquidExperienceClientExtensions.register(modBus);

            // Hide the normalize_fov marker effect icon from HUD/inventory.
            com.riichimahjong.neoforge.caps.NormalizeFovEffectClientExtensions.register(modBus);
        }
    }
}
