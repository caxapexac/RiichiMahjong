package com.themahjong.driver;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * A {@link MahjongPlayerInterface} backed by an external input queue (Minecraft button
 * presses, keyboard, etc.) with optional time-based fallbacks.
 *
 * <p>Two timers, both off by default:
 * <ul>
 *   <li>{@code autoDrawAfterSeconds}: when polled in {@link MatchPhase.AwaitingDraw}, returns
 *       {@link PlayerAction.Draw} after this many seconds elapse with no queued input.
 *       Set to {@code 0.0} for instant auto-draw, or leave empty for fully manual
 *       (immersive "physically take tile from wall" play).</li>
 *   <li>{@code autoDiscardAfterSeconds}: when polled in {@link MatchPhase.AwaitingDiscard}
 *       with no queued input, after the timeout discards a fallback tile (the rightmost
 *       legal {@code Discard} in {@code legalActions}, mimicking tsumogiri). For
 *       {@link MatchPhase.AwaitingClaims} the fallback is always {@code Pass} after the same
 *       timeout.</li>
 * </ul>
 *
 * <p>Typical usage from Minecraft:
 * <pre>{@code
 *   MahjongHumanPlayer me = new MahjongHumanPlayer();
 *   me.setAutoDrawAfterSeconds(0.0);          // auto-draw immediately
 *   me.setAutoDiscardAfterSeconds(30.0);      // 30s discard timer
 *   driver.replacePlayer(localSeat, me);
 *   // when the local player clicks a button:
 *   me.queue(action);
 * }</pre>
 *
 * <p>The timer resets every time the request's phase changes — so a long claim-window
 * deliberation doesn't eat into the next discard's budget.
 *
 * <p><b>Note:</b> this human impl does not call {@link TheMahjongDriver#submitAction}
 * directly. Buttons should call {@link #queue(PlayerAction)}; the queued action is
 * returned from the next {@code chooseAction} poll, which the driver invokes itself.
 */
public class MahjongHumanPlayer implements MahjongPlayerInterface {

    private PlayerAction queued;
    private double elapsedThisPhase;
    private MatchPhase lastPhase;
    private OptionalDouble autoDrawAfterSeconds = OptionalDouble.empty();
    private OptionalDouble autoDiscardAfterSeconds = OptionalDouble.empty();

    /** Queue an action submitted by the local player. Replaces any previously queued action. */
    public void queue(PlayerAction action) {
        this.queued = action;
    }

    /** Drop any queued input. Useful when the player changes their mind. */
    public void clearQueued() {
        this.queued = null;
    }

    public boolean hasQueued() { return queued != null; }

    public void setAutoDrawAfterSeconds(double seconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds must be non-negative");
        this.autoDrawAfterSeconds = OptionalDouble.of(seconds);
    }

    public void disableAutoDraw() { this.autoDrawAfterSeconds = OptionalDouble.empty(); }

    public void setAutoDiscardAfterSeconds(double seconds) {
        if (seconds < 0) throw new IllegalArgumentException("seconds must be non-negative");
        this.autoDiscardAfterSeconds = OptionalDouble.of(seconds);
    }

    public void disableAutoDiscard() { this.autoDiscardAfterSeconds = OptionalDouble.empty(); }

    @Override
    public Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds) {
        if (!request.phase().equals(lastPhase)) {
            elapsedThisPhase = 0.0;
            lastPhase = request.phase();
        }
        elapsedThisPhase += deltaSeconds;

        if (queued != null) {
            if (request.legalActions().contains(queued)) {
                PlayerAction picked = queued;
                queued = null;
                return Optional.of(picked);
            }
            // Queued action is no longer legal (state moved on); drop it silently.
            queued = null;
        }

        if (request.phase() instanceof MatchPhase.AwaitingDraw
                && autoDrawAfterSeconds.isPresent()
                && elapsedThisPhase >= autoDrawAfterSeconds.getAsDouble()) {
            return Optional.of(PlayerAction.Draw.INSTANCE);
        }

        if (autoDiscardAfterSeconds.isPresent()
                && elapsedThisPhase >= autoDiscardAfterSeconds.getAsDouble()) {
            if (request.phase() instanceof MatchPhase.AwaitingClaims) {
                return Optional.of(PlayerAction.Pass.INSTANCE);
            }
            if (request.phase() instanceof MatchPhase.AwaitingDiscard) {
                PlayerAction fallback = null;
                for (PlayerAction a : request.legalActions()) {
                    if (a instanceof PlayerAction.Discard) fallback = a;
                }
                if (fallback != null) return Optional.of(fallback);
            }
        }

        return Optional.empty();
    }
}
