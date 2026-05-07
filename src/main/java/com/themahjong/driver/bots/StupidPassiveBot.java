package com.themahjong.driver.bots;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;

/**
 * Bot that never claims and never declares riichi. Decision policy:
 * <ol>
 *   <li>If a winning declaration is offered (tsumo/ron), take it — passing on a winning
 *       hand would be perverse, and ignores temporary furiten cleanup.</li>
 *   <li>Otherwise pass on every claim opportunity.</li>
 *   <li>Discard the rightmost tile (tsumogiri).</li>
 *   <li>Draw immediately when asked.</li>
 * </ol>
 */
public final class StupidPassiveBot extends AbstractStupidBot {

    /** Instant-commit bot (zero think-time). Used in tests. */
    public StupidPassiveBot() { super(); }

    /** Bot with explicit per-category think durations. See {@link AbstractStupidBot}. */
    public StupidPassiveBot(
            double drawSeconds, double discardSeconds, double claimSeconds,
            double winSeconds, double passSeconds, double riichiSeconds,
            double abortSeconds) {
        super(drawSeconds, discardSeconds, claimSeconds, winSeconds,
                passSeconds, riichiSeconds, abortSeconds);
    }

    /** Sensible defaults for in-game pacing. */
    public static StupidPassiveBot humanLike() {
        return new StupidPassiveBot(0.3, 1.0, 0.5, 1.0, 0.5, 1.5, 1.0);
    }

    @Override
    protected PlayerAction pick(DecisionRequest request) {
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.DeclareTsumo
                    || a instanceof PlayerAction.DeclareRon
                    || a instanceof PlayerAction.DeclareChankan) {
                return a;
            }
        }
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Draw) return a;
        }
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Pass) return a;
        }
        PlayerAction discard = null;
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Discard) discard = a;
        }
        if (discard != null) return discard;
        return request.legalActions().isEmpty() ? null : request.legalActions().get(0);
    }
}
