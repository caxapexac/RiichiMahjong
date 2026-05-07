package com.themahjong.driver;

import com.themahjong.*;
import com.themahjong.yaku.HandShape;
import com.themahjong.yaku.WinCalculator;
import com.themahjong.yaku.WinContext;
import com.themahjong.yaku.WinResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds {@link WinResult}s from current round state for tsumo/ron decisions. Used by
 * {@link TheMahjongDriver} when listing legal actions and by bots that need to evaluate
 * candidate wins. Mirrors the logic in the replay test's {@code buildWinResult} so driver-
 * computed wins match Tenhou-replay-computed wins exactly.
 */
public final class WinResultBuilder {

    private WinResultBuilder() {}

    /** Build a tsumo {@link WinResult} for the current turn seat. Empty when yakuless. */
    public static Optional<WinResult> tryTsumo(
            TheMahjongRound round, TheMahjongRuleSet rules, int seat, boolean rinshanDraw) {
        if (!(round.activeTile() instanceof TheMahjongRound.ActiveTile.Drawn drawn)) {
            return Optional.empty();
        }
        TheMahjongPlayer player = round.players().get(seat);
        TheMahjongTile winTile = drawn.tile();
        List<TheMahjongTile> concealed = player.currentHand();
        List<HandShape> decomp = HandShape.decomposeForWin(concealed, player.melds(), winTile);
        if (decomp.isEmpty()) return Optional.empty();
        boolean lastTile = round.liveWall().isEmpty();
        boolean uninterrupted = round.players().stream().allMatch(p -> p.melds().isEmpty())
                && player.discards().isEmpty();
        WinContext ctx = WinContext.tsumo(
                round.dealer(seat), uninterrupted,
                player.riichiState(), player.ippatsuEligible(),
                winTile, round.seatWind(seat), round.roundWind(),
                lastTile, rinshanDraw);
        return Optional.of(finalize(round, rules, seat, seat, concealed, player, ctx, decomp));
    }

    /** Build a ron {@link WinResult} for {@code seat} on the held discard or kita tile. */
    public static Optional<WinResult> tryRon(
            TheMahjongRound round, TheMahjongRuleSet rules, int seat, TheMahjongTile heldTile, int fromSeat) {
        return tryRonOrChankan(round, rules, seat, heldTile, fromSeat, /*chankan=*/ false);
    }

    /**
     * Build a chankan-ron {@link WinResult} for {@code seat} on the kakan'd tile. Uses the
     * chankan-specific {@link WinContext} factory so the CHANKAN yaku eligibility flag is
     * set correctly.
     */
    public static Optional<WinResult> tryChankan(
            TheMahjongRound round, TheMahjongRuleSet rules, int seat, TheMahjongTile heldTile, int fromSeat) {
        return tryRonOrChankan(round, rules, seat, heldTile, fromSeat, /*chankan=*/ true);
    }

    private static Optional<WinResult> tryRonOrChankan(
            TheMahjongRound round, TheMahjongRuleSet rules, int seat,
            TheMahjongTile heldTile, int fromSeat, boolean chankan) {
        TheMahjongPlayer player = round.players().get(seat);
        List<TheMahjongTile> concealed = new ArrayList<>(player.currentHand());
        concealed.add(heldTile);
        List<HandShape> decomp = HandShape.decomposeForWin(concealed, player.melds(), heldTile);
        if (decomp.isEmpty()) return Optional.empty();
        boolean uninterrupted = round.players().stream().allMatch(p -> p.melds().isEmpty())
                && player.discards().isEmpty();
        boolean lastTile = round.liveWall().isEmpty();
        WinContext ctx = chankan
                ? WinContext.chankan(
                        round.dealer(seat), uninterrupted,
                        player.riichiState(), player.ippatsuEligible(),
                        heldTile, round.seatWind(seat), round.roundWind(),
                        lastTile)
                : WinContext.ron(
                        round.dealer(seat), uninterrupted,
                        player.riichiState(), player.ippatsuEligible(),
                        heldTile, round.seatWind(seat), round.roundWind(),
                        lastTile, false);
        WinResult r = finalize(round, rules, seat, fromSeat, concealed, player, ctx, decomp);
        return r.yaku().isEmpty() && r.yakuman().isEmpty() ? Optional.empty() : Optional.of(r);
    }

    private static WinResult finalize(
            TheMahjongRound round, TheMahjongRuleSet rules, int winner, int fromWho,
            List<TheMahjongTile> concealed, TheMahjongPlayer player, WinContext ctx,
            List<HandShape> decomp) {
        List<TheMahjongTile> allWinnerTiles = new ArrayList<>(concealed);
        for (TheMahjongMeld m : player.melds()) allWinnerTiles.addAll(m.tiles());
        // Chankan-rob rule: under !openKanDoraDelayedReveal (Mahjong Soul, WRC) declareKakan
        // already bumped revealedDoraCount, but the kan never happens from the robber's
        // perspective, so that indicator must be excluded from this win's score. Under
        // delayed reveal (Tenhou) the kakan only adds to pendingKanDoraReveals, which is
        // not counted here, so no adjustment is needed.
        int visible = round.revealedDoraCount();
        if (ctx.winType() == WinContext.WinType.CHANKAN
                && round.state() == TheMahjongRound.State.KAKAN_CLAIM_WINDOW
                && !rules.openKanDoraDelayedReveal()) {
            visible -= 1;
        }
        List<TheMahjongTile> ura = player.riichi()
                ? round.uraDoraIndicators().subList(0, visible)
                : List.of();
        return WinCalculator.calculateBest(
                decomp, ctx, allWinnerTiles,
                round.doraIndicators().subList(0, visible),
                ura,
                round.players().size(), winner, fromWho, round.dealerSeat(),
                round.honba(), round.riichiSticks(), rules,
                player.kitaCount());
    }
}
