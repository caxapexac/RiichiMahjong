package com.riichimahjongforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.mahjongcore.tile.Tile;

/** Registers one block + {@link BlockItem} per mahjong tile type (34 total). */
public final class MahjongTileItems {

    @SuppressWarnings("unchecked")
    private static final RegistryObject<Block>[] TILE_BLOCKS_BY_CODE = new RegistryObject[34];

    @SuppressWarnings("unchecked")
    private static final RegistryObject<Item>[] TILE_ITEMS_BY_CODE = new RegistryObject[34];
    private static final Map<Item, Integer> CODE_BY_ITEM = new HashMap<>();

    private MahjongTileItems() {}

    public static void register(DeferredRegister<Block> blocks, DeferredRegister<Item> items) {
        for (Tile tile : Tile.values()) {
            int code = tile.getCode();
            String id = idForTile(tile);
            RegistryObject<Block> blockRo =
                    blocks.register(id, () -> new MahjongTileBlock(MahjongTileBlock.defaultProperties()));
            TILE_BLOCKS_BY_CODE[code] = blockRo;
            TILE_ITEMS_BY_CODE[code] =
                    items.register(id, () -> new BlockItem(blockRo.get(), new Item.Properties().stacksTo(1)));
        }
    }

    private static String idForTile(Tile tile) {
        return switch (tile) {
            case M1 -> "tile_man_1";
            case M2 -> "tile_man_2";
            case M3 -> "tile_man_3";
            case M4 -> "tile_man_4";
            case M5 -> "tile_man_5";
            case M6 -> "tile_man_6";
            case M7 -> "tile_man_7";
            case M8 -> "tile_man_8";
            case M9 -> "tile_man_9";
            case P1 -> "tile_pin_1";
            case P2 -> "tile_pin_2";
            case P3 -> "tile_pin_3";
            case P4 -> "tile_pin_4";
            case P5 -> "tile_pin_5";
            case P6 -> "tile_pin_6";
            case P7 -> "tile_pin_7";
            case P8 -> "tile_pin_8";
            case P9 -> "tile_pin_9";
            case S1 -> "tile_sou_1";
            case S2 -> "tile_sou_2";
            case S3 -> "tile_sou_3";
            case S4 -> "tile_sou_4";
            case S5 -> "tile_sou_5";
            case S6 -> "tile_sou_6";
            case S7 -> "tile_sou_7";
            case S8 -> "tile_sou_8";
            case S9 -> "tile_sou_9";
            case TON -> "tile_wind_east";
            case NAN -> "tile_wind_south";
            case SHA -> "tile_wind_west";
            case PEI -> "tile_wind_north";
            case HAK -> "tile_dragon_white";
            case HAT -> "tile_dragon_green";
            case CHN -> "tile_dragon_red";
        };
    }

    public static Item itemForCode(int code) {
        if (code < 0 || code >= TILE_ITEMS_BY_CODE.length) {
            return null;
        }
        RegistryObject<Item> ro = TILE_ITEMS_BY_CODE[code];
        return ro == null ? null : ro.get();
    }

    public static Block blockForCode(int code) {
        if (code < 0 || code >= TILE_BLOCKS_BY_CODE.length) {
            return null;
        }
        RegistryObject<Block> ro = TILE_BLOCKS_BY_CODE[code];
        return ro == null ? null : ro.get();
    }

    public static Integer codeForItem(Item item) {
        if (CODE_BY_ITEM.isEmpty()) {
            for (int code = 0; code < TILE_ITEMS_BY_CODE.length; code++) {
                RegistryObject<Item> ro = TILE_ITEMS_BY_CODE[code];
                if (ro != null) {
                    CODE_BY_ITEM.put(ro.get(), code);
                }
            }
        }
        return CODE_BY_ITEM.get(item);
    }

    public static List<Item> allTileItems() {
        ArrayList<Item> out = new ArrayList<>(34);
        for (RegistryObject<Item> ro : TILE_ITEMS_BY_CODE) {
            if (ro != null) {
                out.add(ro.get());
            }
        }
        return out;
    }

    public static List<Block> allTileBlocks() {
        ArrayList<Block> out = new ArrayList<>(34);
        for (RegistryObject<Block> ro : TILE_BLOCKS_BY_CODE) {
            if (ro != null) {
                out.add(ro.get());
            }
        }
        return out;
    }
}
