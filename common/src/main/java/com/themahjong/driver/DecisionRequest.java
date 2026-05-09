package com.themahjong.driver;

import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongRuleSet;

import java.util.List;

/**
 * What a {@link MahjongPlayerInterface} is asked on each driver tick.
 *
 * <p>The driver passes a request to every seat whose decision it needs ({@code seat}
 * indicates which one, since a single player implementation may be reused across seats
 * in tests). The player picks one of {@code legalActions} and returns it; until it does,
 * the driver keeps polling each {@code advance(dt)}.
 *
 * <p>The full {@link TheMahjongRound} snapshot is included so player implementations
 * (especially bots) can inspect public game state — discards, dora, melds, points —
 * without the driver having to expose specialized accessors. Players must treat the
 * snapshot as read-only.
 *
 * @param seat         the seat being asked
 * @param phase        the current driver phase (always {@link MatchPhase.AwaitingDiscard} or
 *                     {@link MatchPhase.AwaitingClaims} — internal phases never reach players)
 * @param legalActions exactly the actions the player may return; {@code Pass} is included
 *                     when passing is legal (claim window, or human auto-timeout)
 * @param round        immutable snapshot of round state at decision time
 * @param rules        active ruleset
 */
public record DecisionRequest(
        int seat,
        MatchPhase phase,
        List<PlayerAction> legalActions,
        TheMahjongRound round,
        TheMahjongRuleSet rules) {

    public DecisionRequest {
        if (legalActions == null) throw new IllegalArgumentException("legalActions cannot be null");
        if (legalActions.isEmpty()) throw new IllegalArgumentException("legalActions cannot be empty");
        if (round == null) throw new IllegalArgumentException("round cannot be null");
        if (rules == null) throw new IllegalArgumentException("rules cannot be null");
        if (phase == null) throw new IllegalArgumentException("phase cannot be null");
        legalActions = List.copyOf(legalActions);
    }
}
