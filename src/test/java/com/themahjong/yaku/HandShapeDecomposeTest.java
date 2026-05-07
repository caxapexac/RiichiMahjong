package com.themahjong.yaku;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandShapeDecomposeTest {

    // -------------------------------------------------------------------------
    // Helpers

    private static TheMahjongTile t(Suit suit, int rank) {
        return new TheMahjongTile(suit, rank, false);
    }

    private static TheMahjongTile m(int rank) { return t(Suit.MANZU, rank); }
    private static TheMahjongTile p(int rank) { return t(Suit.PINZU, rank); }
    private static TheMahjongTile s(int rank) { return t(Suit.SOUZU, rank); }
    private static TheMahjongTile w(int rank) { return t(Suit.WIND, rank); }
    private static TheMahjongTile d(int rank) { return t(Suit.DRAGON, rank); }

    private List<HandShape> decompose(List<TheMahjongTile> tiles) {
        return HandShape.decompose(tiles, List.of());
    }

    private List<TheMahjongTile> hand(TheMahjongTile... tiles) {
        return List.of(tiles);
    }

    // -------------------------------------------------------------------------
    // Reject non-14-tile inputs

    @Test
    void rejectsWrongTileCount() {
        List<TheMahjongTile> thirteen = List.of(
                m(1),m(2),m(3), m(4),m(5),m(6), m(7),m(8),m(9),
                p(1),p(2),p(3), s(1));
        assertTrue(decompose(thirteen).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Standard form — pure sequences

    @Test
    void threeSequencesPlusOnePair() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(2),m(3), m(4),m(5),m(6), m(7),m(8),m(9),
                p(1),p(1),
                s(1),s(2),s(3));
        List<HandShape> shapes = decompose(tiles);
        assertFalse(shapes.isEmpty());
        assertTrue(shapes.stream().anyMatch(h -> h instanceof HandShape.Standard));
    }

    @Test
    void fourSequencesOneSuit() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(2),m(3), m(1),m(2),m(3),
                m(4),m(5),m(6), m(7),m(8),m(9), m(4),m(4));
        assertFalse(decompose(tiles).isEmpty());
    }

    // -------------------------------------------------------------------------
    // Standard form — triplets

    @Test
    void fourTripletsPlusOnePair() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(1),m(1),
                p(2),p(2),p(2),
                s(3),s(3),s(3),
                d(1),d(1),d(1),
                w(1),w(1));
        List<HandShape> shapes = decompose(tiles);
        assertFalse(shapes.isEmpty());
        assertTrue(shapes.stream().allMatch(h -> h instanceof HandShape.Standard));
    }

    // -------------------------------------------------------------------------
    // Mixed decompositions (same hand has multiple valid forms)

    @Test
    void ambiguousHandHasMultipleDecompositions() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(2),m(3), m(1),m(2),m(3),
                p(4),p(5),p(6),
                s(7),s(8),s(9),
                m(9),m(9));
        assertFalse(decompose(tiles).isEmpty());

        // 1-1-1-2-2-2-3-3-3m can be three triplets OR three sequences
        List<TheMahjongTile> tiles2 = hand(
                m(1),m(1),m(1), m(2),m(2),m(2), m(3),m(3),m(3),
                p(5),p(5),p(5),
                s(9),s(9));
        List<HandShape> shapes2 = decompose(tiles2);
        assertTrue(shapes2.size() >= 2, "expected at least 2 decompositions, got " + shapes2.size());
    }

    // -------------------------------------------------------------------------
    // Chitoitsu

    @Test
    void chitoitsuSevenDistinctPairs() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(1), m(3),m(3), m(5),m(5),
                p(2),p(2), p(4),p(4),
                s(6),s(6),
                d(1),d(1));
        List<HandShape> shapes = decompose(tiles);
        assertFalse(shapes.isEmpty());
        assertTrue(shapes.stream().anyMatch(h -> h instanceof HandShape.Chitoitsu));
    }

    @Test
    void chitoitsuRejectedWhenFourOfAKind() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(1),m(1),m(1),
                m(3),m(3), m(5),m(5),
                p(2),p(2), p(4),p(4),
                s(6),s(6));
        assertTrue(decompose(tiles).stream().noneMatch(h -> h instanceof HandShape.Chitoitsu));
    }

    // -------------------------------------------------------------------------
    // Kokushi

    @Test
    void kokushiAllThirteenTerminalsAndHonors() {
        List<TheMahjongTile> tiles = hand(
                m(1), m(9),
                p(1), p(9),
                s(1), s(9),
                w(1), w(2), w(3), w(4),
                d(1), d(2), d(3),
                m(1));
        List<HandShape> shapes = decompose(tiles);
        assertFalse(shapes.isEmpty());
        assertTrue(shapes.stream().anyMatch(h -> h instanceof HandShape.Kokushimusou));
        HandShape.Kokushimusou kok = shapes.stream()
                .filter(h -> h instanceof HandShape.Kokushimusou)
                .map(h -> (HandShape.Kokushimusou) h)
                .findFirst().orElseThrow();
        assertEquals(Suit.MANZU, kok.pairTile().suit());
        assertEquals(1, kok.pairTile().rank());
    }

    @Test
    void kokushiRejectedIfMissingTerminal() {
        List<TheMahjongTile> tiles = hand(
                m(1), m(1),
                p(1), p(9),
                s(1), s(9),
                w(1), w(2), w(3), w(4),
                d(1), d(2), d(3),
                m(1));
        assertTrue(decompose(tiles).stream().noneMatch(h -> h instanceof HandShape.Kokushimusou));
    }

    // -------------------------------------------------------------------------
    // With open melds

    @Test
    void oneOpenPonPlusThreeClosedGroups() {
        TheMahjongTile ponTile = d(1);
        TheMahjongMeld.Pon pon = new TheMahjongMeld.Pon(
                List.of(ponTile, ponTile, ponTile), 2, 1, 0);
        List<TheMahjongTile> concealed = List.of(
                m(1),m(2),m(3),
                p(4),p(5),p(6),
                s(7),s(8),s(9),
                m(9),m(9));
        List<HandShape> shapes = HandShape.decompose(concealed, List.of(pon));
        assertFalse(shapes.isEmpty());
        assertTrue(shapes.stream().allMatch(h -> h instanceof HandShape.Standard));
    }

    // -------------------------------------------------------------------------
    // Edge cases

    @Test
    void emptyMeldsListAllowed() {
        List<TheMahjongTile> tiles = hand(
                m(1),m(2),m(3), m(4),m(5),m(6), m(7),m(8),m(9),
                p(1),p(2),p(3), s(5),s(5));
        assertFalse(decompose(tiles).isEmpty());
    }

    @Test
    void honorTilesPairAndTriplet() {
        List<TheMahjongTile> tiles = hand(
                w(1),w(1),w(1),
                m(1),m(2),m(3),
                p(4),p(5),p(6),
                s(7),s(8),s(9),
                d(2),d(2));
        assertFalse(decompose(tiles).isEmpty());
    }

    @Test
    void honorTilesCannotFormSequences() {
        // w(1) appears once — can't form triplet, can't start a sequence → no standard form
        List<TheMahjongTile> tiles = hand(
                w(1),w(2),w(3),
                w(4),w(4),w(4),
                m(1),m(2),m(3),
                p(5),p(5),p(5),
                s(9),s(9));
        assertTrue(decompose(tiles).stream().noneMatch(h -> h instanceof HandShape.Standard));
    }
}
