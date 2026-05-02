package com.riichimahjongforge;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import com.riichimahjongforge.menu.MahjongTableSettingsMenu;
import com.riichimahjongforge.menu.MahjongTableInventoryMenu;
import com.riichimahjongforge.menu.YakuGeneratorMenu;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(RiichiMahjongForgeMod.MODID)
public class RiichiMahjongForgeMod {
    public static final String MODID = "riichi_mahjong_forge";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);

    public static final RegistryObject<Block> MAHJONG_TABLE = BLOCKS.register(
            "mahjong_table",
            () -> new MahjongTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)));

    public static final RegistryObject<Item> MAHJONG_TABLE_ITEM = ITEMS.register(
            "mahjong_table",
            () -> new BlockItem(MAHJONG_TABLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> FILLED_MAHJONG_TABLE_ITEM = ITEMS.register(
            "filled_mahjong_table",
            () -> new FilledMahjongTableItem(MAHJONG_TABLE.get(), new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Block> MAHJONG_ALTAR = BLOCKS.register(
            "mahjong_altar",
            () -> new MahjongAltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 5.0f)
                    .sound(SoundType.METAL)));

    public static final RegistryObject<Item> MAHJONG_ALTAR_ITEM = ITEMS.register(
            "mahjong_altar",
            () -> new BlockItem(MAHJONG_ALTAR.get(), new Item.Properties()));

    public static final RegistryObject<Block> YAKU_GENERATOR_TIER_1 = BLOCKS.register(
            "yaku_generator_tier_1",
            () -> new YakuGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL), YakuGeneratorBlock.Tier.TIER_1));
    public static final RegistryObject<Block> YAKU_GENERATOR_TIER_2 = BLOCKS.register(
            "yaku_generator_tier_2",
            () -> new YakuGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL), YakuGeneratorBlock.Tier.TIER_2));
    public static final RegistryObject<Block> YAKU_GENERATOR_TIER_3 = BLOCKS.register(
            "yaku_generator_tier_3",
            () -> new YakuGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL), YakuGeneratorBlock.Tier.TIER_3));

    public static final RegistryObject<Item> YAKU_GENERATOR_TIER_1_ITEM = ITEMS.register(
            "yaku_generator_tier_1",
            () -> new BlockItem(YAKU_GENERATOR_TIER_1.get(), new Item.Properties()));
    public static final RegistryObject<Item> YAKU_GENERATOR_TIER_2_ITEM = ITEMS.register(
            "yaku_generator_tier_2",
            () -> new BlockItem(YAKU_GENERATOR_TIER_2.get(), new Item.Properties()));
    public static final RegistryObject<Item> YAKU_GENERATOR_TIER_3_ITEM = ITEMS.register(
            "yaku_generator_tier_3",
            () -> new BlockItem(YAKU_GENERATOR_TIER_3.get(), new Item.Properties()));

    public static final RegistryObject<Item> MAHJONG_WRENCH =
            ITEMS.register("mahjong_wrench", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MAHJONG_RED_DRAGON_SOUL =
            ITEMS.register("mahjong_red_dragon_soul", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<EntityType<RiichiStickEntity>> RIICHI_STICK_ENTITY =
            ENTITY_TYPES.register(
                    "riichi_stick",
                    () -> EntityType.Builder.<RiichiStickEntity>of(RiichiStickEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("riichi_stick"));

    public static final RegistryObject<Item> MAHJONG_RIICHI_STICK =
            ITEMS.register("mahjong_riichi_stick", () -> new MahjongRiichiStickItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> MAHJONG_TABLE_RECORD =
            ITEMS.register("mahjong_table_record", () -> new MahjongTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_CHUUREN_TABLE_RECORD =
            ITEMS.register(
                    "predefined_chuuren_table_record",
                    () -> new PredefinedChuurenpoutouTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_KOKUSHIMUSOU_TABLE_RECORD =
            ITEMS.register(
                    "predefined_kokushimusou_table_record",
                    () -> new PredefinedKokushimusouTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_SUUANKOU_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_suuankou_tsumo_table_record",
                    () -> new PredefinedSuuankouTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_KAZOE_STYLE_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_kazoe_style_tsumo_table_record",
                    () -> new PredefinedKazoeYakumanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_TRIPLE_KAN_OPTIONS_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_triple_kan_options_tsumo_table_record",
                    () -> new PredefinedTripleKanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_KAKAN_OPTIONS_TABLE_RECORD =
            ITEMS.register(
                    "predefined_kakan_options_table_record",
                    () -> new PredefinedKakanOptionsTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_RINSHAN_TABLE_RECORD =
            ITEMS.register(
                    "predefined_rinshan_table_record",
                    () -> new PredefinedRinshanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_CHANKAN_TABLE_RECORD =
            ITEMS.register(
                    "predefined_chankan_table_record",
                    () -> new PredefinedChankanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<MahjongTableBlockEntity>> MAHJONG_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_table",
                    () -> BlockEntityType.Builder.of(MahjongTableBlockEntity::new, MAHJONG_TABLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<YakuGeneratorBlockEntity>> YAKU_GENERATOR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "yaku_generator",
                    () -> BlockEntityType.Builder.of(
                            YakuGeneratorBlockEntity::new,
                            YAKU_GENERATOR_TIER_1.get(),
                            YAKU_GENERATOR_TIER_2.get(),
                            YAKU_GENERATOR_TIER_3.get()).build(null));
    public static final RegistryObject<BlockEntityType<MahjongAltarBlockEntity>> MAHJONG_ALTAR_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_altar",
                    () -> BlockEntityType.Builder.of(MahjongAltarBlockEntity::new, MAHJONG_ALTAR.get()).build(null));

    public static final RegistryObject<RecipeSerializer<MahjongAltarRecipe>> MAHJONG_ALTAR_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("mahjong_altar", MahjongAltarRecipe.Serializer::new);

    public static final RegistryObject<SoundEvent> TILE_PLACE_SOUND =
            SOUND_EVENTS.register(
                    "tile.place",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(MODID, "tile.place")));
    public static final RegistryObject<SoundEvent> TILE_HOVER_TILE_SOUND =
            SOUND_EVENTS.register(
                    "tile.hover.tile",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(MODID, "tile.hover.tile")));
    public static final RegistryObject<SoundEvent> TILE_HOVER_ACTION_SOUND =
            SOUND_EVENTS.register(
                    "tile.hover.action",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(MODID, "tile.hover.action")));
    public static final RegistryObject<MobEffect> NORMALIZE_FOV_EFFECT =
            MOB_EFFECTS.register("normalize_fov", NormalizeFovMobEffect::new);

    public static final RegistryObject<MenuType<MahjongTableSettingsMenu>> MAHJONG_TABLE_SETTINGS =
            MENU_TYPES.register(
                    "mahjong_table_settings",
                    () -> IForgeMenuType.create(MahjongTableSettingsMenu::fromNetwork));
    public static final RegistryObject<MenuType<MahjongTableInventoryMenu>> MAHJONG_TABLE_INVENTORY_MENU =
            MENU_TYPES.register(
                    "mahjong_table_inventory",
                    () -> IForgeMenuType.create(MahjongTableInventoryMenu::fromNetwork));
    public static final RegistryObject<MenuType<YakuGeneratorMenu>> YAKU_GENERATOR_MENU =
            MENU_TYPES.register(
                    "yaku_generator",
                    () -> IForgeMenuType.create(YakuGeneratorMenu::fromNetwork));
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.riichi_mahjong_forge"))
                    .icon(() -> new ItemStack(MAHJONG_TABLE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(MAHJONG_TABLE_ITEM.get());
                        output.accept(FILLED_MAHJONG_TABLE_ITEM.get());
                        output.accept(MAHJONG_ALTAR_ITEM.get());
                        output.accept(YAKU_GENERATOR_TIER_1_ITEM.get());
                        output.accept(YAKU_GENERATOR_TIER_2_ITEM.get());
                        output.accept(YAKU_GENERATOR_TIER_3_ITEM.get());
                        output.accept(MAHJONG_WRENCH.get());
                        output.accept(MAHJONG_RED_DRAGON_SOUL.get());
                        output.accept(MAHJONG_RIICHI_STICK.get());
                        output.accept(MAHJONG_TABLE_RECORD.get());
                        output.accept(PREDEFINED_CHUUREN_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_KOKUSHIMUSOU_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_SUUANKOU_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_KAZOE_STYLE_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_TRIPLE_KAN_OPTIONS_TSUMO_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_KAKAN_OPTIONS_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_RINSHAN_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_CHANKAN_TABLE_RECORD.get().getDefaultInstance());
                        for (Item item : MahjongTileItems.allTileItems()) {
                            output.accept(item);
                        }
                    })
                    .build());

    static {
        MahjongTileItems.register(BLOCKS, ITEMS);
    }

    public RiichiMahjongForgeMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        MOB_EFFECTS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        SOUND_EVENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        modBus.addListener(this::onInterModEnqueue);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onInterModEnqueue(InterModEnqueueEvent event) {
        String mahjongTableId = ResourceLocation.fromNamespaceAndPath(MODID, "mahjong_table").toString();
        InterModComms.sendTo("carryon", "blacklistBlock", () -> mahjongTableId);
        LOGGER.info("Sent Carry On IMC blacklist for block {}", mahjongTableId);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Riichi Mahjong Forge server starting");
    }
}
