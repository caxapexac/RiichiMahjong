package com.themahjong;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class TheMahjongTileSet {

    private final List<TheMahjongTile> tiles;
    private final Map<TheMahjongTile, Integer> copiesPerTile;

    public TheMahjongTileSet(List<TheMahjongTile> tiles, Map<TheMahjongTile, Integer> copiesPerTile) {
        if (tiles == null || copiesPerTile == null) {
            throw new IllegalArgumentException("tiles and copiesPerTile cannot be null");
        }
        this.tiles = List.copyOf(tiles);
        this.copiesPerTile = Map.copyOf(copiesPerTile);
        if (new java.util.HashSet<>(this.tiles).size() != this.tiles.size()) {
            throw new IllegalArgumentException("tiles list contains duplicates");
        }
        if (this.tiles.size() != this.copiesPerTile.size()) {
            throw new IllegalArgumentException("tiles and copiesPerTile must have the same size (no duplicates in tiles, no orphan map entries)");
        }
        for (TheMahjongTile tile : this.tiles) {
            Integer copies = this.copiesPerTile.get(tile);
            if (copies == null) {
                throw new IllegalArgumentException("tile in tiles list has no entry in copiesPerTile: " + tile);
            }
            if (copies < 1) {
                throw new IllegalArgumentException("every tile must have at least one copy");
            }
        }
    }

    public List<TheMahjongTile> tiles() {
        return tiles;
    }

    public Map<TheMahjongTile, Integer> copiesPerTile() {
        return copiesPerTile;
    }

    public List<TheMahjongTile> createOrderedWall() {
        ArrayList<TheMahjongTile> wall = new ArrayList<>();
        for (TheMahjongTile tile : tiles) {
            int copies = copiesPerTile.get(tile);
            for (int copy = 0; copy < copies; copy++) {
                wall.add(tile);
            }
        }
        return List.copyOf(wall);
    }

    public List<TheMahjongTile> createShuffledWall(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random cannot be null");
        }
        ArrayList<TheMahjongTile> wall = new ArrayList<>(createOrderedWall());
        Collections.shuffle(wall, random);
        return List.copyOf(wall);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TheMahjongTileSet other)) return false;
        return tiles.equals(other.tiles) && copiesPerTile.equals(other.copiesPerTile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tiles, copiesPerTile);
    }

    @Override
    public String toString() {
        int totalCopies = copiesPerTile.values().stream().mapToInt(Integer::intValue).sum();
        return "TheMahjongTileSet{" + tiles.size() + " tile kinds, " + totalCopies + " total copies}";
    }

    public static TheMahjongTileSet standardRiichi(boolean includeRedFives) {
        ArrayList<TheMahjongTile> tiles = new ArrayList<>();
        LinkedHashMap<TheMahjongTile, Integer> copies = new LinkedHashMap<>();

        addSuit(tiles, copies, TheMahjongTile.Suit.MANZU, includeRedFives);
        addSuit(tiles, copies, TheMahjongTile.Suit.PINZU, includeRedFives);
        addSuit(tiles, copies, TheMahjongTile.Suit.SOUZU, includeRedFives);

        for (TheMahjongTile.Wind wind : TheMahjongTile.Wind.values()) {
            addHonor(tiles, copies, TheMahjongTile.Suit.WIND, wind.tileRank());
        }
        for (TheMahjongTile.Dragon dragon : TheMahjongTile.Dragon.values()) {
            addHonor(tiles, copies, TheMahjongTile.Suit.DRAGON, dragon.tileRank());
        }

        return new TheMahjongTileSet(tiles, copies);
    }

    /**
     * 3-player Sanma deck: standardRiichi minus M2-M8 (28 tiles fewer; 108 total).
     * Red fives at most one each in pinzu and souzu — never in manzu since 5m doesn't exist.
     */
    public static TheMahjongTileSet standardSanma(boolean includeRedFives) {
        ArrayList<TheMahjongTile> tiles = new ArrayList<>();
        LinkedHashMap<TheMahjongTile, Integer> copies = new LinkedHashMap<>();

        // Manzu: only 1m and 9m (4 copies each); never any red fives in manzu for sanma.
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTile m9 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false);
        tiles.add(m1); copies.put(m1, 4);
        tiles.add(m9); copies.put(m9, 4);

        addSuit(tiles, copies, TheMahjongTile.Suit.PINZU, includeRedFives);
        addSuit(tiles, copies, TheMahjongTile.Suit.SOUZU, includeRedFives);

        for (TheMahjongTile.Wind wind : TheMahjongTile.Wind.values()) {
            addHonor(tiles, copies, TheMahjongTile.Suit.WIND, wind.tileRank());
        }
        for (TheMahjongTile.Dragon dragon : TheMahjongTile.Dragon.values()) {
            addHonor(tiles, copies, TheMahjongTile.Suit.DRAGON, dragon.tileRank());
        }

        return new TheMahjongTileSet(tiles, copies);
    }

    private static void addSuit(
            List<TheMahjongTile> tiles,
            Map<TheMahjongTile, Integer> copies,
            TheMahjongTile.Suit suit,
            boolean includeRedFives) {
        for (int rank = 1; rank <= 9; rank++) {
            TheMahjongTile tile = new TheMahjongTile(suit, rank, false);
            tiles.add(tile);
            copies.put(tile, 4);
        }
        if (includeRedFives) {
            TheMahjongTile redFive = new TheMahjongTile(suit, 5, true);
            TheMahjongTile normalFive = new TheMahjongTile(suit, 5, false);
            tiles.add(redFive);
            copies.put(redFive, 1);
            copies.put(normalFive, 3);
        }
    }

    private static void addHonor(
            List<TheMahjongTile> tiles,
            Map<TheMahjongTile, Integer> copies,
            TheMahjongTile.Suit suit,
            int rank) {
        TheMahjongTile tile = new TheMahjongTile(suit, rank, false);
        tiles.add(tile);
        copies.put(tile, 4);
    }
}
