package com.riichimahjong.registry;

import com.riichimahjong.RiichiMahjong;
import com.riichimahjong.mahjongcore.MahjongTileItems;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(RiichiMahjong.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + RiichiMahjong.MOD_ID))
                    .icon(() -> new ItemStack(ModBlocks.MAHJONG_TABLE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModBlocks.MAHJONG_TABLE_ITEM.get());
                        output.accept(ModBlocks.MAHJONG_SOLITAIRE_ITEM.get());
                        output.accept(ModBlocks.MAHJONG_ALTAR_ITEM.get());
                        output.accept(ModBlocks.YAKU_GENERATOR_TIER_1_ITEM.get());
                        output.accept(ModBlocks.YAKU_GENERATOR_TIER_2_ITEM.get());
                        output.accept(ModBlocks.YAKU_GENERATOR_TIER_3_ITEM.get());
                        output.accept(ModBlocks.DISCARD_CLICKER_TIER_1_ITEM.get());
                        output.accept(ModBlocks.DISCARD_CLICKER_TIER_2_ITEM.get());
                        output.accept(ModBlocks.DISCARD_CLICKER_TIER_3_ITEM.get());
                        output.accept(ModBlocks.TSUMO_CLICKER_ITEM.get());
                        output.accept(ModBlocks.FOV_NORMALIZER_ITEM.get());
                        output.accept(ModItems.LIQUID_EXPERIENCE_BUCKET.get());
                        output.accept(ModItems.MAHJONG_WRENCH.get());
                        output.accept(ModItems.MAHJONG_STAR.get());
                        output.accept(ModItems.MAHJONG_FAKE_STAR.get());
                        output.accept(ModItems.AKA_MANBOU_PAINTING_ITEM.get());
                        output.accept(ModItems.ZUNDAMON_HAPPY_PAINTING_ITEM.get());
                        output.accept(ModItems.MAHJONG_RED_DRAGON_SOUL.get());
                        output.accept(ModItems.MAHJONG_RIICHI_STICK.get());
                        output.accept(ModItems.MAHJONG_TABLE_RECORD.get());
                        output.accept(ModItems.PREDEFINED_CHUUREN_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_KOKUSHIMUSOU_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_SUUANKOU_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_KAZOE_STYLE_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_TRIPLE_KAN_OPTIONS_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_KAKAN_OPTIONS_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_RINSHAN_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_CHANKAN_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_TENHOU_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(ModItems.PREDEFINED_PINFU_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        for (Item item : MahjongTileItems.allTileItems()) {
                            output.accept(item);
                        }
                    })
                    .build());

    private ModCreativeTabs() {}
}
