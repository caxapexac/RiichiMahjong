package com.themahjong;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheMahjongMeldTest {

    @Test
    void chiUsesClaimedTileIndexToPreserveOfficialSequencePosition() {
        TheMahjongTile two = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        TheMahjongTile three = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongTile four = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false);

        TheMahjongMeld.Chi meld = new TheMahjongMeld.Chi(List.of(three, four, two), 2, 1, 0);

        assertEquals(List.of(two, three, four), meld.tiles());
        assertEquals(OptionalInt.of(0), meld.sidewaysTileIndex());
    }

    @Test
    void ponUsesClaimedTileIndexForHorizontalPosition() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.WIND, 1, false);

        TheMahjongMeld.Pon meld = new TheMahjongMeld.Pon(List.of(tile, tile, tile), 1, 2, 3);

        assertEquals(OptionalInt.of(1), meld.sidewaysTileIndex());
    }

    @Test
    void ankanHasNoSidewaysTile() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 9, false);

        TheMahjongMeld.Ankan meld = new TheMahjongMeld.Ankan(List.of(tile, tile, tile, tile));

        assertEquals(OptionalInt.empty(), meld.sidewaysTileIndex());
    }

    @Test
    void ponRejectsOutOfRangeClaimedTileIndex() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.WIND, 1, false);

        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongMeld.Pon(List.of(tile, tile, tile), 3, 1, 0));
    }

    @Test
    void kakanRejectsAddedTileMismatch() {
        TheMahjongTile ponTile = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, false);
        TheMahjongTile wrongTile = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 6, false);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(ponTile, ponTile, ponTile), 0, 1, 2);

        assertThrows(IllegalArgumentException.class, () -> new TheMahjongMeld.Kakan(pon, wrongTile));
    }

    @Test
    void kakanTilesContainsPonTilesPlusAddedTile() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, false);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(tile, tile, tile), 0, 1, 2);

        TheMahjongMeld.Kakan kakan = new TheMahjongMeld.Kakan(pon, tile);

        assertEquals(4, kakan.tiles().size());
        assertEquals(OptionalInt.of(pon.claimedTileIndex()), kakan.sidewaysTileIndex());
    }

    @Test
    void chiRejectsHonorTiles() {
        TheMahjongTile east = new TheMahjongTile(TheMahjongTile.Suit.WIND, 1, false);
        TheMahjongTile south = new TheMahjongTile(TheMahjongTile.Suit.WIND, 2, false);
        TheMahjongTile west = new TheMahjongTile(TheMahjongTile.Suit.WIND, 3, false);
        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongMeld.Chi(List.of(east, south, west), 0, 1, 0));
    }

    @Test
    void chiRejectsNonConsecutiveRanks() {
        TheMahjongTile one = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTile two = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        TheMahjongTile four = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false);
        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongMeld.Chi(List.of(one, two, four), 0, 1, 0));
    }

    @Test
    void chiRejectsMixedSuits() {
        TheMahjongTile man = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongTile pin = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 2, false);
        TheMahjongTile sou = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 3, false);
        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongMeld.Chi(List.of(man, pin, sou), 0, 1, 0));
    }

    @Test
    void openMeldsReturnTrue() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false);
        TheMahjongTile two = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        TheMahjongTile three = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongTile four = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 4, false);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(tile, tile, tile), 0, 1, 0);

        assertTrue(new TheMahjongMeld.Chi(List.of(two, three, four), 0, 1, 0).open());
        assertTrue(pon.open());
        assertTrue(new TheMahjongMeld.Daiminkan(List.of(tile, tile, tile, tile), 0, 1, 0).open());
        assertTrue(new TheMahjongMeld.Kakan(pon, tile).open());
    }

    @Test
    void ankanIsNotOpen() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false);
        assertFalse(new TheMahjongMeld.Ankan(List.of(tile, tile, tile, tile)).open());
    }

    @Test
    void daiminkanRejectsMixedKinds() {
        TheMahjongTile five = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, false);
        TheMahjongTile six = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 6, false);
        assertThrows(IllegalArgumentException.class,
                () -> new TheMahjongMeld.Daiminkan(List.of(five, five, five, six), 0, 1, 0));
    }

    @Test
    void openMeldSourceSeatAndDiscardIndexAreAccessible() {
        TheMahjongTile tile = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 3, false);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(List.of(tile, tile, tile), 1, 2, 7);

        assertEquals(2, pon.sourceSeat());
        assertEquals(7, pon.sourceDiscardIndex());
    }
}
