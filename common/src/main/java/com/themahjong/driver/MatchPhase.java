package com.themahjong.driver;

import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.WinResult;

import java.util.List;
import java.util.Set;

/**
 * What the driver is currently waiting on. Returned by {@link TheMahjongDriver#currentPhase()}.
 *
 * <p>The Minecraft tile entity inspects this each tick to decide what UI to show.
 *
 * <p><b>Auto-drained phase.</b> {@link Resolving} is a real state the driver passes
 * through during multi-ron settlement, but {@link TheMahjongDriver#advance} is
 * contractually required to drain it before returning — i.e. {@code currentPhase()}
 * called immediately after {@code advance(dt)} will never return {@code Resolving}.
 * It is exposed in the sealed type so tests and debug tooling can observe internal
 * progress, but UI code may treat it as unreachable. {@code switch} statements over
 * {@code MatchPhase} should still handle all variants for exhaustiveness.
 */
public sealed interface MatchPhase
        permits MatchPhase.NotStarted, MatchPhase.Dealing, MatchPhase.AwaitingDraw,
                MatchPhase.AwaitingDiscard, MatchPhase.AwaitingClaims, MatchPhase.Resolving,
                MatchPhase.RoundEnded, MatchPhase.BetweenRounds, MatchPhase.MatchEnded {

    /** Driver constructed but {@code TheMahjongMatch} hasn't started its first round. */
    record NotStarted() implements MatchPhase {}

    /**
     * Round just started; the driver is animating the visual deal sequence (wall
     * built → hands dealt → initial dora flipped) before yielding control to the
     * dealer for their first draw. Only entered when {@link TheMahjongDriver#setAnimationsEnabled}
     * is {@code true}; otherwise round-starts go straight to {@link AwaitingDraw}.
     *
     * <p>{@code elapsed} accumulates the {@code advance(dt)} delta within the current
     * {@code stage}; when {@code elapsed >= stage.duration()} the driver advances to
     * the next stage (or to {@link AwaitingDraw} after the last stage).
     *
     * <p>No {@link PlayerAction} is legal during this phase — players are passive.
     */
    record Dealing(Stage stage, double elapsed) implements MatchPhase {}

    /**
     * Visual dealing sub-stages, in order. Each stage has two parts:
     * <ul>
     *   <li>{@code buildDuration} — the active visual build (wall growing,
     *       hands filling, dora rotating).</li>
     *   <li>{@code pauseAfter} — a static hold after the build completes, so
     *       the viewer can read each stage's end state before the next stage
     *       starts.</li>
     * </ul>
     * {@link #duration()} returns the sum and is what the driver clocks against.
     */
    enum Stage {
        WALL_BUILDING(1.0, 0.5),
        DEALING_HANDS(1.0, 0.5),
        DORA_FLIP(0.5, 0.5);

        private final double buildDuration;
        private final double pauseAfter;

        Stage(double buildDuration, double pauseAfter) {
            this.buildDuration = buildDuration;
            this.pauseAfter = pauseAfter;
        }

        public double buildDuration() {
            return buildDuration;
        }

        public double duration() {
            return buildDuration + pauseAfter;
        }
    }

    /**
     * The seat must take the next tile from the wall. {@code rinshan} is {@code true}
     * after a kan (tile pulled from the rinshan pile), {@code false} for a normal turn
     * draw from the live wall.
     *
     * <p>The only legal action here is {@link PlayerAction.Draw}. Exposed as a real
     * external phase (rather than driver-internal) so the host can offer immersive
     * "physically take tile from wall" interactions, and so {@code MahjongHumanPlayer}
     * can be configured for auto-draw vs manual-draw.
     */
    record AwaitingDraw(int seat, boolean rinshan) implements MatchPhase {}

    /**
     * The seat must submit a {@link PlayerAction}: discard, riichi+discard, ankan, kakan,
     * kita, tsumo, or kyuushu abort.
     */
    record AwaitingDiscard(int seat) implements MatchPhase {}

    /**
     * One or more seats may claim the held tile. Each pending seat owes a decision; the
     * driver resolves call priority once everyone has answered (or timed out into Pass).
     *
     * @param pendingSeats seats from whom the driver still needs an answer
     * @param heldTile     the discard / kita / kakan tile being offered
     * @param source       what kind of action produced the held tile, controlling which
     *                     claim types are legal (only ron from KITA/KAKAN sources)
     */
    record AwaitingClaims(
            Set<Integer> pendingSeats,
            TheMahjongTile heldTile,
            ClaimSource source) implements MatchPhase {}

    /**
     * Origin of the {@code AwaitingClaims} held tile. Determines which claim types are
     * legal: {@link #DISCARD} allows ron + pon + chi + daiminkan; {@link #KITA} and
     * {@link #KAKAN} both allow only ron (and chankan-flavored ron from KAKAN).
     */
    enum ClaimSource { DISCARD, KITA, KAKAN }

    /**
     * Multi-ron in progress (2+ ron declared, ruleset allows it). Driver-internal —
     * {@code advance} feeds {@code beginRon}+{@code addRon}+{@code resolveRons} and
     * transitions to {@link RoundEnded}.
     */
    record Resolving() implements MatchPhase {}

    /**
     * Round finished. {@code winResults} is empty on exhaustive/abortive draw, otherwise
     * one entry per winner (length 1 for tsumo/single-ron, ≥2 for double/triple ron).
     */
    record RoundEnded(List<WinResult> winResults) implements MatchPhase {}

    /**
     * Between-rounds gate. Driver waits here for an external {@code advanceRound} call
     * so the host (Minecraft) can show end-of-round UI before dealing the next hand.
     */
    record BetweenRounds() implements MatchPhase {}

    /** Match ended (target reached, busted, or final round complete). */
    record MatchEnded() implements MatchPhase {}
}
