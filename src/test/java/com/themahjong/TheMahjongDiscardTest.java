package com.themahjong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TheMahjongDiscardTest {

    @Test
    void rejectsNullTile() {
        assertThrows(IllegalArgumentException.class, () -> new TheMahjongDiscard(null, false));
    }

    @Test
    void recordEquality() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongDiscard a = new TheMahjongDiscard(tile, false);
        TheMahjongDiscard b = new TheMahjongDiscard(tile, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void riichiDeclaredDistinguishesDiscards() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        assertNotEquals(new TheMahjongDiscard(tile, false), new TheMahjongDiscard(tile, true));
    }

    @Test
    void differentTilesDistinguishesDiscards() {
        TheMahjongTile three = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongTile four = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false);
        assertNotEquals(new TheMahjongDiscard(three, false), new TheMahjongDiscard(four, false));
    }
}
