package com.themahjong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheMahjongTileTest {

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    void rejectsNullSuit() {
        assertThrows(IllegalArgumentException.class, () -> new TheMahjongTile(null, 1, false));
    }

    @Test
    void rejectsRankZero() {
        assertThrows(IllegalArgumentException.class, () -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, 0, false));
    }

    @Test
    void rejectsRankAboveMaxForNumberedSuit() {
        assertThrows(IllegalArgumentException.class, () -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, 10, false));
    }

    @Test
    void rejectsRankAboveMaxForWindSuit() {
        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongTile(TheMahjongTile.Suit.WIND, TheMahjongTile.Wind.values().length + 1, false));
    }

    @Test
    void rejectsRankAboveMaxForDragonSuit() {
        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.values().length + 1, false));
    }

    @Test
    void acceptsMaxRankBoundary() {
        assertDoesNotThrow(() -> new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false));
        assertDoesNotThrow(() -> new TheMahjongTile(TheMahjongTile.Suit.WIND, TheMahjongTile.Wind.values().length, false));
        assertDoesNotThrow(() -> new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.values().length, false));
    }

    // -------------------------------------------------------------------------
    // honor() / terminal()
    // -------------------------------------------------------------------------

    @Test
    void numberedSuitsAreNotHonor() {
        assertFalse(new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false).honor());
        assertFalse(new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, false).honor());
        assertFalse(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, false).honor());
    }

    @Test
    void windAndDragonSuitsAreHonor() {
        assertTrue(new TheMahjongTile(TheMahjongTile.Suit.WIND, 1, false).honor());
        assertTrue(new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false).honor());
    }

    @Test
    void rankOneAndNineAreTerminalForNumberedSuits() {
        assertTrue(new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false).terminal());
        assertTrue(new TheMahjongTile(TheMahjongTile.Suit.PINZU, 9, false).terminal());
        assertTrue(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 1, false).terminal());
    }

    @Test
    void middleRanksAreNotTerminal() {
        assertFalse(new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false).terminal());
    }

    @Test
    void honorTilesAreNotTerminal() {
        assertFalse(new TheMahjongTile(TheMahjongTile.Suit.WIND, 1, false).terminal());
        assertFalse(new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false).terminal());
    }

    // -------------------------------------------------------------------------
    // displayName()
    // -------------------------------------------------------------------------

    @Test
    void displayNameForNumberedSuits() {
        assertEquals("3 Man", new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false).displayName());
        assertEquals("7 Pin", new TheMahjongTile(TheMahjongTile.Suit.PINZU, 7, false).displayName());
        assertEquals("1 Sou", new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 1, false).displayName());
    }

    @Test
    void displayNameForRedDoraAppendsSuffix() {
        assertEquals("5 Man Red", new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true).displayName());
    }

    @Test
    void displayNameForWindTiles() {
        assertEquals("East", new TheMahjongTile(TheMahjongTile.Suit.WIND, TheMahjongTile.Wind.EAST.tileRank(), false).displayName());
        assertEquals("North", new TheMahjongTile(TheMahjongTile.Suit.WIND, TheMahjongTile.Wind.NORTH.tileRank(), false).displayName());
    }

    @Test
    void displayNameForDragonTiles() {
        assertEquals("White", new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.HAKU.tileRank(), false).displayName());
        assertEquals("Green", new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.HATSU.tileRank(), false).displayName());
        assertEquals("Red", new TheMahjongTile(TheMahjongTile.Suit.DRAGON, TheMahjongTile.Dragon.CHUN.tileRank(), false).displayName());
    }

    // -------------------------------------------------------------------------
    // Wind helpers
    // -------------------------------------------------------------------------

    @Test
    void windNextWrapsFromNorthToEast() {
        assertEquals(TheMahjongTile.Wind.EAST, TheMahjongTile.Wind.NORTH.next());
    }

    @Test
    void windNextAdvancesInOrder() {
        assertEquals(TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST.next());
        assertEquals(TheMahjongTile.Wind.WEST, TheMahjongTile.Wind.SOUTH.next());
        assertEquals(TheMahjongTile.Wind.NORTH, TheMahjongTile.Wind.WEST.next());
    }

    @Test
    void windFromTileRankRoundTrips() {
        for (TheMahjongTile.Wind w : TheMahjongTile.Wind.values()) {
            assertEquals(w, TheMahjongTile.Wind.fromTileRank(w.tileRank()));
        }
    }

    @Test
    void windFromTileRankRejectsInvalidRank() {
        assertThrows(IllegalArgumentException.class, () -> TheMahjongTile.Wind.fromTileRank(0));
        assertThrows(IllegalArgumentException.class,
                () -> TheMahjongTile.Wind.fromTileRank(TheMahjongTile.Wind.values().length + 1));
    }

    // -------------------------------------------------------------------------
    // Dragon helpers
    // -------------------------------------------------------------------------

    @Test
    void dragonFromTileRankRoundTrips() {
        for (TheMahjongTile.Dragon d : TheMahjongTile.Dragon.values()) {
            assertEquals(d, TheMahjongTile.Dragon.fromTileRank(d.tileRank()));
        }
    }

    @Test
    void dragonFromTileRankRejectsInvalidRank() {
        assertThrows(IllegalArgumentException.class, () -> TheMahjongTile.Dragon.fromTileRank(0));
        assertThrows(IllegalArgumentException.class,
                () -> TheMahjongTile.Dragon.fromTileRank(TheMahjongTile.Dragon.values().length + 1));
    }

    // -------------------------------------------------------------------------
    // Record equality
    // -------------------------------------------------------------------------

    @Test
    void tilesWithSameFieldsAreEqual() {
        TheMahjongTile a = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 7, false);
        TheMahjongTile b = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 7, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void redDoraDistinguishesTiles() {
        TheMahjongTile normal = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false);
        TheMahjongTile red = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true);
        assertNotEquals(normal, red);
    }
}
