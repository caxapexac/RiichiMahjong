package com.themahjong.driver.bots;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.PlayerAction;

/**
 * Bot that prefers any action over passing or plain discarding. Decision policy:
 * <ol>
 *   <li>If a winning declaration is offered (tsumo/ron/chankan), take it.</li>
 *   <li>Otherwise prefer kan &gt; pon &gt; chi &gt; kita &gt; ankan &gt; kakan &gt; riichi.</li>
 *   <li>Discard the rightmost legal tile (mimics tsumogiri / "throw the just-drawn tile").</li>
 *   <li>Draw immediately when asked.</li>
 * </ol>
 *
 * <p>Useful as a stress-test partner — exercises every claim path and reaches genuine
 * end states without sophisticated tile-efficiency reasoning.
 */
public final class StupidActiveBot extends AbstractStupidBot {

    /** Instant-commit bot (zero think-time). Used in tests. */
    public StupidActiveBot() { super(); }

    /** Bot with explicit per-category think durations. See {@link AbstractStupidBot}. */
    public StupidActiveBot(
            double drawSeconds, double discardSeconds, double claimSeconds,
            double winSeconds, double passSeconds, double riichiSeconds,
            double abortSeconds) {
        super(drawSeconds, discardSeconds, claimSeconds, winSeconds,
                passSeconds, riichiSeconds, abortSeconds);
    }

    /** Sensible defaults for in-game pacing. */
    public static StupidActiveBot humanLike() {
        return new StupidActiveBot(0.3, 0.3, 0.3, 1.0, 0.5, 1.0, 1.0);
    }

    @Override
    protected PlayerAction pick(DecisionRequest request) {
        // Win declarations first.
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.DeclareTsumo
                    || a instanceof PlayerAction.DeclareRon
                    || a instanceof PlayerAction.DeclareChankan) {
                return a;
            }
        }
        // Aggressive call priority.
        PlayerAction call = pickFirst(request,
                PlayerAction.Daiminkan.class,
                PlayerAction.Pon.class,
                PlayerAction.Chi.class,
                PlayerAction.DeclareKita.class,
                PlayerAction.Ankan.class,
                PlayerAction.Kakan.class,
                PlayerAction.DiscardWithRiichi.class);
        if (call != null) return call;

        // Required draws.
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Draw) return a;
        }

        // Plain discard (rightmost = newest tile in hand, typically the just-drawn tile).
        PlayerAction discard = null;
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Discard) discard = a;
        }
        if (discard != null) return discard;

        // Fallback: pass / abort.
        for (PlayerAction a : request.legalActions()) {
            if (a instanceof PlayerAction.Pass) return a;
        }
        return request.legalActions().isEmpty() ? null : request.legalActions().get(0);
    }

    @SafeVarargs
    private static PlayerAction pickFirst(DecisionRequest req, Class<? extends PlayerAction>... types) {
        for (Class<? extends PlayerAction> t : types) {
            for (PlayerAction a : req.legalActions()) {
                if (t.isInstance(a)) return a;
            }
        }
        return null;
    }
}
