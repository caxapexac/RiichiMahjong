package com.riichimahjongforge.mahjongcore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import com.themahjong.TheMahjongTile;

/** Registers one block + {@link BlockItem} per mahjong tile type. Codes 0..33 are
 *  the standard 34 tiles; codes 34/35/36 are the red-five (aka dora) variants of
 *  Man-5 / Pin-5 / Sou-5. */
public final class MahjongTileItems {

    /** Aka-dora tile codes (red-five variants of Man/Pin/Sou 5). */
    public static final int CODE_MAN_5_AKA = 34;
    public static final int CODE_PIN_5_AKA = 35;
    public static final int CODE_SOU_5_AKA = 36;
    private static final int TILE_COUNT = 37;

    public static final int CODE_MAN_4 =
            codeForSuitRank(new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false));

    @SuppressWarnings("unchecked")
    private static final RegistryObject<Block>[] TILE_BLOCKS_BY_CODE = new RegistryObject[TILE_COUNT];

    @SuppressWarnings("unchecked")
    private static final RegistryObject<Item>[] TILE_ITEMS_BY_CODE = new RegistryObject[TILE_COUNT];
    private static final Map<Item, Integer> CODE_BY_ITEM = new HashMap<>();

    private MahjongTileItems() {}

    public static void register(DeferredRegister<Block> blocks, DeferredRegister<Item> items) {
        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                TheMahjongTile tile = new TheMahjongTile(suit, rank, false);
                registerTile(blocks, items, codeForSuitRank(tile), idForSuitRank(suit, rank));
            }
        }
        registerTile(blocks, items, CODE_MAN_5_AKA, "tile_man_5_aka");
        registerTile(blocks, items, CODE_PIN_5_AKA, "tile_pin_5_aka");
        registerTile(blocks, items, CODE_SOU_5_AKA, "tile_sou_5_aka");
    }

    private static String idForSuitRank(TheMahjongTile.Suit suit, int rank) {
        return switch (suit) {
            case MANZU -> "tile_man_" + rank;
            case PINZU -> "tile_pin_" + rank;
            case SOUZU -> "tile_sou_" + rank;
            case WIND -> switch (TheMahjongTile.Wind.fromTileRank(rank)) {
                case EAST -> "tile_wind_east";
                case SOUTH -> "tile_wind_south";
                case WEST -> "tile_wind_west";
                case NORTH -> "tile_wind_north";
            };
            case DRAGON -> switch (TheMahjongTile.Dragon.fromTileRank(rank)) {
                case HAKU -> "tile_dragon_white";
                case HATSU -> "tile_dragon_green";
                case CHUN -> "tile_dragon_red";
            };
        };
    }

    private static void registerTile(DeferredRegister<Block> blocks, DeferredRegister<Item> items,
                                     int code, String id) {
        RegistryObject<Block> blockRo =
                blocks.register(id, () -> new MahjongTileBlock(MahjongTileBlock.defaultProperties()));
        TILE_BLOCKS_BY_CODE[code] = blockRo;
        TILE_ITEMS_BY_CODE[code] =
                items.register(id, () -> new BlockItem(blockRo.get(), new Item.Properties().stacksTo(1)));
    }

    public static Item itemForCode(int code) {
        if (code < 0 || code >= TILE_ITEMS_BY_CODE.length) {
            return null;
        }
        RegistryObject<Item> ro = TILE_ITEMS_BY_CODE[code];
        return ro == null ? null : ro.get();
    }

    /**
     * Maps a {@link TheMahjongTile} to the registered tile block, using the
     * (suit, rank) → int code: MANZU 1..9 → 0..8, PINZU → 9..17, SOUZU → 18..26,
     * WIND E/S/W/N → 27..30, DRAGON HAKU/HATSU/CHUN → 31..33. When
     * {@link TheMahjongTile#redDora()} is set on Man-5 / Pin-5 / Sou-5, returns
     * the aka-dora variant code (34/35/36) — visually distinct red-five blocks.
     */
    public static Block blockForTile(TheMahjongTile tile) {
        return blockForCode(codeForTile(tile));
    }

    public static int codeForTile(TheMahjongTile tile) {
        if (tile.redDora() && tile.rank() == 5) {
            return switch (tile.suit()) {
                case MANZU -> CODE_MAN_5_AKA;
                case PINZU -> CODE_PIN_5_AKA;
                case SOUZU -> CODE_SOU_5_AKA;
                default -> codeForSuitRank(tile);
            };
        }
        return codeForSuitRank(tile);
    }

    private static int codeForSuitRank(TheMahjongTile tile) {
        return switch (tile.suit()) {
            case MANZU  -> tile.rank() - 1;
            case PINZU  -> 9 + tile.rank() - 1;
            case SOUZU  -> 18 + tile.rank() - 1;
            case WIND   -> 27 + tile.rank() - 1;
            case DRAGON -> 31 + tile.rank() - 1;
        };
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
        ArrayList<Item> out = new ArrayList<>(TILE_COUNT);
        for (RegistryObject<Item> ro : TILE_ITEMS_BY_CODE) {
            if (ro != null) {
                out.add(ro.get());
            }
        }
        return out;
    }

    public static List<Block> allTileBlocks() {
        ArrayList<Block> out = new ArrayList<>(TILE_COUNT);
        for (RegistryObject<Block> ro : TILE_BLOCKS_BY_CODE) {
            if (ro != null) {
                out.add(ro.get());
            }
        }
        return out;
    }
}
