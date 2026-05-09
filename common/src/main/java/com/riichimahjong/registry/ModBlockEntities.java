package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjong.mahjongtable.MahjongTableBlockEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.BLOCK_ENTITY_TYPE);

    public static final RegistrySupplier<BlockEntityType<MahjongTableBlockEntity>> MAHJONG_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_table_new",
                    () -> BlockEntityType.Builder.of(
                            MahjongTableBlockEntity::new, ModBlocks.MAHJONG_TABLE.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<MahjongSolitaireBlockEntity>> MAHJONG_SOLITAIRE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_solitaire",
                    () -> BlockEntityType.Builder.of(
                            MahjongSolitaireBlockEntity::new, ModBlocks.MAHJONG_SOLITAIRE.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity>> MAHJONG_ALTAR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_altar",
                    () -> BlockEntityType.Builder.of(
                            com.riichimahjong.mahjongaltar.MahjongAltarBlockEntity::new, ModBlocks.MAHJONG_ALTAR.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity>> YAKU_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "yaku_generator",
                    () -> BlockEntityType.Builder.of(
                            com.riichimahjong.yakugenerator.YakuGeneratorBlockEntity::new,
                            ModBlocks.YAKU_GENERATOR_TIER_1.get(),
                            ModBlocks.YAKU_GENERATOR_TIER_2.get(),
                            ModBlocks.YAKU_GENERATOR_TIER_3.get()).build(null));

    public static final RegistrySupplier<BlockEntityType<com.riichimahjong.fovnormalizer.FovNormalizerBlockEntity>> FOV_NORMALIZER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "fov_normalizer",
                    () -> BlockEntityType.Builder.of(
                            com.riichimahjong.fovnormalizer.FovNormalizerBlockEntity::new,
                            ModBlocks.FOV_NORMALIZER.get()).build(null));

    private ModBlockEntities() {}
}
