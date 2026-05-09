package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.ITEM);

    /** Reward item dropped when a Fake Mahjong board is fully cleared. Free-to-play 3-star reference. */
    public static final RegistrySupplier<Item> MAHJONG_FAKE_STAR = ITEMS.register(
            "mahjong_fake_star",
            () -> new Item(new Item.Properties().stacksTo(64)));

    /** Reward item awarded to mahjong-table winners — N stars per N han
     *  (yakuman counts as 13 han, double yakuman 26, etc.). */
    public static final RegistrySupplier<Item> MAHJONG_STAR = ITEMS.register(
            "mahjong_star",
            () -> new Item(new Item.Properties().stacksTo(64)));

    /** Wrench. Tagged via {@code data/c/tags/items/tools/wrench.json}; used to open
     *  the table's inventory/settings menu. Stacks to 1. */
    public static final RegistrySupplier<Item> MAHJONG_WRENCH = ITEMS.register(
            "mahjong_wrench",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> AKA_MANBOU_PAINTING_ITEM = ITEMS.register(
            "aka_manbou_painting",
            () -> new com.riichimahjong.gimmicks.VariantPaintingItem(
                    new Item.Properties().stacksTo(64),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "aka_manbou_1"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "aka_manbou_2"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "aka_manbou_3")));

    public static final RegistrySupplier<Item> ZUNDAMON_HAPPY_PAINTING_ITEM = ITEMS.register(
            "zundamon_happy_painting",
            () -> new com.riichimahjong.gimmicks.VariantPaintingItem(
                    new Item.Properties().stacksTo(64),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(RiichiMahjong.MOD_ID, "zundamon_happy")));

    public static final RegistrySupplier<Item> MAHJONG_RED_DRAGON_SOUL = ITEMS.register(
            "mahjong_red_dragon_soul",
            () -> new Item(new Item.Properties().stacksTo(64)));

    /** Riichi stick — throwable item that spawns a {@link com.riichimahjong.mahjongtools.MahjongRiichiStickEntity}
     *  on use. The entity applies AoE knockback on hit and drops the stick back. */
    public static final RegistrySupplier<Item> MAHJONG_RIICHI_STICK = ITEMS.register(
            "mahjong_riichi_stick",
            () -> new com.riichimahjong.mahjongtools.MahjongRiichiStickItem(new Item.Properties().stacksTo(64)));

    /** Liquid Experience bucket — {@code BucketItem} bound to the source fluid. */
    public static final RegistrySupplier<Item> LIQUID_EXPERIENCE_BUCKET = ITEMS.register(
            "liquid_experience_bucket",
            () -> new net.minecraft.world.item.BucketItem(
                    com.riichimahjong.registry.ModFluids.LIQUID_EXPERIENCE.get(),
                    new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1)));

    // Generic save/load record + 11 predefined fixture records.
    public static final RegistrySupplier<Item> MAHJONG_TABLE_RECORD = ITEMS.register(
            "mahjong_table_record",
            () -> new com.riichimahjong.mahjongtable.record.MahjongTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_CHUUREN_TABLE_RECORD = ITEMS.register(
            "predefined_chuuren_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedChuurenpoutouTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_KOKUSHIMUSOU_TABLE_RECORD = ITEMS.register(
            "predefined_kokushimusou_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedKokushimusouTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_SUUANKOU_TSUMO_TABLE_RECORD = ITEMS.register(
            "predefined_suuankou_tsumo_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedSuuankouTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_KAZOE_STYLE_TSUMO_TABLE_RECORD = ITEMS.register(
            "predefined_kazoe_style_tsumo_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedKazoeYakumanTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_TRIPLE_KAN_OPTIONS_TSUMO_TABLE_RECORD = ITEMS.register(
            "predefined_triple_kan_options_tsumo_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedTripleKanTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_KAKAN_OPTIONS_TABLE_RECORD = ITEMS.register(
            "predefined_kakan_options_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedKakanOptionsTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_RINSHAN_TABLE_RECORD = ITEMS.register(
            "predefined_rinshan_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedRinshanTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_CHANKAN_TABLE_RECORD = ITEMS.register(
            "predefined_chankan_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedChankanTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_TENHOU_TABLE_RECORD = ITEMS.register(
            "predefined_tenhou_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedTenhouTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    public static final RegistrySupplier<Item> PREDEFINED_PINFU_TSUMO_TABLE_RECORD = ITEMS.register(
            "predefined_pinfu_tsumo_table_record",
            () -> new com.riichimahjong.mahjongtable.record.PredefinedPinfuTableRecordItem(
                    new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
