package com.riichimahjongforge;

import com.mojang.logging.LogUtils;
import com.riichimahjongforge.mahjongcore.MahjongTileItems;
import com.riichimahjongforge.mahjongtable.*;
import com.riichimahjongforge.mahjongtools.MahjongRiichiStickItem;
import com.riichimahjongforge.mahjongtools.MahjongRiichiStickEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.PaintingVariant;
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
import com.riichimahjongforge.yakugenerator.YakuGeneratorBlock;
import com.riichimahjongforge.yakugenerator.YakuGeneratorBlockEntity;
import com.riichimahjongforge.yakugenerator.YakuGeneratorMenu;
import com.riichimahjongforge.fovnormalizer.FovNormalizerBlock;
import com.riichimahjongforge.fovnormalizer.FovNormalizerBlockEntity;
import com.riichimahjongforge.fovnormalizer.NormalizeFovMobEffect;
import com.riichimahjongforge.mahjongaltar.MahjongAltarBlock;
import com.riichimahjongforge.mahjongaltar.MahjongAltarBlockEntity;
import com.riichimahjongforge.mahjongaltar.MahjongAltarRecipe;
import com.riichimahjongforge.mahjongsolitaire.MahjongSolitaireBlock;
import com.riichimahjongforge.mahjongsolitaire.MahjongSolitaireBlockEntity;
import com.riichimahjongforge.gimmicks.VariantPaintingItem;
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
    public static final DeferredRegister<PaintingVariant> PAINTING_VARIANTS =
            DeferredRegister.create(ForgeRegistries.Keys.PAINTING_VARIANTS, MODID);
    public static final DeferredRegister<net.minecraftforge.fluids.FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, MODID);
    public static final DeferredRegister<net.minecraft.world.level.material.Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, MODID);

    public static final RegistryObject<Block> MAHJONG_TABLE = BLOCKS.register(
            "mahjong_table_new",
            () -> new MahjongTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)));
    public static final RegistryObject<Item> MAHJONG_TABLE_ITEM = ITEMS.register(
            "mahjong_table_new",
            () -> new BlockItem(MAHJONG_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<Block> MAHJONG_SOLITAIRE = BLOCKS.register(
            "mahjong_solitaire",
            () -> new MahjongSolitaireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f, 3.0f)
                    .sound(SoundType.WOOD)));
    public static final RegistryObject<Item> MAHJONG_SOLITAIRE_ITEM = ITEMS.register(
            "mahjong_solitaire",
            () -> new BlockItem(MAHJONG_SOLITAIRE.get(), new Item.Properties()));

    /** Reward item dropped when a Fake Mahjong board is fully cleared. Free-to-play 3-star reference. */
    public static final RegistryObject<Item> MAHJONG_FAKE_STAR = ITEMS.register(
            "mahjong_fake_star",
            () -> new Item(new Item.Properties().stacksTo(64)));

    /** Reward item awarded to mahjong-table winners — N stars per N han
     *  (yakuman counts as 13 han, double yakuman 26, etc.). */
    public static final RegistryObject<Item> MAHJONG_STAR = ITEMS.register(
            "mahjong_star",
            () -> new Item(new Item.Properties().stacksTo(64)));

    // Liquid Experience: an abstract fluid (no in-world block, no bucket) used by
    // the Fake Mahjong table to expose a tank capability for automation. Tagged with
    // `forge:experience` so XP-aware mods can recognise it via the standard tag.
    public static final RegistryObject<net.minecraftforge.fluids.FluidType> LIQUID_EXPERIENCE_TYPE =
            FLUID_TYPES.register(
                    "liquid_experience",
                    () -> new net.minecraftforge.fluids.FluidType(
                            net.minecraftforge.fluids.FluidType.Properties.create()
                                    .descriptionId("fluid.riichi_mahjong_forge.liquid_experience")
                                    .canSwim(false).canDrown(false).canExtinguish(false).canHydrate(false)
                                    .density(800).viscosity(1500).lightLevel(7)) {
                        @Override
                        public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                            // Without these texture refs, anything that tries to
                            // render the fluid sprite (Jade, JEI, fluid tank UIs)
                            // crashes with NPE in TextureAtlas.getSprite. Vanilla
                            // water textures are a safe placeholder until / unless
                            // we ship a custom texture.
                            consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                                @Override public net.minecraft.resources.ResourceLocation getStillTexture() {
                                    return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            MODID, "block/liquid_experience_still");
                                }
                                @Override public net.minecraft.resources.ResourceLocation getFlowingTexture() {
                                    return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                            MODID, "block/liquid_experience_flow");
                                }
                                @Override public int getTintColor() {
                                    // ARGB multiplied per-pixel with the texture.
                                    // Vanilla water_still is largely grayscale, so
                                    // this tint is what we'll mostly see — pick a
                                    // bright lime/yellow-green to read as Liquid XP.
                                    return 0xFFC8FF40;
                                }
                            });
                        }
                    });
    public static final RegistryObject<net.minecraft.world.level.material.FlowingFluid> LIQUID_EXPERIENCE =
            FLUIDS.register(
                    "liquid_experience",
                    () -> new net.minecraftforge.fluids.ForgeFlowingFluid.Source(liquidExperienceProps()));
    public static final RegistryObject<net.minecraft.world.level.material.FlowingFluid> LIQUID_EXPERIENCE_FLOWING =
            FLUIDS.register(
                    "liquid_experience_flowing",
                    () -> new net.minecraftforge.fluids.ForgeFlowingFluid.Flowing(liquidExperienceProps()));

    /**
     * Built lazily on registration so the Source/Flowing self-references resolve
     * after this class's static fields are initialized. Constructing it inline in
     * a static block trips Java's definite-assignment check on the forward refs.
     */
    private static net.minecraftforge.fluids.ForgeFlowingFluid.Properties liquidExperienceProps() {
        return new net.minecraftforge.fluids.ForgeFlowingFluid.Properties(
                () -> LIQUID_EXPERIENCE_TYPE.get(),
                () -> LIQUID_EXPERIENCE.get(),
                () -> LIQUID_EXPERIENCE_FLOWING.get());
    }

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

    public static final RegistryObject<Block> FOV_NORMALIZER = BLOCKS.register(
            "fov_normalizer",
            () -> new FovNormalizerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 6.0f)
                    .sound(SoundType.METAL)));
    public static final RegistryObject<Item> FOV_NORMALIZER_ITEM = ITEMS.register(
            "fov_normalizer",
            () -> new BlockItem(FOV_NORMALIZER.get(), new Item.Properties()));

    public static final RegistryObject<Item> MAHJONG_WRENCH =
            ITEMS.register("mahjong_wrench", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MAHJONG_RED_DRAGON_SOUL =
            ITEMS.register("mahjong_red_dragon_soul", () -> new Item(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<EntityType<MahjongRiichiStickEntity>> MAHJONG_RIICHI_STICK_ENTITY =
            ENTITY_TYPES.register(
                    "mahjong_riichi_stick",
                    () -> EntityType.Builder.<MahjongRiichiStickEntity>of(MahjongRiichiStickEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("mahjong_riichi_stick"));
    public static final RegistryObject<PaintingVariant> AKA_MANBOU_PAINTING_1 =
            PAINTING_VARIANTS.register("aka_manbou_1", () -> new PaintingVariant(64, 64));
    public static final RegistryObject<PaintingVariant> AKA_MANBOU_PAINTING_2 =
            PAINTING_VARIANTS.register("aka_manbou_2", () -> new PaintingVariant(64, 64));
    public static final RegistryObject<PaintingVariant> AKA_MANBOU_PAINTING_3 =
            PAINTING_VARIANTS.register("aka_manbou_3", () -> new PaintingVariant(64, 64));
    public static final RegistryObject<PaintingVariant> ZUNDAMON_HAPPY_PAINTING =
            PAINTING_VARIANTS.register("zundamon_happy", () -> new PaintingVariant(64, 64));
    public static final RegistryObject<Item> AKA_MANBOU_PAINTING_ITEM =
            ITEMS.register(
                    "aka_manbou_painting",
                    () -> new VariantPaintingItem(
                            new Item.Properties().stacksTo(64),
                            ResourceLocation.fromNamespaceAndPath(MODID, "aka_manbou_1"),
                            ResourceLocation.fromNamespaceAndPath(MODID, "aka_manbou_2"),
                            ResourceLocation.fromNamespaceAndPath(MODID, "aka_manbou_3")));
    public static final RegistryObject<Item> ZUNDAMON_HAPPY_PAINTING_ITEM =
            ITEMS.register(
                    "zundamon_happy_painting",
                    () -> new VariantPaintingItem(
                            new Item.Properties().stacksTo(64),
                            ResourceLocation.fromNamespaceAndPath(MODID, "zundamon_happy")));

    public static final RegistryObject<Item> MAHJONG_RIICHI_STICK =
            ITEMS.register("mahjong_riichi_stick", () -> new MahjongRiichiStickItem(new Item.Properties().stacksTo(64)));

    public static final RegistryObject<Item> MAHJONG_TABLE_RECORD =
            ITEMS.register("mahjong_table_record", () -> new com.riichimahjongforge.mahjongtable.record.MahjongTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_CHUUREN_TABLE_RECORD =
            ITEMS.register(
                    "predefined_chuuren_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedChuurenpoutouTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_KOKUSHIMUSOU_TABLE_RECORD =
            ITEMS.register(
                    "predefined_kokushimusou_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedKokushimusouTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_SUUANKOU_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_suuankou_tsumo_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedSuuankouTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_KAZOE_STYLE_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_kazoe_style_tsumo_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedKazoeYakumanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_TRIPLE_KAN_OPTIONS_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_triple_kan_options_tsumo_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedTripleKanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_KAKAN_OPTIONS_TABLE_RECORD =
            ITEMS.register(
                    "predefined_kakan_options_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedKakanOptionsTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_RINSHAN_TABLE_RECORD =
            ITEMS.register(
                    "predefined_rinshan_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedRinshanTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PREDEFINED_CHANKAN_TABLE_RECORD =
            ITEMS.register(
                    "predefined_chankan_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedChankanTableRecordItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PREDEFINED_TENHOU_TABLE_RECORD =
            ITEMS.register(
                    "predefined_tenhou_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedTenhouTableRecordItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> PREDEFINED_PINFU_TSUMO_TABLE_RECORD =
            ITEMS.register(
                    "predefined_pinfu_tsumo_table_record",
                    () -> new com.riichimahjongforge.mahjongtable.record.PredefinedPinfuTableRecordItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<BlockEntityType<MahjongTableBlockEntity>> MAHJONG_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_table_new",
                    () -> BlockEntityType.Builder.of(
                            MahjongTableBlockEntity::new, MAHJONG_TABLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<MahjongSolitaireBlockEntity>> MAHJONG_SOLITAIRE_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "mahjong_solitaire",
                    () -> BlockEntityType.Builder.of(
                            MahjongSolitaireBlockEntity::new, MAHJONG_SOLITAIRE.get()).build(null));
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
    public static final RegistryObject<BlockEntityType<FovNormalizerBlockEntity>> FOV_NORMALIZER_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "fov_normalizer",
                    () -> BlockEntityType.Builder.of(FovNormalizerBlockEntity::new, FOV_NORMALIZER.get()).build(null));

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

    public static final RegistryObject<MenuType<MahjongTableMenu>> MAHJONG_TABLE_MENU =
            MENU_TYPES.register(
                    "mahjong_table_new",
                    () -> IForgeMenuType.create(MahjongTableMenu::fromNetwork));
    public static final RegistryObject<MenuType<MahjongTableSettingsMenu>> MAHJONG_TABLE_SETTINGS_MENU =
            MENU_TYPES.register(
                    "mahjong_table_new_settings",
                    () -> IForgeMenuType.create(MahjongTableSettingsMenu::fromNetwork));
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
                        output.accept(MAHJONG_SOLITAIRE_ITEM.get());
                        output.accept(MAHJONG_FAKE_STAR.get());
                        output.accept(MAHJONG_STAR.get());
                        output.accept(MAHJONG_ALTAR_ITEM.get());
                        output.accept(YAKU_GENERATOR_TIER_1_ITEM.get());
                        output.accept(YAKU_GENERATOR_TIER_2_ITEM.get());
                        output.accept(YAKU_GENERATOR_TIER_3_ITEM.get());
                        output.accept(FOV_NORMALIZER_ITEM.get());
                        output.accept(MAHJONG_WRENCH.get());
                        output.accept(MAHJONG_RED_DRAGON_SOUL.get());
                        output.accept(AKA_MANBOU_PAINTING_ITEM.get());
                        output.accept(ZUNDAMON_HAPPY_PAINTING_ITEM.get());
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
                        output.accept(PREDEFINED_TENHOU_TABLE_RECORD.get().getDefaultInstance());
                        output.accept(PREDEFINED_PINFU_TSUMO_TABLE_RECORD.get().getDefaultInstance());
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
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        MENU_TYPES.register(modBus);
        MOB_EFFECTS.register(modBus);
        CREATIVE_MODE_TABS.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        SOUND_EVENTS.register(modBus);
        ENTITY_TYPES.register(modBus);
        PAINTING_VARIANTS.register(modBus);
        modBus.addListener(this::onInterModEnqueue);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onInterModEnqueue(InterModEnqueueEvent event) {
        for (String id : new String[] {"mahjong_table_new", "mahjong_solitaire"}) {
            String fullId = ResourceLocation.fromNamespaceAndPath(MODID, id).toString();
            InterModComms.sendTo("carryon", "blacklistBlock", () -> fullId);
            LOGGER.info("Sent Carry On IMC blacklist for block {}", fullId);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Riichi Mahjong Forge server starting");
    }
}
