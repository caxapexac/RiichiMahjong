package com.themahjong.driver;

import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.WinResult;

import java.util.List;

/**
 * A single legal action a player can submit in response to a {@link DecisionRequest}.
 *
 * <p>Each variant carries exactly the data the driver needs to apply the corresponding
 * {@code TheMahjongRound} transition. The driver computes legal {@code PlayerAction}s up
 * front (see {@link TheMahjongDriver#legalActions(int)}) so UIs can render one button per
 * entry without re-deriving legality.
 *
 * <p>Win-bearing actions ({@link DeclareTsumo}, {@link DeclareRon}, {@link DeclareChankan})
 * carry a fully-computed {@link WinResult}. The driver computes these via
 * {@code WinCalculator} when listing legal actions; players just pick one.
 */
public sealed interface PlayerAction
        permits PlayerAction.Draw, PlayerAction.Discard, PlayerAction.DiscardWithRiichi,
                PlayerAction.Chi, PlayerAction.Pon, PlayerAction.Daiminkan,
                PlayerAction.Ankan, PlayerAction.Kakan, PlayerAction.DeclareKita,
                PlayerAction.DeclareTsumo, PlayerAction.DeclareRon, PlayerAction.DeclareChankan,
                PlayerAction.KyuushuAbort, PlayerAction.Pass {

    /**
     * Take the next tile from the live wall (or rinshan pile after a kan). The only
     * legal action in {@link MatchPhase.AwaitingDraw}. Bots return this immediately when
     * polled; humans may queue it manually (immersive "grab tile from wall" mode) or
     * configure their {@link MahjongHumanPlayer} to auto-return it.
     */
    record Draw() implements PlayerAction {
        public static final Draw INSTANCE = new Draw();
    }

    /** Plain discard. Used in {@link MatchPhase.AwaitingDiscard}. */
    record Discard(TheMahjongTile tile) implements PlayerAction {}

    /**
     * Riichi declaration bundled with its discard. The driver calls
     * {@code declareRiichiIntent} → {@code discard} → {@code commitRiichiDeposit}.
     */
    record DiscardWithRiichi(TheMahjongTile tile) implements PlayerAction {}

    /**
     * One distinct chi shape. Multiple {@code Chi} entries may be legal simultaneously
     * (e.g. both ryanmen-low and ryanmen-high on a middle tile) — UI shows one button
     * each, labeled by {@link #shape}. {@code handTiles} is the two tiles taken from
     * hand to combine with the held discard.
     *
     * <p>When the hand contains a red-five duplicate that could fill the same shape, the
     * driver lists each {@code handTiles} permutation as a separate {@code Chi} entry so
     * the player can choose whether to commit the red five.
     */
    record Chi(ChiShape shape, List<TheMahjongTile> handTiles) implements PlayerAction {}

    /**
     * Which side of the chi the claimed tile fills. Used purely for UI labeling — the
     * round transition is identical regardless. {@code RYANMEN_LOW} fills the low side
     * of a two-sided wait (claimed tile is the smaller end), {@code RYANMEN_HIGH} the
     * high side, {@code KANCHAN} the middle of a closed wait, {@code PENCHAN} the edge
     * of a 12-or-89 wait.
     */
    enum ChiShape { RYANMEN_LOW, RYANMEN_HIGH, KANCHAN, PENCHAN }

    record Pon(List<TheMahjongTile> handTiles) implements PlayerAction {}

    record Daiminkan(List<TheMahjongTile> handTiles) implements PlayerAction {}

    /** Declared from {@link MatchPhase.AwaitingDiscard}. */
    record Ankan(List<TheMahjongTile> handTiles) implements PlayerAction {}

    /** Declared from {@link MatchPhase.AwaitingDiscard}. */
    record Kakan(TheMahjongMeld.Pon upgradedFrom, TheMahjongTile addedTile) implements PlayerAction {}

    /** Sanma north-removal, declared from {@link MatchPhase.AwaitingDiscard}. */
    record DeclareKita(TheMahjongTile tile) implements PlayerAction {}

    /** Self-draw win. Declared from {@link MatchPhase.AwaitingDiscard}. */
    record DeclareTsumo(WinResult result) implements PlayerAction {}

    /**
     * Ron on a discard or kita tile. Declared from {@link MatchPhase.AwaitingClaims}.
     * The driver collects all ron declarations before dispatching {@code declareWin} /
     * {@code beginRon}+{@code addRon} / {@code abortiveDraw} (sanchahou) per ruleset.
     */
    record DeclareRon(WinResult result) implements PlayerAction {}

    /**
     * Ron on a kakan'd tile (chankan). Declared from {@link MatchPhase.AwaitingClaims}
     * when {@code activeTile} is the just-added kakan tile.
     */
    record DeclareChankan(WinResult result) implements PlayerAction {}

    /**
     * Nine-terminals-or-honors abortive draw. Declared from {@link MatchPhase.AwaitingDiscard}
     * on the first turn before any meld has been called. Only legal when
     * {@code rules.abortiveDrawsAllowed()} and {@code round.isKyuushuEligible()}.
     */
    record KyuushuAbort() implements PlayerAction {
        public static final KyuushuAbort INSTANCE = new KyuushuAbort();
    }

    /**
     * Decline this opportunity — no claim, no ron. Used in {@link MatchPhase.AwaitingClaims}
     * and as the default human-timeout action in {@link MatchPhase.AwaitingDiscard}
     * (driver auto-discards the just-drawn tile).
     */
    record Pass() implements PlayerAction {
        public static final Pass INSTANCE = new Pass();
    }
}
