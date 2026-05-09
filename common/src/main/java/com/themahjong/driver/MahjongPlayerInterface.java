package com.themahjong.driver;

import java.util.Optional;

/**
 * A decision source for one seat. Implemented by bots (decide instantly) and by humans
 * (queue button presses; auto-action on timeout).
 *
 * <p>The driver polls {@link #chooseAction} from {@link TheMahjongDriver#advance(double)}.
 * Returning {@link Optional#empty()} means "still thinking" and the driver will poll again
 * next tick. Returning a present action commits the player to that action; the driver
 * validates it against the request's {@code legalActions} and applies the corresponding
 * round transition.
 *
 * <p>{@code deltaSeconds} is the wall-clock time since the previous {@code chooseAction}
 * call for this player, supplied by the host (Minecraft passes its server-tick delta
 * through {@code TheMahjongDriver.advance}). Bots typically ignore it; human
 * implementations use it to drive auto-discard timers.
 *
 * <p>Implementations may hold mutable state (queues, timers) but must remain deterministic
 * given the same input sequence so replay tests stay reproducible.
 */
public interface MahjongPlayerInterface {

    /**
     * @return the chosen action (must be {@code .equals}-equal to one of
     *         {@code request.legalActions()}), or empty if not ready yet
     */
    Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds);

    /**
     * Called by the driver when this player's seat finishes a round / the match ends /
     * an opponent makes a notable move. Default no-op; observation-driven implementations
     * (logging, learning bots, replay verifiers) may override. Never blocks the driver.
     */
    default void onEvent(MatchEvent event) {}
}
