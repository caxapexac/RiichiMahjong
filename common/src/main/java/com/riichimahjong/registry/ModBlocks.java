package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongaltar.MahjongAltarBlock;
import com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock;
import com.riichimahjong.mahjongtable.MahjongTableBlock;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.BLOCK);

    public static final RegistrySupplier<Block> MAHJONG_TABLE = BLOCKS.register(
            "mahjong_table_new",
            () -> new MahjongTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)));

    public static final RegistrySupplier<Item> MAHJONG_TABLE_ITEM = ModItems.ITEMS.register(
            "mahjong_table_new",
            () -> new BlockItem(MAHJONG_TABLE.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> MAHJONG_SOLITAIRE = BLOCKS.register(
            "mahjong_solitaire",
            () -> new com.riichimahjong.mahjongsolitaire.MahjongSolitaireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)));

    public static final RegistrySupplier<Item> MAHJONG_SOLITAIRE_ITEM = ModItems.ITEMS.register(
            "mahjong_solitaire",
            () -> new BlockItem(MAHJONG_SOLITAIRE.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> MAHJONG_ALTAR = BLOCKS.register(
            "mahjong_altar",
            () -> new MahjongAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 5.0f)
                    .sound(SoundType.METAL)));

    public static final RegistrySupplier<Item> MAHJONG_ALTAR_ITEM = ModItems.ITEMS.register(
            "mahjong_altar",
            () -> new BlockItem(MAHJONG_ALTAR.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> YAKU_GENERATOR_TIER_1 = BLOCKS.register(
            "yaku_generator_tier_1",
            () -> new com.riichimahjong.yakugenerator.YakuGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL),
                    com.riichimahjong.yakugenerator.YakuGeneratorBlock.Tier.TIER_1));

    public static final RegistrySupplier<Block> YAKU_GENERATOR_TIER_2 = BLOCKS.register(
            "yaku_generator_tier_2",
            () -> new com.riichimahjong.yakugenerator.YakuGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL),
                    com.riichimahjong.yakugenerator.YakuGeneratorBlock.Tier.TIER_2));

    public static final RegistrySupplier<Block> YAKU_GENERATOR_TIER_3 = BLOCKS.register(
            "yaku_generator_tier_3",
            () -> new com.riichimahjong.yakugenerator.YakuGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL),
                    com.riichimahjong.yakugenerator.YakuGeneratorBlock.Tier.TIER_3));

    public static final RegistrySupplier<Item> YAKU_GENERATOR_TIER_1_ITEM = ModItems.ITEMS.register(
            "yaku_generator_tier_1",
            () -> new BlockItem(YAKU_GENERATOR_TIER_1.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> YAKU_GENERATOR_TIER_2_ITEM = ModItems.ITEMS.register(
            "yaku_generator_tier_2",
            () -> new BlockItem(YAKU_GENERATOR_TIER_2.get(), new Item.Properties()));
    public static final RegistrySupplier<Item> YAKU_GENERATOR_TIER_3_ITEM = ModItems.ITEMS.register(
            "yaku_generator_tier_3",
            () -> new BlockItem(YAKU_GENERATOR_TIER_3.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> DISCARD_CLICKER_TIER_1 = BLOCKS.register(
            "discard_clicker_tier_1",
            () -> new com.riichimahjong.clickers.DiscardClickerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL),
                    com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_1));
    public static final RegistrySupplier<Block> DISCARD_CLICKER_TIER_2 = BLOCKS.register(
            "discard_clicker_tier_2",
            () -> new com.riichimahjong.clickers.DiscardClickerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL),
                    com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_2));
    public static final RegistrySupplier<Block> DISCARD_CLICKER_TIER_3 = BLOCKS.register(
            "discard_clicker_tier_3",
            () -> new com.riichimahjong.clickers.DiscardClickerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL),
                    com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_3));

    public static final RegistrySupplier<Item> DISCARD_CLICKER_TIER_1_ITEM = ModItems.ITEMS.register(
            "discard_clicker_tier_1",
            () -> new com.riichimahjong.clickers.DiscardClickerItem(DISCARD_CLICKER_TIER_1.get(),
                    com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_1, new Item.Properties()));
    public static final RegistrySupplier<Item> DISCARD_CLICKER_TIER_2_ITEM = ModItems.ITEMS.register(
            "discard_clicker_tier_2",
            () -> new com.riichimahjong.clickers.DiscardClickerItem(DISCARD_CLICKER_TIER_2.get(),
                    com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_2, new Item.Properties()));
    public static final RegistrySupplier<Item> DISCARD_CLICKER_TIER_3_ITEM = ModItems.ITEMS.register(
            "discard_clicker_tier_3",
            () -> new com.riichimahjong.clickers.DiscardClickerItem(DISCARD_CLICKER_TIER_3.get(),
                    com.riichimahjong.clickers.DiscardClickerBlock.Tier.TIER_3, new Item.Properties()));

    public static final RegistrySupplier<Block> TSUMO_CLICKER = BLOCKS.register(
            "tsumo_clicker",
            () -> new com.riichimahjong.clickers.TsumoClickerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0f, 5.0f).sound(SoundType.METAL)));
    public static final RegistrySupplier<Item> TSUMO_CLICKER_ITEM = ModItems.ITEMS.register(
            "tsumo_clicker",
            () -> new com.riichimahjong.clickers.TsumoClickerItem(TSUMO_CLICKER.get(), new Item.Properties()));

    public static final RegistrySupplier<Block> FOV_NORMALIZER = BLOCKS.register(
            "fov_normalizer",
            () -> new com.riichimahjong.fovnormalizer.FovNormalizerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.5f, 6.0f).sound(SoundType.METAL)));

    public static final RegistrySupplier<Item> FOV_NORMALIZER_ITEM = ModItems.ITEMS.register(
            "fov_normalizer",
            () -> new BlockItem(FOV_NORMALIZER.get(), new Item.Properties()));

    /**
     * In-world placeable form of the Liquid Experience fluid. Architectury's
     * {@link dev.architectury.core.fluid.ArchitecturyFlowingFluid} attributes reference
     * this back via the fluid registration in {@link ModFluids}.
     */
    public static final RegistrySupplier<net.minecraft.world.level.block.LiquidBlock> LIQUID_EXPERIENCE_BLOCK =
            BLOCKS.register("liquid_experience_block",
                    () -> new net.minecraft.world.level.block.LiquidBlock(
                            (net.minecraft.world.level.material.FlowingFluid)
                                    com.riichimahjong.registry.ModFluids.LIQUID_EXPERIENCE.get(),
                            BlockBehaviour.Properties.of()
                                    .mapColor(MapColor.GLOW_LICHEN)
                                    .noCollission()
                                    .replaceable()
                                    .strength(100f)
                                    .noLootTable()
                                    .liquid()
                                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    private ModBlocks() {}
}
