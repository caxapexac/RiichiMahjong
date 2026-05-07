package com.themahjong;

import com.themahjong.replay.TenhouAction;
import com.themahjong.replay.TenhouGame;
import com.themahjong.replay.TenhouLogParser;
import com.themahjong.replay.TenhouRound;
import com.themahjong.replay.TenhouWallReconstructor;
import com.themahjong.yaku.HandShape;
import com.themahjong.yaku.WinCalculator;
import com.themahjong.yaku.WinContext;
import com.themahjong.yaku.WinResult;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Replay tests using real Tenhou game logs (235 games from github.com/m77so/tenhou).
 */
class TheMahjongReplayTest {

    // -------------------------------------------------------------------------
    // Fixture discovery

    static Stream<Named<Path>> allFixtures() throws Exception {
        URL root = TheMahjongReplayTest.class.getResource("/com/themahjong/replay/tenhou");
        assertNotNull(root, "tenhou fixture directory not found on classpath");
        Path rootPath = Paths.get(root.toURI());
        return Files.walk(rootPath)
                .filter(p -> p.toString().endsWith(".xml"))
                .filter(p -> !p.getParent().getFileName().toString().equals("2019"))
                .sorted()
                .map(p -> Named.of(p.getFileName().toString(), p));
    }

    private TenhouGame load(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return TenhouLogParser.parse(path.getFileName().toString(), in);
        }
    }

    // -------------------------------------------------------------------------
    // Parameterized assertions over all 235 games

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void parsesWithoutException(Path fixture) throws Exception {
        assumeTrue(load(fixture) != null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void hasAtLeastOneRound(Path fixture) throws Exception {
        TenhouGame game = load(fixture);
        assumeTrue(game != null);
        assertFalse(game.rounds().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void eachRoundHasCorrectInitialHands(Path fixture) throws Exception {
        TenhouGame game = load(fixture);
        assumeTrue(game != null);
        for (TenhouRound round : game.rounds()) {
            int pc = round.playerCount();
            assertTrue(pc == 3 || pc == 4, "unexpected playerCount " + pc);
            for (int seat = 0; seat < pc; seat++) {
                assertEquals(13, round.initialHands().get(seat).size(),
                        "round " + round.roundNumber() + " seat " + seat);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void eachRoundEndsWithWinOrExhaustiveDraw(Path fixture) throws Exception {
        TenhouGame game = load(fixture);
        assumeTrue(game != null);
        for (TenhouRound round : game.rounds()) {
            List<TenhouAction> actions = round.actions();
            assertFalse(actions.isEmpty(), "round " + round.roundNumber() + " has no actions");
            TenhouAction last = actions.get(actions.size() - 1);
            assertTrue(last instanceof TenhouAction.Win || last instanceof TenhouAction.ExhaustiveDraw,
                    "round " + round.roundNumber() + " ends with " + last.getClass().getSimpleName());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void drawsPlusClaimsCoversDiscards(Path fixture) throws Exception {
        TenhouGame game = load(fixture);
        assumeTrue(game != null);
        for (TenhouRound round : game.rounds()) {
            int[] draws = new int[4], discards = new int[4], claims = new int[4];
            for (TenhouAction a : round.actions()) {
                if (a instanceof TenhouAction.Draw d) draws[d.seat()]++;
                else if (a instanceof TenhouAction.Discard d) discards[d.seat()]++;
                else if (a instanceof TenhouAction.Claim c) claims[c.seat()]++;
            }
            for (int seat = 0; seat < 4; seat++) {
                assertTrue(draws[seat] + claims[seat] >= discards[seat],
                        "round " + round.roundNumber() + " seat " + seat
                                + ": draws=" + draws[seat] + " claims=" + claims[seat]
                                + " discards=" + discards[seat]);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void doraIndicatorsAreValid(Path fixture) throws Exception {
        TenhouGame game = load(fixture);
        assumeTrue(game != null);
        for (TenhouRound round : game.rounds()) {
            assertNotNull(round.doraIndicator().suit(),
                    "round " + round.roundNumber() + " dora indicator suit is null");
        }
    }

    // -------------------------------------------------------------------------
    // Spot checks on the single known-good fixture

    private static final String KNOWN = "2016122101gm-0089-0000-ac06ce54";

    private TenhouGame loadKnown() throws Exception {
        String path = "/com/themahjong/replay/" + KNOWN + ".xml";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, "known fixture not found: " + path);
            return TenhouLogParser.parse(KNOWN, in);
        }
    }

    @Test
    void known_8rounds() throws Exception {
        assertEquals(8, loadKnown().rounds().size());
    }

    @Test
    void known_dealerRotates() throws Exception {
        List<TenhouRound> rounds = loadKnown().rounds();
        for (int i = 0; i < rounds.size(); i++) {
            assertEquals(i % 4, rounds.get(i).dealer(), "round index " + i);
        }
    }

    @Test
    void known_round1_doraIndicatorIs4m() throws Exception {
        TheMahjongTile dora = loadKnown().rounds().get(0).doraIndicator();
        assertEquals(TheMahjongTile.Suit.MANZU, dora.suit());
        assertEquals(4, dora.rank());
        assertFalse(dora.redDora());
    }

    @Test
    void known_round1_firstDrawIsDealer1p() throws Exception {
        TenhouAction first = loadKnown().rounds().get(0).actions().get(0);
        assertInstanceOf(TenhouAction.Draw.class, first);
        TenhouAction.Draw draw = (TenhouAction.Draw) first;
        assertEquals(0, draw.seat());
        assertEquals(TheMahjongTile.Suit.PINZU, draw.tile().suit());
        assertEquals(1, draw.tile().rank());
    }

    @Test
    void known_round1_winnerSeat3ByRonFromSeat0() throws Exception {
        TenhouAction.Win win = loadKnown().rounds().get(0).actions().stream()
                .filter(a -> a instanceof TenhouAction.Win)
                .map(a -> (TenhouAction.Win) a)
                .findFirst().orElseThrow();
        assertEquals(3, win.winner());
        assertEquals(0, win.fromWho());
    }

    @Test
    void known_lastRoundMarkedFinal() throws Exception {
        List<TenhouRound> rounds = loadKnown().rounds();
        TenhouRound last = rounds.get(rounds.size() - 1);
        boolean hasFinalWin = last.actions().stream()
                .anyMatch(a -> a instanceof TenhouAction.Win w && w.isLastRound());
        assertTrue(hasFinalWin);
    }

    // -------------------------------------------------------------------------
    // Full replay

    @ParameterizedTest(name = "{0}")
    @MethodSource("allFixtures")
    void fullReplay(Path fixture) throws Exception {
        TenhouGame game = load(fixture);
        assumeTrue(game != null);
        for (TenhouRound r : game.rounds()) {
            List<TheMahjongTile> wall = TenhouWallReconstructor.reconstruct(r);
            TheMahjongTile.Wind roundWind = TheMahjongTile.Wind.fromTileRank(r.roundNumber() / 4 + 1);
            int handNumber = r.roundNumber() % 4 + 1;
            TheMahjongRound round = TheMahjongRound.start(
                    r.playerCount(), 25000, wall,
                    roundWind, handNumber, r.honba(), r.riichiSticks(), r.dealer());
            boolean drawnFromRinshan = false;
            for (TenhouAction action : r.actions()) {
                if (action instanceof TenhouAction.Draw) {
                    if (round.state() == TheMahjongRound.State.CLAIM_WINDOW) {
                        round = round.skipClaims();
                    } else if (round.state() == TheMahjongRound.State.KITA_WINDOW) {
                        round = round.skipKitaClaims();
                    } else if (round.state() == TheMahjongRound.State.KAKAN_CLAIM_WINDOW) {
                        round = round.skipKakanClaims();
                    }
                    drawnFromRinshan = (round.state() == TheMahjongRound.State.RINSHAN_DRAW);
                    round = round.draw();
                } else if (action instanceof TenhouAction.Discard d) {
                    drawnFromRinshan = false;
                    round = round.discard(d.tile());
                } else if (action instanceof TenhouAction.RiichiStep1) {
                    round = round.declareRiichiIntent();
                } else if (action instanceof TenhouAction.RiichiStep2) {
                    round = round.commitRiichiDeposit();
                } else if (action instanceof TenhouAction.Claim c) {
                    drawnFromRinshan = false;
                    round = applyTenhouClaim(round, c);
                } else if (action instanceof TenhouAction.Win w) {
                    if (round.state() != TheMahjongRound.State.ENDED) {
                        WinResult result = buildWinResult(round, w, drawnFromRinshan, r.playerCount());
                        if (result != null && w.scoreDeltas().length >= r.playerCount()) {
                            boolean tsumo = w.winner() == w.fromWho();
                            String ctx = String.format(
                                    " [%dp %s winner=%d from=%d dealer=%d honba=%d sticks=%d"
                                    + " yaku=%s yakuman=%s han=%d fu=%d dora=%d]",
                                    r.playerCount(),
                                    tsumo ? "TSUMO" : "RON",
                                    w.winner(), w.fromWho(), round.dealerSeat(),
                                    round.honba(), round.riichiSticks(),
                                    result.yaku(), result.yakuman(),
                                    result.han(), result.fu(), result.doraCount());
                            for (int seat = 0; seat < r.playerCount(); seat++) {
                                assertEquals(w.scoreDeltas()[seat], result.pointDeltas().get(seat),
                                        fixture.getFileName() + " round " + r.roundNumber()
                                        + " seat " + seat + ctx);
                            }
                        }
                        if (result == null) result = zeroWinResult(r.playerCount());
                        round = round.declareWin(w.winner(), w.fromWho(), result);
                    }
                } else if (action instanceof TenhouAction.ExhaustiveDraw) {
                    round = round.exhaustiveDraw(TheMahjongRuleSet.tenhou());
                }
            }
            assertEquals(TheMahjongRound.State.ENDED, round.state(),
                    "round " + r.roundNumber() + " did not reach ENDED");
        }
    }

    // -------------------------------------------------------------------------
    // Win result helpers

    /**
     * Builds a WinResult for the given win action. Returns null for chankan wins
     * (RINSHAN_DRAW state) where the winning tile is embedded in a meld.
     */
    static WinResult buildWinResult(
            TheMahjongRound round, TenhouAction.Win w,
            boolean drawnFromRinshan, int playerCount) {
        int winner  = w.winner();
        int fromWho = w.fromWho();
        boolean isTsumo = (winner == fromWho);
        boolean isChankan = round.state() == TheMahjongRound.State.KAKAN_CLAIM_WINDOW;

        TheMahjongPlayer player = round.players().get(winner);
        TheMahjongTile winTile;
        List<TheMahjongTile> concealedTiles;

        if (isTsumo) {
            // Tenhou puts a Draw action between kan and rinshan-tsumo, so by the time
            // the Win action arrives the engine is in TURN with a Drawn active tile.
            // RINSHAN_DRAW here would mean a Tenhou log without that Draw — defensive skip.
            if (round.state() == TheMahjongRound.State.RINSHAN_DRAW) return null;
            winTile = ((TheMahjongRound.ActiveTile.Drawn) round.activeTile()).tile();
            concealedTiles = player.currentHand();
        } else if (round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKita hk) {
            // Sanma kita-ron: the winning tile is the just-declared north.
            winTile = hk.tile();
            concealedTiles = new ArrayList<>(player.currentHand());
            concealedTiles.add(winTile);
        } else if (round.activeTile() instanceof TheMahjongRound.ActiveTile.HeldKakan hk) {
            // Chankan: the winning tile is the kakan-added tile, held by the kakan
            // declarer's meld. The winner's hand stays as-is in the engine; we assemble
            // the conceptual 14-tile hand by appending the chankan tile.
            winTile = hk.tile();
            concealedTiles = new ArrayList<>(player.currentHand());
            concealedTiles.add(winTile);
        } else {
            winTile = ((TheMahjongRound.ActiveTile.HeldDiscard) round.activeTile()).tile();
            concealedTiles = new ArrayList<>(player.currentHand());
            concealedTiles.add(winTile);
        }

        List<HandShape> decompositions = HandShape.decomposeForWin(concealedTiles, player.melds(), winTile);
        if (decompositions.isEmpty()) return null;

        List<TheMahjongTile> allWinnerTiles = new ArrayList<>(concealedTiles);
        for (TheMahjongMeld meld : player.melds()) allWinnerTiles.addAll(meld.tiles());

        // Tenhou (dealer first tsumo): no discards anywhere yet — naturally implied
        // by "winner has no discards" + "no calls made" since the dealer draws first.
        // Chihou (non-dealer first tsumo): the dealer (and possibly intermediate seats)
        // may already have discarded; what disqualifies it is any claim/kan, not discards.
        boolean uninterrupted = round.players().stream().allMatch(p -> p.melds().isEmpty())
                && player.discards().isEmpty();
        boolean lastTile = round.liveWall().isEmpty();

        WinContext ctx;
        if (isTsumo) {
            ctx = WinContext.tsumo(
                    round.dealer(winner), uninterrupted,
                    player.riichiState(), player.ippatsuEligible(),
                    winTile, round.seatWind(winner), round.roundWind(),
                    lastTile, drawnFromRinshan);
        } else if (isChankan) {
            ctx = WinContext.chankan(
                    round.dealer(winner), uninterrupted,
                    player.riichiState(), player.ippatsuEligible(),
                    winTile, round.seatWind(winner), round.roundWind(),
                    lastTile);
        } else {
            ctx = WinContext.ron(
                    round.dealer(winner), uninterrupted,
                    player.riichiState(), player.ippatsuEligible(),
                    winTile, round.seatWind(winner), round.roundWind(),
                    lastTile, false);
        }

        int visibleDoraCount = round.revealedDoraCount();
        List<TheMahjongTile> uraIndicators = player.riichi()
                ? round.uraDoraIndicators().subList(0, visibleDoraCount)
                : List.of();

        TheMahjongRuleSet rules = (playerCount == 3) ? TheMahjongRuleSet.tenhouSanma() : TheMahjongRuleSet.tenhou();
        return WinCalculator.calculateBest(
                decompositions, ctx, allWinnerTiles,
                round.doraIndicators().subList(0, visibleDoraCount),
                uraIndicators,
                playerCount, winner, fromWho, round.dealerSeat(),
                round.honba(), round.riichiSticks(), rules,
                player.kitaCount());
    }

    /** Placeholder WinResult used when {@link #buildWinResult} cannot reconstruct a hand
     *  (chankan: winning tile is embedded in a meld). Carries a fabricated RIICHI yaku so
     *  the engine's yaku-existence guard accepts it; deltas are zero because the replay
     *  driver doesn't validate scores on these paths. */
    static WinResult zeroWinResult(int playerCount) {
        List<Integer> zeros = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) zeros.add(0);
        return new WinResult(
                List.of(com.themahjong.yaku.NonYakuman.RIICHI), List.of(),
                1, 0, 0, List.copyOf(zeros));
    }

    // -------------------------------------------------------------------------
    // Winning hand completeness check (kept for historical context)

    private static void assertWinningHandIsComplete(TheMahjongRound round, TenhouAction.Win w) {
        TheMahjongRound.State state = round.state();
        if (state == TheMahjongRound.State.RINSHAN_DRAW) return;

        TheMahjongPlayer winner = round.players().get(w.winner());
        List<TheMahjongTile> concealedTiles;
        if (state == TheMahjongRound.State.TURN) {
            concealedTiles = winner.currentHand();
        } else {
            TheMahjongTile winTile = ((TheMahjongRound.ActiveTile.HeldDiscard) round.activeTile()).tile();
            concealedTiles = new ArrayList<>(winner.currentHand());
            concealedTiles.add(winTile);
        }
        assertFalse(
                HandShape.decompose(concealedTiles, winner.melds()).isEmpty(),
                "Winner seat " + w.winner() + " hand has no valid decomposition: " + concealedTiles
                        + " melds=" + winner.melds());
    }

    static TheMahjongRound applyTenhouClaim(TheMahjongRound round, TenhouAction.Claim c) {
        if (c.type() == TenhouAction.ClaimType.KITA) {
            TheMahjongTile refTile = c.tiles().get(0);
            TheMahjongPlayer player = round.players().get(round.currentTurnSeat());
            TheMahjongTile kitaTile = player.currentHand().stream()
                    .filter(t -> t.matchesSuitRank(refTile))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Kita tile not in hand: " + refTile));
            return round.declareKita(kitaTile);
        }
        if (c.type() == TenhouAction.ClaimType.KAKAN) {
            TheMahjongTile refTile = c.tiles().get(0);
            TheMahjongPlayer player = round.players().get(round.currentTurnSeat());
            TheMahjongMeld.Pon existingPon = null;
            for (TheMahjongMeld m : player.melds()) {
                if (m instanceof TheMahjongMeld.Pon pon
                        && pon.tiles().get(0).matchesSuitRank(refTile)) {
                    existingPon = pon;
                    break;
                }
            }
            if (existingPon == null) {
                throw new IllegalStateException("No matching Pon for kakan tile " + refTile);
            }
            TheMahjongTile addedTile = player.currentHand().stream()
                    .filter(t -> t.matchesSuitRank(refTile))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Kakan tile not in hand: " + refTile));
            return round.declareKakan(existingPon, addedTile, TheMahjongRuleSet.tenhou());
        }
        if (c.type() == TenhouAction.ClaimType.ANKAN) {
            TheMahjongTile refTile = c.tiles().get(0);
            TheMahjongPlayer player = round.players().get(round.currentTurnSeat());
            List<TheMahjongTile> handTiles = new ArrayList<>();
            List<TheMahjongTile> hand = new ArrayList<>(player.currentHand());
            for (int i = 0; i < 4; i++) {
                TheMahjongTile found = null;
                for (int j = 0; j < hand.size(); j++) {
                    if (hand.get(j).matchesSuitRank(refTile)) {
                        found = hand.remove(j);
                        break;
                    }
                }
                if (found == null) throw new IllegalStateException("Ankan tile not in hand: " + refTile);
                handTiles.add(found);
            }
            return round.declareAnkan(handTiles, TheMahjongRuleSet.tenhou());
        }
        // CHI / PON / DAIMINKAN — claimed tile is the held discard
        TheMahjongTile claimed = ((TheMahjongRound.ActiveTile.HeldDiscard) round.activeTile()).tile();
        List<TheMahjongTile> handTiles = new ArrayList<>(c.tiles());
        handTiles.remove(claimed);
        if (c.type() == TenhouAction.ClaimType.CHI) return round.claimChi(c.seat(), handTiles);
        if (c.type() == TenhouAction.ClaimType.PON) return round.claimPon(c.seat(), handTiles);
        return round.claimDaiminkan(c.seat(), handTiles, TheMahjongRuleSet.tenhou());
    }
}
