package com.themahjong.driver;

import com.themahjong.TheMahjongTile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the multiset-enumeration helper backing chi/pon variant generation.
 * Red-five duplicates produce additional variants; identical-rank duplicates collapse.
 */
class ClaimVariantsTest {

    private static TheMahjongTile m(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, false);
    }

    private static TheMahjongTile mRed(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, true);
    }

    @Test
    void twoIdenticalTilesProduceOneVariant() {
        List<List<TheMahjongTile>> out =
                TheMahjongDriver.sizeKSubMultisets(List.of(m(5), m(5)), 2);
        assertEquals(1, out.size());
        assertEquals(List.of(m(5), m(5)), out.get(0));
    }

    @Test
    void twoNonRedAndOneRedProduceTwoPonVariants() {
        // Hand has two regular 5m and one red 5m. Pon (size=2) variants:
        //   {5m, 5m}     — keep red
        //   {5m, 5m-red} — commit red
        List<List<TheMahjongTile>> out =
                TheMahjongDriver.sizeKSubMultisets(List.of(m(5), m(5), mRed(5)), 2);
        assertEquals(2, out.size());
        assertTrue(out.contains(List.of(m(5), m(5))), out.toString());
        assertTrue(out.contains(List.of(m(5), mRed(5))), out.toString());
    }

    @Test
    void threeIdenticalTilesProduceOneDaiminkanVariant() {
        List<List<TheMahjongTile>> out =
                TheMahjongDriver.sizeKSubMultisets(List.of(m(5), m(5), m(5)), 3);
        assertEquals(1, out.size());
    }

    @Test
    void mixedTilesProduceOneDaiminkanVariant() {
        // 3 distinct tiles → one combination only (size==input)
        List<List<TheMahjongTile>> out =
                TheMahjongDriver.sizeKSubMultisets(List.of(m(5), m(5), mRed(5)), 3);
        assertEquals(1, out.size());
    }

    @Test
    void emptyInputProducesEmpty() {
        assertEquals(List.of(), TheMahjongDriver.sizeKSubMultisets(List.of(), 2));
    }

    @Test
    void exactSizeOneInputAndOneOutput() {
        List<TheMahjongTile> hand = List.of(m(5), mRed(5));
        List<List<TheMahjongTile>> out = TheMahjongDriver.sizeKSubMultisets(hand, 2);
        assertEquals(1, out.size());
    }
}
