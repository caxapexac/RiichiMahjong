package com.themahjong.yaku;

import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongTile;
import com.themahjong.TheMahjongTile.Suit;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FuritenTest {

    private static TheMahjongTile m(int rank) { return new TheMahjongTile(Suit.MANZU, rank, false); }
    private static TheMahjongTile p(int rank) { return new TheMahjongTile(Suit.PINZU, rank, false); }
    private static TheMahjongTile s(int rank) { return new TheMahjongTile(Suit.SOUZU, rank, false); }

    /** Tenpai on 1m / 4m via the 2-3m ryanmen + 4-5-6m + 7-8-9m + 1p triplet + 2p pair. */
    private static TheMahjongPlayer tenpaiOn1mOr4m(List<TheMahjongDiscard> discards, boolean tempFuriten,
                                                    boolean riichiPermFuriten) {
        List<TheMahjongTile> hand = new ArrayList<>(Arrays.asList(
                m(2), m(3),
                m(4), m(5), m(6),
                m(7), m(8), m(9),
                p(1), p(1), p(1),
                p(2), p(2)));
        return new TheMahjongPlayer(
                25000,
                TheMahjongPlayer.RiichiState.NONE,
                false,
                hand, List.of(), discards,
                tempFuriten ? List.of(m(1)) : List.of(),
                riichiPermFuriten);
    }

    @Test
    void canRon_legalWhenWaitTileMatches() {
        TheMahjongPlayer p = tenpaiOn1mOr4m(List.of(), false, false);
        assertTrue(Furiten.canRon(p, m(1)));
        assertTrue(Furiten.canRon(p, m(4)));
    }

    @Test
    void canRon_falseWhenTileNotInWait() {
        TheMahjongPlayer p = tenpaiOn1mOr4m(List.of(), false, false);
        assertFalse(Furiten.canRon(p, m(2)));
        assertFalse(Furiten.canRon(p, p(5)));
    }

    @Test
    void discardRiverFuriten_locksAllRons() {
        // Player previously discarded 4m — one of their wait tiles is in their own river.
        List<TheMahjongDiscard> discards = List.of(
                new TheMahjongDiscard(s(9), false),
                new TheMahjongDiscard(m(4), false));
        TheMahjongPlayer p = tenpaiOn1mOr4m(discards, false, false);
        assertTrue(Furiten.inDiscardRiverFuriten(p));
        // Even ron'ing on 1m (not in own discards) is denied — discard-river furiten is total.
        assertFalse(Furiten.canRon(p, m(1)));
        assertFalse(Furiten.canRon(p, m(4)));
    }

    @Test
    void temporaryFuriten_locksAllRons() {
        TheMahjongPlayer p = tenpaiOn1mOr4m(List.of(), true, false);
        assertTrue(p.temporaryFuriten());
        assertFalse(Furiten.canRon(p, m(1)));
        assertFalse(Furiten.canRon(p, m(4)));
    }

    @Test
    void riichiPermanentFuriten_locksAllRons() {
        TheMahjongPlayer p = tenpaiOn1mOr4m(List.of(), false, true);
        assertTrue(p.riichiPermanentFuriten());
        assertFalse(Furiten.canRon(p, m(1)));
        assertFalse(Furiten.canRon(p, m(4)));
    }

    @Test
    void notTenpai_isFuritenFalse() {
        TheMahjongPlayer p = new TheMahjongPlayer(
                25000, TheMahjongPlayer.RiichiState.NONE, false,
                List.of(m(1), m(2), m(3)), List.of(), List.of(), List.of(), false);
        assertFalse(Furiten.isFuriten(p));
        assertFalse(Furiten.canRon(p, m(4)));
    }
}
