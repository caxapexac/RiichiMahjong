package com.themahjong;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TheMahjongFixedDealTest {

    private static TheMahjongTile man(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, false);
    }

    private static TheMahjongTile pin(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false);
    }

    private static TheMahjongTile sou(int rank) {
        return new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false);
    }

    private static TheMahjongTile haku() {
        return new TheMahjongTile(TheMahjongTile.Suit.DRAGON,
                TheMahjongTile.Dragon.HAKU.tileRank(), false);
    }

    /** Chuurenpoutou-style hand for seat 0 (1112345678999 in MANZU). */
    private static List<TheMahjongTile> chuurenHand() {
        List<TheMahjongTile> hand = new ArrayList<>();
        hand.add(man(1)); hand.add(man(1)); hand.add(man(1));
        hand.add(man(2)); hand.add(man(3)); hand.add(man(4));
        hand.add(man(5)); hand.add(man(6)); hand.add(man(7));
        hand.add(man(8)); hand.add(man(9)); hand.add(man(9)); hand.add(man(9));
        return hand;
    }

    @Test
    void buildRound_placesReservedHandsAndTopOfWall() {
        List<TheMahjongTile> hand0 = chuurenHand();
        TheMahjongRound round = TheMahjongFixedDeal.builder()
                .handForSeat(0, hand0)
                .doraIndicators(List.of(haku()))
                .topOfWall(List.of(pin(5), sou(7)))
                .buildRound();

        assertEquals(TheMahjongRound.State.SETUP, round.state());
        assertEquals(hand0, round.players().get(0).currentHand());
        assertEquals(haku(), round.doraIndicators().get(0));
        // Top-of-wall is preserved in order.
        assertEquals(pin(5), round.liveWall().get(0));
        assertEquals(sou(7), round.liveWall().get(1));
    }

    @Test
    void drawForDealer_advancesToTurnWithDrawnTile() {
        List<TheMahjongTile> hand0 = chuurenHand();
        TheMahjongRound round = TheMahjongFixedDeal.builder()
                .handForSeat(0, hand0)
                .topOfWall(List.of(man(5)))
                .drawForDealer(true)
                .buildRound();

        assertEquals(TheMahjongRound.State.TURN, round.state());
        assertEquals(0, round.currentTurnSeat());
        assertTrue(round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn d
                && d.tile().equals(man(5)),
                "drawn tile should be the first topOfWall entry");
        assertEquals(14, round.players().get(0).currentHand().size());
    }

    @Test
    void buildMatch_inRoundStateMatchesPlayerCount() {
        TheMahjongMatch match = TheMahjongFixedDeal.builder()
                .playerCount(3)
                .tileSet(TheMahjongTileSet.standardSanma(false))
                .ruleSet(TheMahjongRuleSet.tenhouSanma())
                .buildMatch();
        assertEquals(TheMahjongMatch.State.IN_ROUND, match.state());
        assertEquals(3, match.playerCount());
        assertTrue(match.currentRound().isPresent());
        assertEquals(3, match.currentRound().get().players().size());
    }

    @Test
    void overReservingThrowsClearError() {
        // 5× M5 — only 4 copies in standardRiichi without red fives.
        List<TheMahjongTile> oversaturatedHand = new ArrayList<>();
        for (int i = 0; i < 5; i++) oversaturatedHand.add(man(5));
        for (int i = 0; i < 8; i++) oversaturatedHand.add(pin(1));
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> TheMahjongFixedDeal.builder()
                        .handForSeat(0, oversaturatedHand)
                        .buildRound());
        assertTrue(ex.getMessage().contains("not available"));
    }
}
