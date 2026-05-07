package com.themahjong;

import com.themahjong.yaku.WinCalculator;
import com.themahjong.yaku.WinContext;
import com.themahjong.yaku.WinResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sanma (3-player) coverage: deck shape, kita action/scoring, dora wrap, tsumo distribution. */
class TheMahjongSanmaTest {

    // -------------------------------------------------------------------------
    // Deck
    // -------------------------------------------------------------------------

    @Test
    void standardSanma_omitsManzu2Through8() {
        TheMahjongTileSet sanma = TheMahjongTileSet.standardSanma(false);
        for (int rank = 2; rank <= 8; rank++) {
            TheMahjongTile mid = new TheMahjongTile(TheMahjongTile.Suit.MANZU, rank, false);
            assertFalse(sanma.copiesPerTile().containsKey(mid),
                    "sanma deck must not contain m" + rank);
        }
        assertTrue(sanma.copiesPerTile().containsKey(
                new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false)));
        assertTrue(sanma.copiesPerTile().containsKey(
                new TheMahjongTile(TheMahjongTile.Suit.MANZU, 9, false)));
        // 108 tiles total: 136 minus 7 missing manzu ranks * 4 copies.
        int total = sanma.copiesPerTile().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(108, total);
    }

    @Test
    void standardSanma_redFivesNeverInManzu() {
        TheMahjongTileSet withReds = TheMahjongTileSet.standardSanma(true);
        TheMahjongTile redM5 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 5, true);
        assertFalse(withReds.copiesPerTile().containsKey(redM5));
        // Pinzu and souzu still get one red five each.
        TheMahjongTile redP5 = new TheMahjongTile(TheMahjongTile.Suit.PINZU, 5, true);
        TheMahjongTile redS5 = new TheMahjongTile(TheMahjongTile.Suit.SOUZU, 5, true);
        assertEquals(1, withReds.copiesPerTile().get(redP5));
        assertEquals(1, withReds.copiesPerTile().get(redS5));
    }

    // -------------------------------------------------------------------------
    // Match factories
    // -------------------------------------------------------------------------

    @Test
    void defaultTenhouSanma_hasCorrectShape() {
        TheMahjongMatch match = TheMahjongMatch.defaultTenhouSanma();
        assertEquals(3, match.playerCount());
        assertEquals(35_000, match.startingPoints());
        assertEquals(40_000, match.targetPoints());
        assertTrue(match.ruleSet().chiDisabled());
        assertTrue(match.ruleSet().kitaCountsAsDora());
        assertTrue(match.ruleSet().tsumoLoss());
    }

    @Test
    void start_3playerUsesEightRinshanTiles() {
        List<TheMahjongTile> wall = TheMahjongTileSet.standardSanma(false).createOrderedWall();
        TheMahjongRound r = TheMahjongRound.start(3, 35000, wall);
        assertEquals(8, r.rinshanTiles().size());
    }

    // -------------------------------------------------------------------------
    // Kita action
    // -------------------------------------------------------------------------

    @Test
    void declareKita_incrementsKitaCountAndTransitionsToKitaWindow() {
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        r = r.draw();
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);

        TheMahjongRound after = r.declareKita(north);
        assertEquals(TheMahjongRound.State.KITA_WINDOW, after.state());
        assertEquals(1, after.players().get(after.currentTurnSeat()).kitaCount());
        assertTrue(after.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKita);
    }

    @Test
    void declareKita_clearsOtherPlayersIppatsuEligibility() {
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        // Manually set seat 1 ippatsu-eligible so we can detect the clearing.
        TheMahjongPlayer p1 = r.players().get(1);
        TheMahjongPlayer ippatsuP1 = new TheMahjongPlayer(
                p1.points(), p1.riichiState(), true,
                p1.currentHand(), p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten(), p1.kitaCount());
        List<TheMahjongPlayer> newPlayers = new ArrayList<>(r.players());
        newPlayers.set(1, ippatsuP1);
        TheMahjongRound r2 = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), newPlayers, List.of());
        r2 = r2.draw();
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);
        TheMahjongRound after = r2.declareKita(north);
        assertFalse(after.players().get(1).ippatsuEligible());
    }

    @Test
    void skipKitaClaims_advancesToRinshanDraw() {
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        r = r.draw();
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);
        TheMahjongRound afterSkip = r.declareKita(north).skipKitaClaims();
        assertEquals(TheMahjongRound.State.RINSHAN_DRAW, afterSkip.state());
        // Replacement draw comes from rinshan pile, not live wall.
        TheMahjongTile firstRinshan = afterSkip.rinshanTiles().get(0);
        TheMahjongRound afterDraw = afterSkip.draw();
        assertEquals(TheMahjongRound.State.TURN, afterDraw.state());
        assertTrue(afterDraw.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn);
        assertEquals(firstRinshan, ((TheMahjongRound.ActiveTile.Drawn) afterDraw.activeTile()).tile());
    }

    @Test
    void declareWin_acceptsKitaWindowState() {
        // Set up a round in KITA_WINDOW with seat 1 tenpai-on-north and able to ron the kita.
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);
        // Build a tenpai-on-north hand for seat 1: 13 tiles waiting tanki on north.
        List<TheMahjongTile> hand = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        hand.add(north);
        TheMahjongPlayer seat1 = new TheMahjongPlayer(
                35000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(), List.of(), List.of(), false, 0);
        List<TheMahjongPlayer> players = new ArrayList<>(r.players());
        players.set(1, seat1);
        TheMahjongRound seeded = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), players, List.of());
        TheMahjongRound kitaWindow = seeded.draw().declareKita(north);
        assertEquals(TheMahjongRound.State.KITA_WINDOW, kitaWindow.state());

        // declareWin from KITA_WINDOW with seat 1 as winner, dealer (seat 0) as fromWho.
        WinResult fakeResult = new WinResult(
                List.of(com.themahjong.yaku.NonYakuman.RIICHI), List.of(),
                1, 30, 0, List.of(-3000, 3000, 0));
        TheMahjongRound ended = kitaWindow.declareWin(1, 0, fakeResult);
        assertEquals(TheMahjongRound.State.ENDED, ended.state());
        assertEquals(35000 - 3000, ended.players().get(0).points());
        assertEquals(35000 + 3000, ended.players().get(1).points());
    }

    // -------------------------------------------------------------------------
    // chiDisabled
    // -------------------------------------------------------------------------

    @Test
    void claimChi_blockedWhenChiDisabled() {
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        r = r.draw();
        // Seat 0 discards; seat 1 (kamicha) attempts chi.
        TheMahjongTile discard = r.players().get(0).currentHand().get(0);
        TheMahjongRound cw = r.discard(discard);
        assertThrows(IllegalStateException.class,
                () -> cw.claimChi(1, List.of(discard, discard), TheMahjongRuleSet.tenhouSanma()));
    }

    // -------------------------------------------------------------------------
    // Tsumo distribution: tsumo-loss vs bisection
    // -------------------------------------------------------------------------

    @Test
    void tsumoLoss_winnerEqualsSumOfTwoPayers() {
        WinResult r = nonDealerTsumoResult(TheMahjongRuleSet.tenhouSanma(), /*tsumoLoss*/ true);
        // Winner gets exactly the sum of dealer + other non-dealer payments (no missing 4th seat).
        int dealerPay = -r.pointDeltas().get(0);
        int otherPay  = -r.pointDeltas().get(2);
        assertEquals(dealerPay + otherPay, r.pointDeltas().get(1));
    }

    @Test
    void bisection_winnerCollectsRedistributedShareToo() {
        WinResult r = nonDealerTsumoResult(bisectionRules(), /*tsumoLoss*/ false);
        int dealerPay = -r.pointDeltas().get(0);
        int otherPay  = -r.pointDeltas().get(2);
        assertEquals(dealerPay + otherPay, r.pointDeltas().get(1));
    }

    @Test
    void tsumoLoss_andBisection_produceDifferentDeltas() {
        WinResult tsumoLoss = nonDealerTsumoResult(TheMahjongRuleSet.tenhouSanma(), true);
        WinResult bisection = nonDealerTsumoResult(bisectionRules(), false);
        // Bisection winner should collect strictly more than tsumo-loss winner (north share added).
        assertTrue(bisection.pointDeltas().get(1) > tsumoLoss.pointDeltas().get(1),
                "bisection " + bisection.pointDeltas() + " vs tsumo-loss " + tsumoLoss.pointDeltas());
        // Each remaining payer in bisection pays more than in tsumo-loss.
        assertTrue(bisection.pointDeltas().get(0) < tsumoLoss.pointDeltas().get(0));
        assertTrue(bisection.pointDeltas().get(2) < tsumoLoss.pointDeltas().get(2));
    }

    @Test
    void kitaCountsAsDora_addsToWinnerDoraTotal() {
        // A simple 1-han hand with 3 kita = 4 han total. Use closed riichi-style hand.
        // Build a winning hand: 123p 456p 789p 234s + 1m1m (pair on 1m, ron on 1m).
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        List<TheMahjongTile> winnerTiles = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++)
            winnerTiles.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            winnerTiles.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        winnerTiles.add(m1);
        winnerTiles.add(m1); // 14 tiles total

        var decomps = com.themahjong.yaku.HandShape.decompose(winnerTiles, List.of());
        assertFalse(decomps.isEmpty());

        // No dora from indicators (use a tile that doesn't appear in hand).
        TheMahjongTile irrelevantIndicator = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false);

        WinContext ctx = new WinContext(
                WinContext.WinType.RON, /*dealer*/ false,
                /*uninterruptedFirstRound*/ false,
                TheMahjongPlayer.RiichiState.RIICHI, /*ippatsu*/ false,
                m1, TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                /*lastTile*/ false, /*rinshanDraw*/ false);
        WinResult withKita = WinCalculator.calculateBest(
                decomps, ctx, winnerTiles, List.of(irrelevantIndicator), List.of(),
                3, /*winnerSeat*/ 1, /*loserSeat*/ 0, /*dealerSeat*/ 0,
                0, 0, TheMahjongRuleSet.tenhouSanma(), /*winnerKitaCount*/ 3);
        WinResult withoutKita = WinCalculator.calculateBest(
                decomps, ctx, winnerTiles, List.of(irrelevantIndicator), List.of(),
                3, 1, 0, 0, 0, 0, TheMahjongRuleSet.tenhouSanma(), 0);
        assertEquals(withoutKita.doraCount() + 3, withKita.doraCount());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds a 3-player round at SETUP state where dealer (seat 0) holds at least one north tile. */
    private static TheMahjongRound sanmaRoundWithNorthInDealerHand() {
        TheMahjongTileSet sanma = TheMahjongTileSet.standardSanma(false);
        List<TheMahjongTile> wall = sanma.createOrderedWall();
        TheMahjongRound r = TheMahjongRound.start(3, 35000, wall);
        // Seed dealer's hand to include a north (rank 4 wind). Replace seat 0 hand wholesale.
        List<TheMahjongTile> dealerHand = new ArrayList<>();
        dealerHand.add(new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false));
        for (int rank = 1; rank <= 9; rank++)
            dealerHand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            dealerHand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        TheMahjongPlayer dealer = new TheMahjongPlayer(
                35000, TheMahjongPlayer.RiichiState.NONE, false,
                dealerHand, List.of(), List.of(), List.of(), false, 0);
        List<TheMahjongPlayer> ps = new ArrayList<>(r.players());
        ps.set(0, dealer);
        return new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), ps, List.of());
    }

    /** Computes a non-dealer tsumo result for a 3-player game. Winner = seat 1, dealer = seat 0. */
    private static WinResult nonDealerTsumoResult(TheMahjongRuleSet rules, boolean expectTsumoLoss) {
        assertEquals(expectTsumoLoss, rules.tsumoLoss());
        // Build a winning hand: 123p 456p 789p 234s + 1m1m.
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        List<TheMahjongTile> winnerTiles = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++)
            winnerTiles.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            winnerTiles.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        winnerTiles.add(m1); winnerTiles.add(m1);
        var decomps = com.themahjong.yaku.HandShape.decompose(winnerTiles, List.of());
        TheMahjongTile irrelevant = new TheMahjongTile(TheMahjongTile.Suit.DRAGON, 1, false);
        WinContext ctx = new WinContext(
                WinContext.WinType.TSUMO, /*dealer*/ false,
                /*uninterruptedFirstRound*/ false,
                TheMahjongPlayer.RiichiState.RIICHI, /*ippatsu*/ false,
                m1, TheMahjongTile.Wind.SOUTH, TheMahjongTile.Wind.EAST,
                false, false);
        WinResult result = WinCalculator.calculateBest(
                decomps, ctx, winnerTiles, List.of(irrelevant), List.of(),
                3, /*winnerSeat*/ 1, /*loserSeat*/ 1, /*dealerSeat*/ 0,
                0, 0, rules, 0);
        assertNotNull(result);
        return result;
    }

    /** Returns a sanma ruleset with tsumoLoss=false (bisection mode). */
    private static TheMahjongRuleSet bisectionRules() {
        TheMahjongRuleSet base = TheMahjongRuleSet.tenhouSanma();
        return new TheMahjongRuleSet(
                base.kiriageMangan(), base.doubleWindPairFu4(), base.renhouAllowed(),
                base.paoOnSuukantsu(), base.abortiveDrawsAllowed(), base.nagashiManganAllowed(),
                base.doubleRonAllowed(), base.bustingEndsGame(), base.riichiRequires1000Points(),
                base.depositsToFirstAtEnd(), base.agariYameAllowed(), base.suddenDeathRound(),
                base.openKanDoraDelayedReveal(), base.kuikaeForbidden(), base.strictRiichiAnkan(),
                base.chiDisabled(), base.kitaCountsAsDora(), /*tsumoLoss*/ false,
                base.kitaDeclineCausesFuriten(), base.sanchahouAbortive());
    }

    @Test
    void sanmaRound_holdsKitaCountAcrossDrawAndDiscard() {
        // After kita+rinshan-draw+discard, the kitaCount should still be 1.
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);
        TheMahjongRound afterDiscard = r.draw().declareKita(north).skipKitaClaims().draw()
                .discard(new TheMahjongTile(TheMahjongTile.Suit.PINZU, 1, false));
        assertEquals(1, afterDiscard.players().get(0).kitaCount());
    }

    // -------------------------------------------------------------------------
    // Kita-decline furiten: Tenhou (causes furiten) vs Mahjong Soul (exception)
    // -------------------------------------------------------------------------

    @Test
    void declineRon_onKita_underTenhouSanma_addsToTemporaryFuriten() {
        TheMahjongRound r = sanmaRoundWithSeat1TenpaiOnNorth();
        TheMahjongRound kitaWindow = r.draw().declareKita(
                new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false));
        TheMahjongRound after = kitaWindow.declineRon(1, TheMahjongRuleSet.tenhouSanma());
        assertEquals(1, after.players().get(1).temporaryFuritenTiles().size());
        assertFalse(after.players().get(1).riichiPermanentFuriten()); // not in riichi
    }

    @Test
    void declineRon_onKita_underMahjongSoulSanma_doesNotAddFuriten() {
        TheMahjongRound r = sanmaRoundWithSeat1TenpaiOnNorth();
        TheMahjongRound kitaWindow = r.draw().declareKita(
                new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false));
        TheMahjongRound after = kitaWindow.declineRon(1, TheMahjongRuleSet.mahjongSoulSanma());
        assertTrue(after.players().get(1).temporaryFuritenTiles().isEmpty());
        assertFalse(after.players().get(1).riichiPermanentFuriten());
    }

    @Test
    void declineRon_onKita_inRiichi_underTenhouSanma_setsRiichiPermanentFuriten() {
        TheMahjongRound r = sanmaRoundWithSeat1TenpaiOnNorth();
        // Force seat 1 into riichi.
        TheMahjongPlayer p1 = r.players().get(1);
        TheMahjongPlayer riichiP1 = new TheMahjongPlayer(
                p1.points(), TheMahjongPlayer.RiichiState.RIICHI, p1.ippatsuEligible(),
                p1.currentHand(), p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten(), p1.kitaCount());
        List<TheMahjongPlayer> ps = new ArrayList<>(r.players());
        ps.set(1, riichiP1);
        TheMahjongRound seeded = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), ps, List.of());
        TheMahjongRound kitaWindow = seeded.draw().declareKita(
                new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false));
        TheMahjongRound after = kitaWindow.declineRon(1, TheMahjongRuleSet.tenhouSanma());
        assertTrue(after.players().get(1).riichiPermanentFuriten());
    }

    @Test
    void declineRon_onKita_inRiichi_underMahjongSoulSanma_doesNotSetRiichiPermanentFuriten() {
        TheMahjongRound r = sanmaRoundWithSeat1TenpaiOnNorth();
        TheMahjongPlayer p1 = r.players().get(1);
        TheMahjongPlayer riichiP1 = new TheMahjongPlayer(
                p1.points(), TheMahjongPlayer.RiichiState.RIICHI, p1.ippatsuEligible(),
                p1.currentHand(), p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten(), p1.kitaCount());
        List<TheMahjongPlayer> ps = new ArrayList<>(r.players());
        ps.set(1, riichiP1);
        TheMahjongRound seeded = new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), ps, List.of());
        TheMahjongRound kitaWindow = seeded.draw().declareKita(
                new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false));
        TheMahjongRound after = kitaWindow.declineRon(1, TheMahjongRuleSet.mahjongSoulSanma());
        assertFalse(after.players().get(1).riichiPermanentFuriten());
        assertTrue(after.players().get(1).temporaryFuritenTiles().isEmpty());
    }

    @Test
    void declineRon_onHeldDiscard_unaffectedByKitaDeclineFuritenFlag() {
        // Regular ron-decline path must still apply furiten under both sanma rulesets.
        TheMahjongRound r = sanmaRoundWithSeat1TenpaiOnNorth();
        // Seat 0 (dealer) discards north — exposing it as a HeldDiscard, not HeldKita.
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);
        TheMahjongRound cw = r.draw().discard(north);
        TheMahjongRound afterMs = cw.declineRon(1, TheMahjongRuleSet.mahjongSoulSanma());
        assertEquals(1, afterMs.players().get(1).temporaryFuritenTiles().size());
        TheMahjongRound afterTenhou = cw.declineRon(1, TheMahjongRuleSet.tenhouSanma());
        assertEquals(1, afterTenhou.players().get(1).temporaryFuritenTiles().size());
    }

    /** Sanma round at SETUP with seat 0 holding a north and seat 1 tenpai-tanki on north. */
    private static TheMahjongRound sanmaRoundWithSeat1TenpaiOnNorth() {
        TheMahjongRound r = sanmaRoundWithNorthInDealerHand();
        TheMahjongTile north = new TheMahjongTile(TheMahjongTile.Suit.WIND, 4, false);
        List<TheMahjongTile> hand = new ArrayList<>();
        for (int rank = 1; rank <= 9; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 2; rank <= 4; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        hand.add(north);
        TheMahjongPlayer seat1 = new TheMahjongPlayer(
                35000, TheMahjongPlayer.RiichiState.NONE, false,
                hand, List.of(), List.of(), List.of(), false, 0);
        List<TheMahjongPlayer> ps = new ArrayList<>(r.players());
        ps.set(1, seat1);
        return new TheMahjongRound(
                r.roundWind(), r.handNumber(), r.honba(), r.riichiSticks(), r.dealerSeat(),
                r.state(), r.currentTurnSeat(), r.claimSourceSeat().orElse(-1),
                r.activeTile(), r.liveWall(), r.rinshanTiles(), r.doraIndicators(),
                r.uraDoraIndicators(), r.revealedDoraCount(), ps, List.of());
    }

    @Test
    void claimChi_backwardCompatOverloadStillExists() {
        // Existing 2-arg claimChi delegates to the 3-arg version with WRC rules (chi enabled).
        // This is a smoke test that the overload compiles and runs without throwing the chiDisabled error.
        TheMahjongTileSet ts = TheMahjongTileSet.standardRiichi(false);
        TheMahjongRound r = TheMahjongRound.start(4, 25000, ts.createOrderedWall()).draw();
        // Discard m1, seat 1 chis with m2/m3 from hand.
        TheMahjongTile m1 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 1, false);
        TheMahjongRound cw = r.discard(m1);
        // Seed seat 1 with m2,m3 in hand.
        TheMahjongTile m2 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 2, false);
        TheMahjongTile m3 = new TheMahjongTile(TheMahjongTile.Suit.MANZU, 3, false);
        TheMahjongPlayer p1 = cw.players().get(1);
        List<TheMahjongTile> hand = new ArrayList<>();
        hand.add(m2); hand.add(m3);
        for (int rank = 4; rank <= 9; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.PINZU, rank, false));
        for (int rank = 1; rank <= 4; rank++)
            hand.add(new TheMahjongTile(TheMahjongTile.Suit.SOUZU, rank, false));
        TheMahjongPlayer p1New = new TheMahjongPlayer(
                p1.points(), p1.riichiState(), p1.ippatsuEligible(),
                hand, p1.melds(), p1.discards(), p1.temporaryFuritenTiles(),
                p1.riichiPermanentFuriten(), p1.kitaCount());
        List<TheMahjongPlayer> ps = new ArrayList<>(cw.players());
        ps.set(1, p1New);
        TheMahjongRound seeded = new TheMahjongRound(
                cw.roundWind(), cw.handNumber(), cw.honba(), cw.riichiSticks(), cw.dealerSeat(),
                cw.state(), cw.currentTurnSeat(), cw.claimSourceSeat().orElse(-1),
                cw.activeTile(), cw.liveWall(), cw.rinshanTiles(), cw.doraIndicators(),
                cw.uraDoraIndicators(), cw.revealedDoraCount(), ps, List.of());
        TheMahjongRound after = seeded.claimChi(1, List.of(m2, m3));
        assertEquals(TheMahjongRound.State.TURN, after.state());
    }
}
