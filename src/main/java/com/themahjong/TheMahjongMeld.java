package com.themahjong;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

public sealed interface TheMahjongMeld
        permits TheMahjongMeld.Chi, TheMahjongMeld.Pon,
                TheMahjongMeld.Daiminkan, TheMahjongMeld.Kakan, TheMahjongMeld.Ankan {

    boolean open();

    List<TheMahjongTile> tiles();

    OptionalInt sidewaysTileIndex();

    record Chi(
            List<TheMahjongTile> tiles,
            int claimedTileIndex,
            int sourceSeat,
            int sourceDiscardIndex) implements TheMahjongMeld {

        public Chi {
            requireValidSeat(sourceSeat);
            requireNonNegative(sourceDiscardIndex, "sourceDiscardIndex");
            if (tiles == null) throw new IllegalArgumentException("tiles cannot be null");
            if (tiles.size() != 3) throw new IllegalArgumentException("Chi must have exactly 3 tiles");
            if (claimedTileIndex < 0 || claimedTileIndex >= tiles.size())
                throw new IllegalArgumentException("Chi claimedTileIndex out of range");
            TheMahjongTile claimed = tiles.get(claimedTileIndex);
            List<TheMahjongTile> sorted = sortedByRank(tiles);
            validateChiTiles(sorted);
            int normalizedIndex = sorted.indexOf(claimed);
            if (normalizedIndex < 0) throw new IllegalArgumentException("Chi claimed tile not in tiles");
            tiles = List.copyOf(sorted);
            claimedTileIndex = normalizedIndex;
        }

        @Override public boolean open() { return true; }
        @Override public OptionalInt sidewaysTileIndex() { return OptionalInt.of(claimedTileIndex); }
    }

    record Pon(
            List<TheMahjongTile> tiles,
            int claimedTileIndex,
            int sourceSeat,
            int sourceDiscardIndex) implements TheMahjongMeld {

        public Pon {
            requireValidSeat(sourceSeat);
            requireNonNegative(sourceDiscardIndex, "sourceDiscardIndex");
            if (tiles == null) throw new IllegalArgumentException("tiles cannot be null");
            tiles = List.copyOf(tiles);
            requireTileCount(tiles, 3, "Pon");
            requireAllSameKind(tiles, "Pon");
            if (claimedTileIndex < 0 || claimedTileIndex > 2)
                throw new IllegalArgumentException("Pon claimedTileIndex must be 0–2");
        }

        @Override public boolean open() { return true; }
        @Override public OptionalInt sidewaysTileIndex() { return OptionalInt.of(claimedTileIndex); }
    }

    record Daiminkan(
            List<TheMahjongTile> tiles,
            int claimedTileIndex,
            int sourceSeat,
            int sourceDiscardIndex) implements TheMahjongMeld {

        public Daiminkan {
            requireValidSeat(sourceSeat);
            requireNonNegative(sourceDiscardIndex, "sourceDiscardIndex");
            if (tiles == null) throw new IllegalArgumentException("tiles cannot be null");
            tiles = List.copyOf(tiles);
            requireTileCount(tiles, 4, "Daiminkan");
            requireAllSameKind(tiles, "Daiminkan");
            if (claimedTileIndex < 0 || claimedTileIndex > 3)
                throw new IllegalArgumentException("Daiminkan claimedTileIndex must be 0–3");
        }

        @Override public boolean open() { return true; }
        @Override public OptionalInt sidewaysTileIndex() { return OptionalInt.of(claimedTileIndex); }
    }

    record Kakan(Pon upgradedFrom, TheMahjongTile addedTile) implements TheMahjongMeld {

        public Kakan {
            if (upgradedFrom == null) throw new IllegalArgumentException("upgradedFrom cannot be null");
            if (addedTile == null) throw new IllegalArgumentException("addedTile cannot be null");
            TheMahjongTile base = upgradedFrom.tiles().get(0);
            if (addedTile.suit() != base.suit() || addedTile.rank() != base.rank())
                throw new IllegalArgumentException("Kakan addedTile must match the Pon tile kind");
        }

        @Override public boolean open() { return true; }

        @Override
        public List<TheMahjongTile> tiles() {
            List<TheMahjongTile> result = new ArrayList<>(upgradedFrom.tiles());
            result.add(addedTile);
            return List.copyOf(result);
        }

        @Override public OptionalInt sidewaysTileIndex() { return OptionalInt.of(upgradedFrom.claimedTileIndex()); }
    }

    record Ankan(List<TheMahjongTile> tiles) implements TheMahjongMeld {

        public Ankan {
            if (tiles == null) throw new IllegalArgumentException("tiles cannot be null");
            tiles = List.copyOf(tiles);
            requireTileCount(tiles, 4, "Ankan");
            requireAllSameKind(tiles, "Ankan");
        }

        @Override public boolean open() { return false; }
        @Override public OptionalInt sidewaysTileIndex() { return OptionalInt.empty(); }
    }

    private static void requireValidSeat(int seat) {
        int max = TheMahjongTile.Wind.values().length;
        if (seat < 0 || seat >= max)
            throw new IllegalArgumentException("sourceSeat must be 0–" + (max - 1));
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must be non-negative");
    }

    private static void requireTileCount(List<TheMahjongTile> tiles, int expected, String name) {
        if (tiles.size() != expected)
            throw new IllegalArgumentException(name + " must have exactly " + expected + " tiles");
    }

    private static void requireAllSameKind(List<TheMahjongTile> tiles, String name) {
        TheMahjongTile first = tiles.get(0);
        boolean allMatch = tiles.stream().allMatch(t -> t.suit() == first.suit() && t.rank() == first.rank());
        if (!allMatch) throw new IllegalArgumentException(name + " tiles must all be the same kind");
    }

    private static List<TheMahjongTile> sortedByRank(List<TheMahjongTile> tiles) {
        return tiles.stream()
                .sorted((a, b) -> Integer.compare(a.rank(), b.rank()))
                .toList();
    }

    private static void validateChiTiles(List<TheMahjongTile> tiles) {
        TheMahjongTile a = tiles.get(0), b = tiles.get(1), c = tiles.get(2);
        if (a.honor() || b.honor() || c.honor())
            throw new IllegalArgumentException("Chi cannot contain honor tiles");
        if (a.suit() != b.suit() || b.suit() != c.suit())
            throw new IllegalArgumentException("Chi tiles must share the same suit");
        if (b.rank() != a.rank() + 1 || c.rank() != b.rank() + 1)
            throw new IllegalArgumentException("Chi tiles must be consecutive");
    }
}
