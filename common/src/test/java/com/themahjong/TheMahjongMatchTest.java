package com.themahjong;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TheMahjongMatchTest {

    @Test
    void newMatchHasNoCurrentRoundBeforeStart() {
        TheMahjongMatch match = TheMahjongMatch.defaults().validate();

        assertTrue(match.currentRound().isEmpty());
    }

    @Test
    void startCreatesInitialActiveRound() {
        TheMahjongMatch startedMatch = TheMahjongMatch.defaults().validate().startRound();

        assertEquals(TheMahjongMatch.State.IN_ROUND, startedMatch.state());
        assertTrue(startedMatch.currentRound().isPresent());
        assertEquals(TheMahjongRound.State.SETUP, startedMatch.currentRound().orElseThrow().state());
        assertEquals(4, startedMatch.currentRound().orElseThrow().players().size());
        assertEquals(70, startedMatch.currentRound().orElseThrow().liveWall().size());
        assertEquals(4, startedMatch.currentRound().orElseThrow().rinshanTiles().size());
    }

    @Test
    void shuffledStartUsesShuffledWall() {
        TheMahjongMatch deterministic = TheMahjongMatch.defaults().validate().startRound();
        TheMahjongMatch shuffled = TheMahjongMatch.defaults().validate().startRound(new Random(1234L));

        assertEquals(TheMahjongMatch.State.IN_ROUND, shuffled.state());
        assertTrue(shuffled.currentRound().isPresent());
        assertNotSame(deterministic.currentRound().orElseThrow().players().get(0).currentHand(), shuffled.currentRound().orElseThrow().players().get(0).currentHand());
        assertNotEquals(
                deterministic.currentRound().orElseThrow().players().get(0).currentHand(),
                shuffled.currentRound().orElseThrow().players().get(0).currentHand());
    }

    @Test
    void startRejectsMatchThatAlreadyStarted() {
        TheMahjongMatch startedMatch = TheMahjongMatch.defaults().validate().startRound();

        assertThrows(IllegalStateException.class, startedMatch::startRound);
    }

    @Test
    void witherMethodsReturnNewInstanceWithUpdatedValues() {
        TheMahjongMatch original = TheMahjongMatch.defaults();
        TheMahjongTileSet customTileSet = TheMahjongTileSet.standardRiichi(true);

        TheMahjongMatch result = original
                .withPlayerCount(3)
                .withStartingPoints(35000)
                .withTargetPoints(40000)
                .withRoundCount(1)
                .withTileSet(customTileSet);

        result.validate();

        assertNotSame(original, result);
        assertEquals(3, result.playerCount());
        assertEquals(35000, result.startingPoints());
        assertEquals(40000, result.targetPoints());
        assertEquals(1, result.roundCount());
        assertSame(customTileSet, result.tileSet());

        assertEquals(4, original.playerCount());
        assertEquals(30000, original.startingPoints());
    }

    @Test
    void startLeavesOriginalMatchUnchanged() {
        TheMahjongMatch before = TheMahjongMatch.defaults().validate();
        TheMahjongMatch after = before.startRound();

        assertNotSame(before, after);
        assertTrue(before.currentRound().isEmpty());
        assertEquals(TheMahjongMatch.State.NOT_STARTED, before.state());
        assertTrue(after.currentRound().isPresent());
        assertEquals(TheMahjongMatch.State.IN_ROUND, after.state());
    }

    @Test
    void shuffledStartProducesDifferentHandsWithDifferentSeeds() {
        TheMahjongMatch a = TheMahjongMatch.defaults().validate().startRound(new Random(1L));
        TheMahjongMatch b = TheMahjongMatch.defaults().validate().startRound(new Random(9999L));
        assertNotEquals(
                a.currentRound().orElseThrow().players().get(0).currentHand(),
                b.currentRound().orElseThrow().players().get(0).currentHand());
    }

    @Test
    void validateAcceptsStartedMatchWithMatchingPlayerCount() {
        TheMahjongMatch started = TheMahjongMatch.defaults().validate().startRound(new Random(1L));
        assertDoesNotThrow(started::validate);
    }

    @Test
    void validateRejectsStartedMatchWithMismatchedPlayerCount() {
        TheMahjongMatch started = TheMahjongMatch.defaults().validate().startRound(new Random(1L));
        TheMahjongRound round = started.currentRound().orElseThrow();
        TheMahjongMatch mismatched = new TheMahjongMatch(
                3,
                started.startingPoints(),
                started.targetPoints(),
                started.roundCount(),
                TheMahjongMatch.State.IN_ROUND,
                started.tileSet(),
                started.ruleSet(),
                List.of(),
                round);
        assertThrows(IllegalArgumentException.class, mismatched::validate);
    }

    @Test
    void equalsAndHashCode() {
        TheMahjongMatch a = TheMahjongMatch.defaults();
        TheMahjongMatch b = TheMahjongMatch.defaults();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalityDistinguishesByPlayerCount() {
        assertNotEquals(TheMahjongMatch.defaults(), TheMahjongMatch.defaults().withPlayerCount(3));
    }

    @Test
    void toStringContainsState() {
        assertTrue(TheMahjongMatch.defaults().toString().contains("NOT_STARTED"));
    }

    @Test
    void withTargetPointsRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> TheMahjongMatch.defaults().withTargetPoints(-1));
    }

    @Test
    void validateRejectsTargetPointsBelowStartingPoints() {
        TheMahjongMatch match = TheMahjongMatch.defaults()
                .withStartingPoints(30000)
                .withTargetPoints(25000);

        assertThrows(IllegalArgumentException.class, match::validate);
    }

    // -------------------------------------------------------------------------
    // advanceRound
    // -------------------------------------------------------------------------

    /** Builds a 4-player ended round where players have the given point totals. */
    private static TheMahjongRound endedRound(int p0, int p1, int p2, int p3) {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound started = TheMahjongRound.start(4, 30_000, wall);
        List<TheMahjongPlayer> players = new ArrayList<>();
        int[] pts = {p0, p1, p2, p3};
        for (int i = 0; i < 4; i++) {
            TheMahjongPlayer orig = started.players().get(i);
            players.add(new TheMahjongPlayer(pts[i], orig.riichiState(), orig.ippatsuEligible(),
                    orig.currentHand(), orig.melds(), orig.discards(), orig.temporaryFuritenTiles()));
        }
        return new TheMahjongRound(
                started.roundWind(), started.handNumber(), started.honba(), started.riichiSticks(),
                started.dealerSeat(), TheMahjongRound.State.ENDED, started.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                started.liveWall(), started.rinshanTiles(), started.doraIndicators(),
                started.uraDoraIndicators(), started.revealedDoraCount(), players, List.of());
    }

    /** Wraps an ended round in an IN_ROUND match using the given ruleSet. */
    private static TheMahjongMatch matchWithEndedRound(TheMahjongRound ended, TheMahjongRuleSet ruleSet) {
        return new TheMahjongMatch(4, 30_000, 30_000, 2,
                TheMahjongMatch.State.IN_ROUND, TheMahjongTileSet.standardRiichi(false),
                ruleSet, List.of(), ended);
    }

    @Test
    void advanceRound_requiresInRoundState() {
        TheMahjongMatch notStarted = TheMahjongMatch.defaults().validate();
        assertThrows(IllegalStateException.class,
                () -> notStarted.advanceRound(new Random(0), false, 0));
    }

    @Test
    void advanceRound_requiresEndedCurrentRound() {
        TheMahjongMatch inRound = TheMahjongMatch.defaults().validate().startRound(new Random(0));
        assertThrows(IllegalStateException.class,
                () -> inRound.advanceRound(new Random(0), false, 0));
    }

    @Test
    void advanceRound_bustEndsMatchWhenEnabled() {
        TheMahjongRound ended = endedRound(30_000, -1000, 30_000, 30_000);
        TheMahjongMatch match = matchWithEndedRound(ended, TheMahjongRuleSet.tenhou());

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
        assertEquals(1, result.completedRounds().size());
    }

    @Test
    void advanceRound_bustDoesNotEndMatchWhenDisabled() {
        TheMahjongRound ended = endedRound(30_000, -1000, 30_000, 30_000);
        TheMahjongMatch match = matchWithEndedRound(ended, TheMahjongRuleSet.wrc());

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
    }

    @Test
    void advanceRound_advancesDealer() {
        TheMahjongRound ended = endedRound(30_000, 30_000, 30_000, 30_000);
        TheMahjongMatch match = matchWithEndedRound(ended, TheMahjongRuleSet.wrc());

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
        assertEquals(1, result.currentRound().orElseThrow().dealerSeat());
    }

    @Test
    void advanceRound_renchanKeepsDealer() {
        TheMahjongRound ended = endedRound(30_000, 30_000, 30_000, 30_000);
        TheMahjongMatch match = matchWithEndedRound(ended, TheMahjongRuleSet.wrc());

        TheMahjongMatch result = match.advanceRound(new Random(0), true, 1);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
        assertEquals(0, result.currentRound().orElseThrow().dealerSeat());
        assertEquals(1, result.currentRound().orElseThrow().honba());
    }

    @Test
    void advanceRound_advancesWindAfterFullDealerRotation() {
        // East-4 (dealer=3) non-renchan → South-1 (dealer=0)
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound east4 = TheMahjongRound.start(4, 30_000, wall,
                TheMahjongTile.Wind.EAST, 4, 0, 0, 3);
        List<TheMahjongPlayer> players = new ArrayList<>(east4.players());
        TheMahjongRound endedEast4 = new TheMahjongRound(
                east4.roundWind(), east4.handNumber(), east4.honba(), east4.riichiSticks(),
                east4.dealerSeat(), TheMahjongRound.State.ENDED, east4.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                east4.liveWall(), east4.rinshanTiles(), east4.doraIndicators(),
                east4.uraDoraIndicators(), east4.revealedDoraCount(), players, List.of());
        TheMahjongMatch match = new TheMahjongMatch(4, 30_000, 30_000, 2,
                TheMahjongMatch.State.IN_ROUND, TheMahjongTileSet.standardRiichi(false),
                TheMahjongRuleSet.wrc(), List.of(), endedEast4);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
        assertEquals(TheMahjongTile.Wind.SOUTH, result.currentRound().orElseThrow().roundWind());
        assertEquals(1, result.currentRound().orElseThrow().handNumber());
        assertEquals(0, result.currentRound().orElseThrow().dealerSeat());
    }

    @Test
    void advanceRound_roundCountEndsMatch() {
        // South-4 (dealer=3) non-renchan would start West-1, but roundCount=2 → ENDED
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound south4 = TheMahjongRound.start(4, 30_000, wall,
                TheMahjongTile.Wind.SOUTH, 4, 0, 0, 3);
        List<TheMahjongPlayer> players = new ArrayList<>(south4.players());
        TheMahjongRound endedSouth4 = new TheMahjongRound(
                south4.roundWind(), south4.handNumber(), south4.honba(), south4.riichiSticks(),
                south4.dealerSeat(), TheMahjongRound.State.ENDED, south4.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                south4.liveWall(), south4.rinshanTiles(), south4.doraIndicators(),
                south4.uraDoraIndicators(), south4.revealedDoraCount(), players, List.of());
        TheMahjongMatch match = new TheMahjongMatch(4, 30_000, 30_000, 2,
                TheMahjongMatch.State.IN_ROUND, TheMahjongTileSet.standardRiichi(false),
                TheMahjongRuleSet.wrc(), List.of(), endedSouth4);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    @Test
    void advanceRound_carriesOverPlayerPoints() {
        TheMahjongRound ended = endedRound(35_000, 28_000, 22_000, 15_000);
        TheMahjongMatch match = matchWithEndedRound(ended, TheMahjongRuleSet.wrc());

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        List<TheMahjongPlayer> nextPlayers = result.currentRound().orElseThrow().players();
        assertEquals(35_000, nextPlayers.get(0).points());
        assertEquals(28_000, nextPlayers.get(1).points());
        assertEquals(22_000, nextPlayers.get(2).points());
        assertEquals(15_000, nextPlayers.get(3).points());
    }

    @Test
    void advanceRound_carriesOverRiichiSticks() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound base = TheMahjongRound.start(4, 30_000, wall);
        TheMahjongRound endedWithSticks = new TheMahjongRound(
                base.roundWind(), base.handNumber(), base.honba(), 3,
                base.dealerSeat(), TheMahjongRound.State.ENDED, base.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                base.liveWall(), base.rinshanTiles(), base.doraIndicators(),
                base.uraDoraIndicators(), base.revealedDoraCount(), base.players(), List.of());
        TheMahjongMatch match = matchWithEndedRound(endedWithSticks, TheMahjongRuleSet.wrc());

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(3, result.currentRound().orElseThrow().riichiSticks());
    }

    // -------------------------------------------------------------------------
    // applyFinalDeposits
    // -------------------------------------------------------------------------

    /** Builds a 4-player ended round with the given riichi stick count and player points. */
    private static TheMahjongRound endedRoundWithSticks(int sticks, int p0, int p1, int p2, int p3) {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound started = TheMahjongRound.start(4, 30_000, wall);
        List<TheMahjongPlayer> players = new ArrayList<>();
        int[] pts = {p0, p1, p2, p3};
        for (int i = 0; i < 4; i++) {
            TheMahjongPlayer orig = started.players().get(i);
            players.add(new TheMahjongPlayer(pts[i], orig.riichiState(), orig.ippatsuEligible(),
                    orig.currentHand(), orig.melds(), orig.discards(), orig.temporaryFuritenTiles()));
        }
        return new TheMahjongRound(
                started.roundWind(), started.handNumber(), started.honba(), sticks,
                started.dealerSeat(), TheMahjongRound.State.ENDED, started.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                started.liveWall(), started.rinshanTiles(), started.doraIndicators(),
                started.uraDoraIndicators(), started.revealedDoraCount(), players, List.of());
    }

    private static TheMahjongMatch endedMatch(TheMahjongRound round, TheMahjongRuleSet ruleSet) {
        return new TheMahjongMatch(4, 30_000, 30_000, 2,
                TheMahjongMatch.State.ENDED, TheMahjongTileSet.standardRiichi(false),
                ruleSet, List.of(), round);
    }

    @Test
    void applyFinalDeposits_requiresEndedState() {
        TheMahjongMatch inRound = TheMahjongMatch.defaults().validate().startRound(new Random(0));
        assertThrows(IllegalStateException.class, inRound::applyFinalDeposits);
    }

    @Test
    void applyFinalDeposits_awardsSticksToFirstPlace_whenEnabled() {
        TheMahjongRound ended = endedRoundWithSticks(3, 25_000, 35_000, 20_000, 40_000);
        TheMahjongMatch match = endedMatch(ended, TheMahjongRuleSet.tenhou());

        TheMahjongMatch result = match.applyFinalDeposits();

        assertEquals(43_000, result.currentRound().orElseThrow().players().get(3).points());
        assertEquals(0, result.currentRound().orElseThrow().riichiSticks());
    }

    @Test
    void applyFinalDeposits_noOpWhenRuleDisabled() {
        TheMahjongRound ended = endedRoundWithSticks(2, 25_000, 35_000, 20_000, 40_000);
        TheMahjongMatch match = endedMatch(ended, TheMahjongRuleSet.wrc());

        TheMahjongMatch result = match.applyFinalDeposits();

        assertSame(match, result);
    }

    @Test
    void applyFinalDeposits_noOpWhenNoSticks() {
        TheMahjongRound ended = endedRoundWithSticks(0, 25_000, 35_000, 20_000, 40_000);
        TheMahjongMatch match = endedMatch(ended, TheMahjongRuleSet.tenhou());

        TheMahjongMatch result = match.applyFinalDeposits();

        assertSame(match, result);
    }

    @Test
    void applyFinalDeposits_tiebreakerFavorsLowerSeat() {
        // seats 1 and 3 both have 40_000; seat 1 (lower) should win
        TheMahjongRound ended = endedRoundWithSticks(2, 25_000, 40_000, 20_000, 40_000);
        TheMahjongMatch match = endedMatch(ended, TheMahjongRuleSet.tenhou());

        TheMahjongMatch result = match.applyFinalDeposits();

        assertEquals(42_000, result.currentRound().orElseThrow().players().get(1).points());
        assertEquals(40_000, result.currentRound().orElseThrow().players().get(3).points());
    }

    @Test
    void applyFinalDeposits_isIdempotentAfterZeroingSticks() {
        TheMahjongRound ended = endedRoundWithSticks(1, 25_000, 35_000, 20_000, 40_000);
        TheMahjongMatch match = endedMatch(ended, TheMahjongRuleSet.tenhou());

        TheMahjongMatch once = match.applyFinalDeposits();
        TheMahjongMatch twice = once.applyFinalDeposits();

        assertSame(once, twice);
        assertEquals(41_000, twice.currentRound().orElseThrow().players().get(3).points());
    }

    // -------------------------------------------------------------------------
    // isAllLast / isAgariYameEligible
    // -------------------------------------------------------------------------

    /**
     * Builds an IN_ROUND match with an ended round at the given wind/hand/dealer and player points.
     * roundCount=2 (East+South). playerCount=4.
     */
    private static TheMahjongMatch inRoundMatchAt(
            TheMahjongTile.Wind wind, int handNumber, int dealerSeat,
            boolean roundEnded, TheMahjongRuleSet ruleSet, int p0, int p1, int p2, int p3) {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardRiichi(false).createOrderedWall();
        TheMahjongRound base = TheMahjongRound.start(4, 30_000, wall, wind, handNumber, 0, 0, dealerSeat);
        List<TheMahjongPlayer> players = new ArrayList<>();
        int[] pts = {p0, p1, p2, p3};
        for (int i = 0; i < 4; i++) {
            TheMahjongPlayer orig = base.players().get(i);
            players.add(new TheMahjongPlayer(pts[i], orig.riichiState(), orig.ippatsuEligible(),
                    orig.currentHand(), orig.melds(), orig.discards(), orig.temporaryFuritenTiles()));
        }
        TheMahjongRound.State roundState = roundEnded ? TheMahjongRound.State.ENDED : TheMahjongRound.State.SETUP;
        TheMahjongRound round = new TheMahjongRound(
                base.roundWind(), base.handNumber(), base.honba(), base.riichiSticks(),
                base.dealerSeat(), roundState, base.currentTurnSeat(),
                -1, TheMahjongRound.ActiveTile.none(),
                base.liveWall(), base.rinshanTiles(), base.doraIndicators(),
                base.uraDoraIndicators(), base.revealedDoraCount(), players, List.of());
        return new TheMahjongMatch(4, 30_000, 30_000, 2,
                TheMahjongMatch.State.IN_ROUND, TheMahjongTileSet.standardRiichi(false),
                ruleSet, List.of(), round);
    }

    @Test
    void isAllLast_trueOnLastHandOfLastWind() {
        // South-4, dealer=3: next non-renchan → dealer=0 → West → ordinal 2 >= roundCount 2
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, TheMahjongRuleSet.wrc(), 30_000, 30_000, 30_000, 30_000);
        assertTrue(match.isAllLast());
    }

    @Test
    void isAllLast_falseOnMiddleHand() {
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.EAST, 2, 1,
                true, TheMahjongRuleSet.wrc(), 30_000, 30_000, 30_000, 30_000);
        assertFalse(match.isAllLast());
    }

    @Test
    void isAllLast_falseOnLastHandOfFirstWind() {
        // East-4, dealer=3: next non-renchan → dealer=0 → South → ordinal 1 < roundCount 2
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.EAST, 4, 3,
                true, TheMahjongRuleSet.wrc(), 30_000, 30_000, 30_000, 30_000);
        assertFalse(match.isAllLast());
    }

    @Test
    void isAllLast_falseWhenNotInRound() {
        assertFalse(TheMahjongMatch.defaults().validate().isAllLast());
    }

    @Test
    void isAgariYameEligible_trueWhenDealerLeadsInAllLast() {
        // South-4, dealer=3 (seat 3) has most points, round ended, rule enabled
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, TheMahjongRuleSet.tenhou(), 25_000, 28_000, 20_000, 40_000);
        assertTrue(match.isAgariYameEligible());
    }

    @Test
    void isAgariYameEligible_falseWhenRuleDisabled() {
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, TheMahjongRuleSet.wrc(), 25_000, 28_000, 20_000, 40_000);
        assertFalse(match.isAgariYameEligible());
    }

    @Test
    void isAgariYameEligible_falseWhenNotAllLast() {
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.EAST, 2, 1,
                true, TheMahjongRuleSet.tenhou(), 25_000, 40_000, 20_000, 28_000);
        assertFalse(match.isAgariYameEligible());
    }

    @Test
    void isAgariYameEligible_falseWhenDealerNotLeading() {
        // dealer=3 has 28_000 but seat 1 has 35_000
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, TheMahjongRuleSet.tenhou(), 25_000, 35_000, 20_000, 28_000);
        assertFalse(match.isAgariYameEligible());
    }

    @Test
    void isAgariYameEligible_falseWhenDealerTied() {
        // dealer=3 tied with seat 1 — not strictly leading
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, TheMahjongRuleSet.tenhou(), 25_000, 40_000, 20_000, 40_000);
        assertFalse(match.isAgariYameEligible());
    }

    @Test
    void isAgariYameEligible_falseWhenRoundNotEnded() {
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                false, TheMahjongRuleSet.tenhou(), 25_000, 28_000, 20_000, 40_000);
        assertFalse(match.isAgariYameEligible());
    }

    @Test
    void agariYame_endMatchWhenDealerPassesRenchanFalse() {
        // Dealer wins in all-last but passes renchan=false → match ends
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, TheMahjongRuleSet.tenhou(), 25_000, 28_000, 20_000, 40_000);
        assertTrue(match.isAgariYameEligible());

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    // -------------------------------------------------------------------------
    // suddenDeathRound
    // -------------------------------------------------------------------------

    /** Ruleset with only suddenDeathRound enabled (all other rules at WRC defaults). */
    private static TheMahjongRuleSet suddenDeathOnly() {
        return new TheMahjongRuleSet(
                true, false, true, true,
                false, false, false, false, false, false, false,
                true, // suddenDeathRound
                false,
                true,
                true,
                false, false, false, true, true);
    }

    /** IN_ROUND match at South-4 (dealer=3) with given points and sudden-death ruleset. */
    private static TheMahjongMatch south4Match(TheMahjongRuleSet ruleSet, int p0, int p1, int p2, int p3) {
        return inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3, true, ruleSet, p0, p1, p2, p3);
    }

    @Test
    void suddenDeath_continuesIntoWestWhenNobodyAtTarget() {
        TheMahjongMatch match = south4Match(suddenDeathOnly(), 25_000, 28_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
        assertEquals(TheMahjongTile.Wind.WEST, result.currentRound().orElseThrow().roundWind());
        assertEquals(1, result.currentRound().orElseThrow().handNumber());
    }

    @Test
    void suddenDeath_endsWhenSomeoneAtTargetAtScheduleBoundary() {
        // Seat 0 has 31_000 >= targetPoints (30_000) at South-4
        TheMahjongMatch match = south4Match(suddenDeathOnly(), 31_000, 28_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    @Test
    void suddenDeath_endsAfterExtraRoundWin() {
        // West-2 ended, seat 1 now has 32_000 >= target
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.WEST, 2, 1,
                true, suddenDeathOnly(), 25_000, 32_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    @Test
    void suddenDeath_continuesInExtraRoundWhenNobodyAtTarget() {
        // West-2 ended, no one at 30_000
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.WEST, 2, 1,
                true, suddenDeathOnly(), 25_000, 28_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.IN_ROUND, result.state());
    }

    @Test
    void suddenDeath_endsAfterRenchanInExtraRound() {
        // West-2 renchan (dealer won), seat 1 now at 30_000 >= target
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.WEST, 2, 1,
                true, suddenDeathOnly(), 25_000, 30_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), true, 1);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    @Test
    void suddenDeath_endsAfterNorthExhausted() {
        // North-4 ended, no one at target → all winds exhausted → ENDED
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.NORTH, 4, 3,
                true, suddenDeathOnly(), 25_000, 28_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    @Test
    void suddenDeath_disabledEndsAtRoundCount() {
        // No sudden death: South-4 ends the match even with no player at target
        TheMahjongMatch match = south4Match(TheMahjongRuleSet.wrc(), 25_000, 28_000, 20_000, 27_000);

        TheMahjongMatch result = match.advanceRound(new Random(0), false, 0);

        assertEquals(TheMahjongMatch.State.ENDED, result.state());
    }

    @Test
    void isAllLast_falseInSuddenDeathExtraRound() {
        // West-4 is in the extra rounds when suddenDeath=true — not "all-last"
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.WEST, 4, 3,
                true, suddenDeathOnly(), 25_000, 28_000, 20_000, 27_000);
        assertFalse(match.isAllLast());
    }

    @Test
    void isAllLast_trueOnLastScheduledHandWithSuddenDeathEnabled() {
        // South-4 is still all-last even with suddenDeath enabled
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.SOUTH, 4, 3,
                true, suddenDeathOnly(), 25_000, 28_000, 20_000, 27_000);
        assertTrue(match.isAllLast());
    }

    @Test
    void isAgariYameEligible_falseInSuddenDeathExtraRound() {
        // West-4: in extra rounds, agari-yame does not apply
        TheMahjongMatch match = inRoundMatchAt(TheMahjongTile.Wind.WEST, 4, 3,
                true, new TheMahjongRuleSet(true, false, true, true, false, false, false, false,
                        false, false, true, true, false, true, true, false, false, false, true, true),
                25_000, 28_000, 20_000, 40_000);
        assertFalse(match.isAgariYameEligible());
    }
}
