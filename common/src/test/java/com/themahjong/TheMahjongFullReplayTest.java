package com.themahjong;

import com.themahjong.replay.TenhouAction;
import com.themahjong.replay.TenhouGame;
import com.themahjong.replay.TenhouLogParser;
import com.themahjong.replay.TenhouRound;
import com.themahjong.replay.TenhouWallReconstructor;
import com.themahjong.yaku.WinResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Full replay tests against the downloaded 2019 Tenhou Houou dataset.
 *
 * <p>Excluded from the normal {@code testTheMahjong} task. Run via:
 * <pre>.\gradlew.bat testTheMahjongFull</pre>
 *
 * <p>Requires fixtures to be present — run {@code scripts/download-tenhou-2019.ps1} first.
 */
@Tag("full-replay")
class TheMahjongFullReplayTest {

    static Stream<Path> fixtures2019() throws Exception {
        URL root = TheMahjongFullReplayTest.class
                .getResource("/com/themahjong/replay/tenhou/2019");
        assumeTrue(root != null, "2019 fixtures not found — run scripts/download-tenhou-2019.ps1");
        return Files.walk(Paths.get(root.toURI()))
                .filter(p -> p.toString().endsWith(".xml"))
                .sorted();
    }

    private TenhouGame load(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return TenhouLogParser.parse(path.getFileName().toString(), in);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures2019")
    void fullReplay2019(Path fixture) throws Exception {
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
                    round = TheMahjongReplayTest.applyTenhouClaim(round, c);
                } else if (action instanceof TenhouAction.Win w) {
                    if (round.state() != TheMahjongRound.State.ENDED) {
                        WinResult result = TheMahjongReplayTest.buildWinResult(
                                round, w, drawnFromRinshan, r.playerCount());
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
                        if (result == null) result = TheMahjongReplayTest.zeroWinResult(r.playerCount());
                        round = round.declareWin(w.winner(), w.fromWho(), result);
                    }
                } else if (action instanceof TenhouAction.ExhaustiveDraw) {
                    round = round.exhaustiveDraw(TheMahjongRuleSet.tenhou());
                }
                // DoraReveal is a no-op
            }
            assertEquals(TheMahjongRound.State.ENDED, round.state(),
                    "round " + r.roundNumber() + " did not reach ENDED");
        }
    }
}
