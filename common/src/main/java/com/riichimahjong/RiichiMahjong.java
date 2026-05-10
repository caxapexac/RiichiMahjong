package com.riichimahjong;

import com.riichimahjong.cuterenderer.net.CuteNetwork;
import com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import com.riichimahjong.registry.ModBlockEntities;
import com.riichimahjong.registry.ModFluids;
import com.riichimahjong.registry.ModRecipeTypes;
import com.riichimahjong.registry.ModBlocks;
import com.riichimahjong.registry.ModCreativeTabs;
import com.riichimahjong.registry.ModEntities;
import com.riichimahjong.registry.ModItems;
import com.riichimahjong.registry.ModMenus;
import com.riichimahjong.registry.ModMobEffects;
import com.riichimahjong.registry.ModRecipeSerializers;
import com.riichimahjong.registry.ModSounds;

public final class RiichiMahjong {
    public static final String MOD_ID = "riichi_mahjong";

    public static void init() {
        MahjongTileItems.register(ModBlocks.BLOCKS, ModItems.ITEMS);

        ModFluids.FLUIDS.register();
        ModBlocks.BLOCKS.register();
        ModItems.ITEMS.register();
        ModBlockEntities.BLOCK_ENTITY_TYPES.register();
        ModMenus.MENU_TYPES.register();
        ModSounds.SOUND_EVENTS.register();
        ModMobEffects.MOB_EFFECTS.register();
        ModEntities.ENTITY_TYPES.register();
        // Painting variants are datapack-driven in 1.21+ (data/<modid>/painting_variant/*.json),
        // no runtime registry. Defined as datapack JSON in a later phase.
        ModRecipeSerializers.RECIPE_SERIALIZERS.register();
        ModRecipeTypes.RECIPE_TYPES.register();
        ModCreativeTabs.CREATIVE_MODE_TABS.register();

        CuteNetwork.register();
        MahjongTableBlock.registerEvents();
        MahjongAltarBlockEntity.registerEvents();
        // Sou tiles burn as fuel (1-sou = 1 coal worth, scaling linearly).
        // Architectury FuelRegistry fans out to both NeoForge and Fabric.
        com.riichimahjong.mahjongcore.MahjongTileFuels.register();
        // EndermanCarriesMahjongTile is wired per-loader (NeoForge: FinalizeSpawnEvent
        // listener; Fabric: Mob.finalizeSpawn mixin) — no common-bus registration here.
    }
}
