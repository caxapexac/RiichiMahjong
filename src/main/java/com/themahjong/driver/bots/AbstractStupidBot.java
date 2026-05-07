package com.themahjong.driver.bots;

import com.themahjong.driver.DecisionRequest;
import com.themahjong.driver.MahjongPlayerInterface;
import com.themahjong.driver.PlayerAction;

import java.util.Objects;
import java.util.Optional;

/**
 * Shared base for the stock "stupid" bots. Owns per-action-category think durations and
 * the elapsed-time accounting; subclasses only override {@link #pick(DecisionRequest)}
 * with their decision policy.
 *
 * <p>Think-time semantics: every call to {@link #chooseAction} accumulates {@code dt}
 * for the *current* request. When the request changes (different phase, different held
 * tile, etc.) the elapsed counter resets — so a partially-spent claim window doesn't
 * eat into the next discard's budget. Once accumulated time meets the threshold for the
 * picked action's category, the action commits.
 *
 * <p>The default constructor sets all delays to zero (instant-commit; used in tests).
 * Subclasses pass per-category seconds through to the long constructor for in-game pacing.
 */
public abstract class AbstractStupidBot implements MahjongPlayerInterface {

    private final double drawSeconds;
    private final double discardSeconds;
    private final double claimSeconds;
    private final double winSeconds;
    private final double passSeconds;
    private final double riichiSeconds;
    private final double abortSeconds;

    private DecisionRequest currentRequest;
    private double elapsedThisRequest;

    /** All delays zero — bot commits on the first poll regardless of {@code dt}. */
    protected AbstractStupidBot() {
        this(0, 0, 0, 0, 0, 0, 0);
    }

    protected AbstractStupidBot(
            double drawSeconds,
            double discardSeconds,
            double claimSeconds,
            double winSeconds,
            double passSeconds,
            double riichiSeconds,
            double abortSeconds) {
        if (drawSeconds < 0 || discardSeconds < 0 || claimSeconds < 0
                || winSeconds < 0 || passSeconds < 0 || riichiSeconds < 0
                || abortSeconds < 0) {
            throw new IllegalArgumentException("think durations must be non-negative");
        }
        this.drawSeconds = drawSeconds;
        this.discardSeconds = discardSeconds;
        this.claimSeconds = claimSeconds;
        this.winSeconds = winSeconds;
        this.passSeconds = passSeconds;
        this.riichiSeconds = riichiSeconds;
        this.abortSeconds = abortSeconds;
    }

    @Override
    public final Optional<PlayerAction> chooseAction(DecisionRequest request, double deltaSeconds) {
        PlayerAction chosen = pick(request);
        if (chosen == null) return Optional.empty();
        if (!Objects.equals(currentRequest, request)) {
            currentRequest = request;
            elapsedThisRequest = 0;
        }
        elapsedThisRequest += deltaSeconds;
        return elapsedThisRequest >= secondsFor(chosen)
                ? Optional.of(chosen)
                : Optional.empty();
    }

    /**
     * Pick the action this bot wants for the given request, ignoring think-time. Return
     * {@code null} only if no action is selectable (legalActions empty); the base class
     * converts this to {@link Optional#empty()}.
     */
    protected abstract PlayerAction pick(DecisionRequest request);

    private double secondsFor(PlayerAction action) {
        if (action instanceof PlayerAction.Draw) return drawSeconds;
        if (action instanceof PlayerAction.Discard) return discardSeconds;
        if (action instanceof PlayerAction.DiscardWithRiichi) return riichiSeconds;
        if (action instanceof PlayerAction.Chi
                || action instanceof PlayerAction.Pon
                || action instanceof PlayerAction.Daiminkan
                || action instanceof PlayerAction.Ankan
                || action instanceof PlayerAction.Kakan
                || action instanceof PlayerAction.DeclareKita) return claimSeconds;
        if (action instanceof PlayerAction.DeclareTsumo
                || action instanceof PlayerAction.DeclareRon
                || action instanceof PlayerAction.DeclareChankan) return winSeconds;
        if (action instanceof PlayerAction.Pass) return passSeconds;
        if (action instanceof PlayerAction.KyuushuAbort) return abortSeconds;
        return 0;
    }
}
