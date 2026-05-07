package com.themahjong.driver;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMatch;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongRound;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.Furiten;
import com.themahjong.yaku.TenpaiChecker;
import com.themahjong.yaku.WinResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;

/**
 * Mutable orchestrator that turns the immutable {@code TheMahjongRound} state machine
 * into a tickable, player-driven game loop. See package docs for surface overview.
 *
 * <p><b>Not thread-safe</b> — {@code advance} and {@code submitAction} must be called
 * from a single thread (Minecraft server thread).
 */
public final class TheMahjongDriver {

    private final ArrayList<MahjongPlayerInterface> players;
    private final Random random;

    private TheMahjongMatch match;
    private MatchPhase phase = new MatchPhase.NotStarted();

    /** Externally submitted actions, consumed by the next {@code chooseAction} poll. */
    private final Map<Integer, PlayerAction> submitted = new HashMap<>();

    /** Collected during {@link MatchPhase.AwaitingClaims}, drained when all seats answered. */
    private final Map<Integer, PlayerAction> claimDecisions = new HashMap<>();

    /** Last-completed RINSHAN_DRAW marker — needed to compute {@code rinshanDraw} in
     *  {@code WinContext} for the next discard's tsumo evaluation. Set when {@code draw()}
     *  pulls from the rinshan pile, cleared on the next {@code discard()}. */
    private boolean lastDrawWasRinshan;

    /** Seat with a pending riichi deposit commit, applied after the claim window
     *  resolves to anything other than a ron. */
    private OptionalInt pendingRiichiCommit = OptionalInt.empty();

    /** When {@code true}, {@link #startMatch()} and {@link #advanceRound} enter
     *  {@link MatchPhase.Dealing} for the configured sub-stage durations before
     *  yielding to the dealer's first draw. Default {@code false} preserves the
     *  pre-existing "round starts → AwaitingDraw" behaviour for tests and headless
     *  clients. */
    private boolean animationsEnabled;

    public TheMahjongDriver(
            TheMahjongMatch match,
            List<MahjongPlayerInterface> players,
            Random random) {
        Objects.requireNonNull(match, "match");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(random, "random");
        if (players.size() != match.playerCount())
            throw new IllegalArgumentException(
                    "players.size()=" + players.size()
                    + " does not match match.playerCount()=" + match.playerCount());
        this.match = match;
        this.players = new ArrayList<>(players);
        this.random = random;
    }

    /**
     * Restore constructor for persistence: rebuilds a driver mid-match from a
     * previously captured {@link Snapshot}. Transient inputs ({@code submitted},
     * {@code claimDecisions}) start empty — players in the middle of pressing a
     * button or a claim window must re-decide. The match itself, current phase,
     * and rinshan / pending-riichi flags are restored exactly.
     */
    public TheMahjongDriver(
            TheMahjongMatch match,
            List<MahjongPlayerInterface> players,
            Random random,
            Snapshot snapshot) {
        this(match, players, random);
        Objects.requireNonNull(snapshot, "snapshot");
        this.phase = Objects.requireNonNull(snapshot.phase(), "snapshot.phase");
        this.lastDrawWasRinshan = snapshot.lastDrawWasRinshan();
        this.pendingRiichiCommit = Objects.requireNonNull(
                snapshot.pendingRiichiCommit(), "snapshot.pendingRiichiCommit");
    }

    /** Captures the driver's mutable, non-transient state for external persistence. */
    public record Snapshot(
            MatchPhase phase,
            boolean lastDrawWasRinshan,
            OptionalInt pendingRiichiCommit) {
    }

    /** @return a snapshot of state that callers must persist to fully restore the driver. */
    public Snapshot snapshot() {
        return new Snapshot(phase, lastDrawWasRinshan, pendingRiichiCommit);
    }

    // ---- read accessors --------------------------------------------------

    public MatchPhase currentPhase() { return phase; }

    public TheMahjongMatch match() { return match; }

    public TheMahjongRound currentRound() {
        return match.currentRound().orElseThrow(
                () -> new IllegalStateException("no round in progress"));
    }

    public TheMahjongRuleSet rules() { return match.ruleSet(); }

    public MahjongPlayerInterface playerAt(int seat) { return players.get(seat); }

    public List<PlayerAction> legalActions(int seat) {
        if (phase instanceof MatchPhase.AwaitingDraw d) {
            if (d.seat() != seat) return List.of();
            return List.of(PlayerAction.Draw.INSTANCE);
        }
        if (phase instanceof MatchPhase.AwaitingDiscard d) {
            if (d.seat() != seat) return List.of();
            return computeDiscardActions(seat);
        }
        if (phase instanceof MatchPhase.AwaitingClaims c) {
            if (!c.pendingSeats().contains(seat)) return List.of();
            return computeClaimActions(seat, c.heldTile(), c.source());
        }
        return List.of();
    }

    // ---- driving the loop ------------------------------------------------

    public void startMatch() {
        if (!(phase instanceof MatchPhase.NotStarted)) {
            throw new IllegalStateException("startMatch() already called");
        }
        match = match.validate().startRound(random);
        TheMahjongRound round = currentRound();
        broadcast(new MatchEvent.RoundStarted(
                round.dealerSeat(), round.roundWind(), round.handNumber(), round.honba()));
        phase = animationsEnabled
                ? new MatchPhase.Dealing(MatchPhase.Stage.WALL_BUILDING, 0.0)
                : new MatchPhase.AwaitingDraw(round.currentTurnSeat(), false);
    }

    /**
     * Toggle the visual round-start animation phase ({@link MatchPhase.Dealing}).
     * Defaults to {@code false}: round-starts go straight to {@link MatchPhase.AwaitingDraw},
     * preserving headless / test behaviour. Hosts that want to render a deal animation
     * (e.g. the Minecraft mahjong table) set this {@code true} immediately after
     * constructing the driver. Affects subsequent {@code startMatch} / {@code advanceRound}
     * calls; does not retroactively change the current phase.
     */
    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
    }

    public boolean animationsEnabled() {
        return animationsEnabled;
    }

    public void advance(double deltaSeconds) {
        Set<Integer> polledThisFrame = new HashSet<>();
        boolean progress = true;
        while (progress) {
            progress = advanceOnce(deltaSeconds, polledThisFrame);
        }
    }

    private boolean advanceOnce(double deltaSeconds, Set<Integer> polledThisFrame) {
        if (phase instanceof MatchPhase.NotStarted) return false;
        if (phase instanceof MatchPhase.RoundEnded) return false;
        if (phase instanceof MatchPhase.MatchEnded) return false;
        if (phase instanceof MatchPhase.BetweenRounds) return false;
        if (phase instanceof MatchPhase.Resolving) {
            applyResolving();
            return true;
        }
        if (phase instanceof MatchPhase.Dealing dealing) {
            return advanceDealing(dealing, deltaSeconds);
        }
        if (phase instanceof MatchPhase.AwaitingDraw ad) {
            PlayerAction a = pollSeat(ad.seat(), deltaSeconds, polledThisFrame);
            if (a == null) return false;
            applyDrawPhase(ad.seat(), a);
            return true;
        }
        if (phase instanceof MatchPhase.AwaitingDiscard ad) {
            PlayerAction a = pollSeat(ad.seat(), deltaSeconds, polledThisFrame);
            if (a == null) return false;
            applyDiscardPhase(ad.seat(), a);
            return true;
        }
        if (phase instanceof MatchPhase.AwaitingClaims ac) {
            boolean any = false;
            for (Integer s : ac.pendingSeats()) {
                if (claimDecisions.containsKey(s)) continue;
                PlayerAction a = pollSeat(s, deltaSeconds, polledThisFrame);
                if (a == null) continue;
                claimDecisions.put(s, a);
                any = true;
            }
            if (claimDecisions.size() >= ac.pendingSeats().size()) {
                resolveClaimPriority(ac);
                return true;
            }
            return any;
        }
        throw new IllegalStateException("unhandled phase: " + phase);
    }

    public void submitAction(int seat, PlayerAction action) {
        Objects.requireNonNull(action, "action");
        List<PlayerAction> legal = legalActions(seat);
        if (!legal.contains(action)) {
            throw new IllegalArgumentException(
                    "action not legal for seat " + seat + " in phase " + phase + ": " + action);
        }
        submitted.put(seat, action);
    }

    public void advanceRound(boolean renchan, int nextHonba) {
        if (!(phase instanceof MatchPhase.RoundEnded) && !(phase instanceof MatchPhase.BetweenRounds)) {
            throw new IllegalStateException("advanceRound() requires RoundEnded/BetweenRounds, was " + phase);
        }
        match = match.advanceRound(random, renchan, nextHonba);
        if (match.state() == TheMahjongMatch.State.ENDED) {
            match = match.applyFinalDeposits();
            phase = new MatchPhase.MatchEnded();
            broadcast(new MatchEvent.MatchEnded());
            return;
        }
        TheMahjongRound round = currentRound();
        lastDrawWasRinshan = false;
        pendingRiichiCommit = OptionalInt.empty();
        broadcast(new MatchEvent.RoundStarted(
                round.dealerSeat(), round.roundWind(), round.handNumber(), round.honba()));
        phase = animationsEnabled
                ? new MatchPhase.Dealing(MatchPhase.Stage.WALL_BUILDING, 0.0)
                : new MatchPhase.AwaitingDraw(round.currentTurnSeat(), false);
    }

    public void replacePlayer(int seat, MahjongPlayerInterface newImpl) {
        Objects.requireNonNull(newImpl, "newImpl");
        players.set(seat, newImpl);
        submitted.remove(seat);
        claimDecisions.remove(seat);
    }

    // ---- internal: polling ----------------------------------------------

    private PlayerAction pollSeat(int seat, double deltaSeconds, Set<Integer> polledThisFrame) {
        PlayerAction queued = submitted.remove(seat);
        if (queued != null) return queued;
        double dt = polledThisFrame.add(seat) ? deltaSeconds : 0.0;
        DecisionRequest req = new DecisionRequest(
                seat, phase, legalActions(seat), currentRound(), rules());
        return players.get(seat).chooseAction(req, dt).orElse(null);
    }

    // ---- internal: phase transitions -----------------------------------

    /**
     * Drives the {@link MatchPhase.Dealing} clock. Adds {@code deltaSeconds} to the
     * stage's elapsed budget and walks through completed stages within a single call,
     * exiting to {@link MatchPhase.AwaitingDraw} when the final stage finishes.
     * Returns {@code true} only when the phase leaves Dealing (so the outer
     * {@code advance} loop iterates into the new phase); returns {@code false}
     * while still in Dealing, ending the loop without re-entering this method.
     */
    private boolean advanceDealing(MatchPhase.Dealing dealing, double deltaSeconds) {
        double elapsed = dealing.elapsed() + Math.max(0.0, deltaSeconds);
        MatchPhase.Stage stage = dealing.stage();
        MatchPhase.Stage[] stages = MatchPhase.Stage.values();
        while (elapsed >= stage.duration()) {
            elapsed -= stage.duration();
            int next = stage.ordinal() + 1;
            if (next >= stages.length) {
                phase = new MatchPhase.AwaitingDraw(currentRound().currentTurnSeat(), false);
                return true;
            }
            stage = stages[next];
        }
        phase = new MatchPhase.Dealing(stage, elapsed);
        return false;
    }

    private void applyDrawPhase(int seat, PlayerAction action) {
        if (!(action instanceof PlayerAction.Draw)) {
            throw new IllegalStateException("AwaitingDraw expects Draw, got " + action);
        }
        TheMahjongRound round = currentRound();
        boolean fromRinshan = (round.state() == TheMahjongRound.State.RINSHAN_DRAW);
        round = round.draw();
        match = matchWithRound(round);
        lastDrawWasRinshan = fromRinshan;
        phase = new MatchPhase.AwaitingDiscard(seat);
    }

    private void applyDiscardPhase(int seat, PlayerAction action) {
        TheMahjongRound round = currentRound();
        if (action instanceof PlayerAction.Discard d) {
            round = round.discard(d.tile(), rules());
            match = matchWithRound(round);
            lastDrawWasRinshan = false;
            broadcast(new MatchEvent.Discarded(seat, d.tile(), false));
            enterPostDiscardPhase();
        } else if (action instanceof PlayerAction.DiscardWithRiichi d) {
            round = round.declareRiichiIntent(rules());
            match = matchWithRound(round);
            broadcast(new MatchEvent.RiichiDeclared(seat));
            round = round.discard(d.tile(), rules());
            match = matchWithRound(round);
            lastDrawWasRinshan = false;
            pendingRiichiCommit = OptionalInt.of(seat);
            broadcast(new MatchEvent.Discarded(seat, d.tile(), true));
            enterPostDiscardPhase();
        } else if (action instanceof PlayerAction.Ankan a) {
            round = round.declareAnkan(a.handTiles(), rules());
            match = matchWithRound(round);
            broadcast(new MatchEvent.MeldDeclared(seat, lastMeldOf(seat)));
            phase = new MatchPhase.AwaitingDraw(seat, true);
        } else if (action instanceof PlayerAction.Kakan k) {
            round = round.declareKakan(k.upgradedFrom(), k.addedTile(), rules());
            match = matchWithRound(round);
            broadcast(new MatchEvent.MeldDeclared(seat, lastMeldOf(seat)));
            enterPostKakanPhase();
        } else if (action instanceof PlayerAction.DeclareKita k) {
            round = round.declareKita(k.tile());
            match = matchWithRound(round);
            broadcast(new MatchEvent.KitaDeclared(seat, k.tile()));
            enterPostKitaPhase();
        } else if (action instanceof PlayerAction.DeclareTsumo t) {
            round = round.declareWin(seat, seat, t.result());
            match = matchWithRound(round);
            phase = new MatchPhase.RoundEnded(List.of(t.result()));
            broadcast(new MatchEvent.RoundEnded(List.of(t.result())));
        } else if (action instanceof PlayerAction.KyuushuAbort) {
            round = round.abortiveDraw(rules());
            match = matchWithRound(round);
            phase = new MatchPhase.RoundEnded(List.of());
            broadcast(new MatchEvent.RoundEnded(List.of()));
        } else {
            throw new IllegalStateException("AwaitingDiscard does not accept " + action);
        }
    }

    /** After a discard transitions the round to CLAIM_WINDOW, decide whether to enter
     *  {@link MatchPhase.AwaitingClaims} or skip directly to the next draw / exhaustive draw. */
    private void enterPostDiscardPhase() {
        TheMahjongRound round = currentRound();
        if (round.state() == TheMahjongRound.State.ENDED) {
            phase = new MatchPhase.RoundEnded(List.of());
            return;
        }
        TheMahjongTile held = ((TheMahjongRound.ActiveTile.HeldDiscard) round.activeTile()).tile();
        Set<Integer> pending = computeClaimEligibleSeats(held, MatchPhase.ClaimSource.DISCARD);
        if (pending.isEmpty()) {
            commitPendingRiichiAndAdvance();
        } else {
            claimDecisions.clear();
            phase = new MatchPhase.AwaitingClaims(pending, held, MatchPhase.ClaimSource.DISCARD);
        }
    }

    private void enterPostKitaPhase() {
        TheMahjongRound round = currentRound();
        TheMahjongTile held = ((TheMahjongRound.ActiveTile.HeldKita) round.activeTile()).tile();
        Set<Integer> pending = computeClaimEligibleSeats(held, MatchPhase.ClaimSource.KITA);
        if (pending.isEmpty()) {
            TheMahjongRound updated = round.skipKitaClaims();
            match = matchWithRound(updated);
            phase = new MatchPhase.AwaitingDraw(updated.currentTurnSeat(), true);
        } else {
            claimDecisions.clear();
            phase = new MatchPhase.AwaitingClaims(pending, held, MatchPhase.ClaimSource.KITA);
        }
    }

    private void enterPostKakanPhase() {
        TheMahjongRound round = currentRound();
        TheMahjongTile held = ((TheMahjongRound.ActiveTile.HeldKakan) round.activeTile()).tile();
        Set<Integer> pending = computeClaimEligibleSeats(held, MatchPhase.ClaimSource.KAKAN);
        if (pending.isEmpty()) {
            TheMahjongRound updated = round.skipKakanClaims();
            match = matchWithRound(updated);
            phase = new MatchPhase.AwaitingDraw(updated.currentTurnSeat(), true);
        } else {
            claimDecisions.clear();
            phase = new MatchPhase.AwaitingClaims(pending, held, MatchPhase.ClaimSource.KAKAN);
        }
    }

    private void resolveClaimPriority(MatchPhase.AwaitingClaims aw) {
        TheMahjongRound round = currentRound();
        int discarder = round.claimSourceSeat().orElseThrow();

        // Apply temporary furiten to seats that COULD have ron'd on the held
        // tile but chose Pass — without this, a player who passes on a ron
        // can immediately ron the next opponent's discard, which is illegal.
        // {@link TheMahjongRound#declineRon} also flips the riichi player's
        // permanent-furiten flag if applicable.
        for (Integer s : aw.pendingSeats()) {
            PlayerAction a = claimDecisions.get(s);
            if (!(a instanceof PlayerAction.Pass)) continue;
            TheMahjongPlayer player = round.players().get(s);
            if (com.themahjong.yaku.Furiten.canRon(player, aw.heldTile())) {
                round = round.declineRon(s, rules());
            }
        }
        match = matchWithRound(round);

        // Collect ron seats in kamicha-priority order from the discarder. DeclareRon and
        // DeclareChankan both lead to the same ron path; DeclareChankan is only legal from
        // a KAKAN claim source.
        List<Integer> ronSeats = new ArrayList<>();
        for (int offset = 1; offset < players.size(); offset++) {
            int s = (discarder + offset) % players.size();
            PlayerAction a = claimDecisions.get(s);
            if (a instanceof PlayerAction.DeclareRon
                    || a instanceof PlayerAction.DeclareChankan) ronSeats.add(s);
        }

        if (!ronSeats.isEmpty()) {
            applyRonResolution(ronSeats);
            return;
        }

        // Riichi commit succeeds when no ron occurred, even if pon/chi/kan claims do.
        commitPendingRiichi();

        // Pon/Daiminkan priority over Chi.
        for (int offset = 1; offset < players.size(); offset++) {
            int s = (discarder + offset) % players.size();
            PlayerAction a = claimDecisions.get(s);
            if (a instanceof PlayerAction.Daiminkan k) {
                TheMahjongRound updated = round.claimDaiminkan(s, k.handTiles(), rules());
                match = matchWithRound(updated);
                broadcast(new MatchEvent.MeldDeclared(s, lastMeldOf(s)));
                claimDecisions.clear();
                phase = new MatchPhase.AwaitingDraw(s, true);
                return;
            }
            if (a instanceof PlayerAction.Pon p) {
                TheMahjongRound updated = round.claimPon(s, p.handTiles());
                match = matchWithRound(updated);
                broadcast(new MatchEvent.MeldDeclared(s, lastMeldOf(s)));
                claimDecisions.clear();
                phase = new MatchPhase.AwaitingDiscard(s);
                return;
            }
        }
        for (int offset = 1; offset < players.size(); offset++) {
            int s = (discarder + offset) % players.size();
            PlayerAction a = claimDecisions.get(s);
            if (a instanceof PlayerAction.Chi c) {
                TheMahjongRound updated = round.claimChi(s, c.handTiles(), rules());
                match = matchWithRound(updated);
                broadcast(new MatchEvent.MeldDeclared(s, lastMeldOf(s)));
                claimDecisions.clear();
                phase = new MatchPhase.AwaitingDiscard(s);
                return;
            }
        }

        // Everyone passed.
        switch (aw.source()) {
            case KITA -> {
                TheMahjongRound updated = round.skipKitaClaims();
                match = matchWithRound(updated);
                claimDecisions.clear();
                phase = new MatchPhase.AwaitingDraw(updated.currentTurnSeat(), true);
            }
            case KAKAN -> {
                TheMahjongRound updated = round.skipKakanClaims();
                match = matchWithRound(updated);
                claimDecisions.clear();
                phase = new MatchPhase.AwaitingDraw(updated.currentTurnSeat(), true);
            }
            case DISCARD -> {
                TheMahjongRound updated = round.skipClaims();
                match = matchWithRound(updated);
                claimDecisions.clear();
                checkExhaustiveDraw(updated);
            }
        }
    }

    /** Pull a WinResult from either DeclareRon or DeclareChankan. */
    private static WinResult ronResultOf(PlayerAction a) {
        if (a instanceof PlayerAction.DeclareRon r) return r.result();
        if (a instanceof PlayerAction.DeclareChankan c) return c.result();
        throw new IllegalStateException("not a ron-bearing action: " + a);
    }

    private void applyRonResolution(List<Integer> ronSeats) {
        TheMahjongRound round = currentRound();
        int discarder = round.claimSourceSeat().orElseThrow();

        // Sanchahou abort under Tenhou rules.
        if (ronSeats.size() == 3 && rules().sanchahouAbortive()) {
            round = round.abortiveDraw(rules());
            match = matchWithRound(round);
            claimDecisions.clear();
            phase = new MatchPhase.RoundEnded(List.of());
            broadcast(new MatchEvent.RoundEnded(List.of()));
            return;
        }

        // Drop secondary rons when double ron is disallowed.
        if (ronSeats.size() > 1 && !rules().doubleRonAllowed()) {
            ronSeats = ronSeats.subList(0, 1);
        }

        if (ronSeats.size() == 1) {
            int winner = ronSeats.get(0);
            WinResult wr = ronResultOf(claimDecisions.get(winner));
            round = round.declareWin(winner, discarder, wr);
            match = matchWithRound(round);
            claimDecisions.clear();
            phase = new MatchPhase.RoundEnded(List.of(wr));
            broadcast(new MatchEvent.RoundEnded(List.of(wr)));
            return;
        }

        // 2+ rons, allowed: stage through Resolving so multi-ron settlement is observable.
        // Stash the ordered seats as a transient by leaving claimDecisions populated — applyResolving
        // will read them back.
        phase = new MatchPhase.Resolving();
    }

    private void applyResolving() {
        TheMahjongRound round = currentRound();
        int discarder = round.claimSourceSeat().orElseThrow();
        List<Integer> ronSeats = new ArrayList<>();
        for (int offset = 1; offset < players.size(); offset++) {
            int s = (discarder + offset) % players.size();
            PlayerAction a = claimDecisions.get(s);
            if (a instanceof PlayerAction.DeclareRon
                    || a instanceof PlayerAction.DeclareChankan) ronSeats.add(s);
        }
        List<WinResult> results = new ArrayList<>();
        WinResult first = ronResultOf(claimDecisions.get(ronSeats.get(0)));
        round = round.beginRon(ronSeats.get(0), first, rules());
        results.add(first);
        for (int i = 1; i < ronSeats.size(); i++) {
            WinResult wr = ronResultOf(claimDecisions.get(ronSeats.get(i)));
            round = round.addRon(ronSeats.get(i), wr);
            results.add(wr);
        }
        round = round.resolveRons();
        match = matchWithRound(round);
        claimDecisions.clear();
        phase = new MatchPhase.RoundEnded(List.copyOf(results));
        broadcast(new MatchEvent.RoundEnded(List.copyOf(results)));
    }

    private void checkExhaustiveDraw(TheMahjongRound round) {
        // After skipClaims, the engine is in SETUP awaiting next draw. If wall is empty,
        // exhaustive-draw the round.
        if (round.liveWall().isEmpty()) {
            TheMahjongRound ended = round.exhaustiveDraw(rules());
            match = matchWithRound(ended);
            phase = new MatchPhase.RoundEnded(List.of());
            broadcast(new MatchEvent.RoundEnded(List.of()));
            return;
        }
        phase = new MatchPhase.AwaitingDraw(round.currentTurnSeat(), false);
    }

    private void commitPendingRiichiAndAdvance() {
        // No claims to wait on — riichi (if any) commits, then advance.
        commitPendingRiichi();
        TheMahjongRound round = currentRound();
        TheMahjongRound updated = round.skipClaims();
        match = matchWithRound(updated);
        checkExhaustiveDraw(updated);
    }

    private void commitPendingRiichi() {
        if (pendingRiichiCommit.isEmpty()) return;
        TheMahjongRound round = currentRound().commitRiichiDeposit(rules());
        match = matchWithRound(round);
        pendingRiichiCommit = OptionalInt.empty();
    }

    // ---- internal: legal-action computation ------------------------------

    private List<PlayerAction> computeDiscardActions(int seat) {
        TheMahjongRound round = currentRound();
        TheMahjongPlayer player = round.players().get(seat);
        List<PlayerAction> out = new ArrayList<>();

        // Tsumo
        boolean rinshan = lastDrawWasRinshan;
        Optional<WinResult> tsumo = WinResultBuilder.tryTsumo(round, rules(), seat, rinshan);
        tsumo.filter(r -> !r.yaku().isEmpty() || !r.yakuman().isEmpty())
                .ifPresent(r -> out.add(new PlayerAction.DeclareTsumo(r)));

        // Kyuushu kyuuhai
        if (rules().abortiveDrawsAllowed() && round.isKyuushuEligible()) {
            out.add(PlayerAction.KyuushuAbort.INSTANCE);
        }

        // Discards (every distinct tile in hand). For a riichi player only the just-drawn
        // tile is legal; the engine validates this in discard().
        Set<TileKey> seen = new HashSet<>();
        for (TheMahjongTile t : player.currentHand()) {
            if (!seen.add(TileKey.of(t))) continue;
            try {
                round.discard(t, rules());
                out.add(new PlayerAction.Discard(t));
            } catch (RuntimeException ignored) { /* illegal under kuikae or riichi */ }
        }

        // Riichi-with-discard. Closed hand, not already in riichi, sufficient points,
        // 4+ wall tiles remaining (so opponents have a turn after).
        if (!player.riichi() && !player.handOpen()
                && player.points() >= 1000
                && round.liveWall().size() >= 4) {
            // Riichi requires the post-discard hand to be tenpai. The engine's
            // discard() / declareRiichiIntent() don't validate that on their own —
            // they trust the caller — so we check here, and only surface tiles
            // whose post-discard hand still has a winning wait.
            seen.clear();
            for (TheMahjongTile t : player.currentHand()) {
                if (!seen.add(TileKey.of(t))) continue;
                try {
                    TheMahjongRound after = round.declareRiichiIntent(rules()).discard(t, rules());
                    if (TenpaiChecker.inTenpai(after.players().get(seat))) {
                        out.add(new PlayerAction.DiscardWithRiichi(t));
                    }
                } catch (RuntimeException ignored) { /* illegal */ }
            }
        }

        // Ankan: any 4-of-a-kind in hand.
        Map<TileKey, List<TheMahjongTile>> byKind = groupBySuitRank(player.currentHand());
        for (var e : byKind.entrySet()) {
            if (e.getValue().size() >= 4) {
                List<TheMahjongTile> four = e.getValue().subList(0, 4);
                try {
                    round.declareAnkan(four, rules());
                    out.add(new PlayerAction.Ankan(List.copyOf(four)));
                } catch (RuntimeException ignored) {}
            }
        }

        // Kakan: any tile in hand matching an existing Pon meld.
        for (TheMahjongMeld m : player.melds()) {
            if (!(m instanceof TheMahjongMeld.Pon pon)) continue;
            TheMahjongTile ref = pon.tiles().get(0);
            for (TheMahjongTile t : player.currentHand()) {
                if (!t.matchesSuitRank(ref)) continue;
                try {
                    round.declareKakan(pon, t, rules());
                    out.add(new PlayerAction.Kakan(pon, t));
                    break;
                } catch (RuntimeException ignored) {}
            }
        }

        // Kita (sanma only): any wind tile in hand with rank > playerCount.
        if (players.size() == 3) {
            seen.clear();
            for (TheMahjongTile t : player.currentHand()) {
                if (t.suit() != TheMahjongTile.Suit.WIND) continue;
                if (t.rank() <= players.size()) continue;
                if (!seen.add(TileKey.of(t))) continue;
                try {
                    round.declareKita(t);
                    out.add(new PlayerAction.DeclareKita(t));
                } catch (RuntimeException ignored) {}
            }
        }

        return List.copyOf(out);
    }

    private List<PlayerAction> computeClaimActions(int seat, TheMahjongTile held, MatchPhase.ClaimSource source) {
        TheMahjongRound round = currentRound();
        TheMahjongPlayer player = round.players().get(seat);
        int fromSeat = round.claimSourceSeat().orElseThrow();
        List<PlayerAction> out = new ArrayList<>();
        out.add(PlayerAction.Pass.INSTANCE);

        // Ron / Chankan
        if (Furiten.canRon(player, held)) {
            if (source == MatchPhase.ClaimSource.KAKAN) {
                WinResultBuilder.tryChankan(round, rules(), seat, held, fromSeat)
                        .ifPresent(r -> out.add(new PlayerAction.DeclareChankan(r)));
            } else {
                WinResultBuilder.tryRon(round, rules(), seat, held, fromSeat)
                        .ifPresent(r -> out.add(new PlayerAction.DeclareRon(r)));
            }
        }

        // Pon / Daiminkan / Chi only legal on plain discards.
        if (source != MatchPhase.ClaimSource.DISCARD) return List.copyOf(out);
        if (player.riichi()) return List.copyOf(out);

        // Pon / Daiminkan — enumerate distinct multisets when the hand has red-five duplicates.
        List<TheMahjongTile> matching = new ArrayList<>();
        for (TheMahjongTile t : player.currentHand()) {
            if (t.matchesSuitRank(held)) matching.add(t);
        }
        if (matching.size() >= 2) {
            for (List<TheMahjongTile> pair : sizeKSubMultisets(matching, 2)) {
                out.add(new PlayerAction.Pon(pair));
            }
        }
        if (matching.size() >= 3) {
            for (List<TheMahjongTile> triple : sizeKSubMultisets(matching, 3)) {
                out.add(new PlayerAction.Daiminkan(triple));
            }
        }

        // Chi (kamicha only, when enabled)
        int kamicha = (fromSeat + 1) % players.size();
        if (seat == kamicha && !rules().chiDisabled() && held.suit().isNumber()) {
            out.addAll(computeChiOptions(player, held));
        }

        return List.copyOf(out);
    }

    /**
     * Distinct size-{@code k} sub-multisets of {@code tiles}. Two picks are considered
     * equivalent when their multiset of (suit, rank, redDora) keys matches — so a hand
     * with [5m, 5m, 5m-red] yields two distinct pon pairs ({5m,5m} and {5m,5m-red}),
     * while [5m, 5m, 5m] yields one.
     */
    static List<List<TheMahjongTile>> sizeKSubMultisets(List<TheMahjongTile> tiles, int k) {
        java.util.LinkedHashMap<TheMahjongTile, Integer> counts = new java.util.LinkedHashMap<>();
        for (TheMahjongTile t : tiles) counts.merge(t, 1, Integer::sum);
        List<java.util.Map.Entry<TheMahjongTile, Integer>> kinds = new ArrayList<>(counts.entrySet());
        List<List<TheMahjongTile>> out = new ArrayList<>();
        subMultisetBacktrack(kinds, 0, k, new ArrayList<>(), out);
        return out;
    }

    private static void subMultisetBacktrack(
            List<java.util.Map.Entry<TheMahjongTile, Integer>> kinds, int idx, int need,
            List<TheMahjongTile> picked, List<List<TheMahjongTile>> out) {
        if (need == 0) { out.add(List.copyOf(picked)); return; }
        if (idx >= kinds.size()) return;
        var e = kinds.get(idx);
        int max = Math.min(need, e.getValue());
        for (int take = 0; take <= max; take++) {
            for (int j = 0; j < take; j++) picked.add(e.getKey());
            subMultisetBacktrack(kinds, idx + 1, need - take, picked, out);
            for (int j = 0; j < take; j++) picked.remove(picked.size() - 1);
        }
    }

    private List<PlayerAction.Chi> computeChiOptions(TheMahjongPlayer player, TheMahjongTile held) {
        List<PlayerAction.Chi> out = new ArrayList<>();
        TheMahjongTile.Suit suit = held.suit();
        int rank = held.rank();
        int max = suit.maxRank();

        // For each shape, look up the two needed ranks; pick representative tiles from hand.
        // RYANMEN_LOW: held is the low tile (X), partners are X+1 and X+2.
        // RYANMEN_HIGH: held is the high tile (X), partners are X-1 and X-2.
        // KANCHAN: held is the middle (X), partners are X-1 and X+1.
        // PENCHAN: held=3 with [1,2], or held=7 with [8,9].
        addChi(out, player, suit, rank, rank + 1, rank + 2, max, PlayerAction.ChiShape.RYANMEN_LOW);
        addChi(out, player, suit, rank, rank - 2, rank - 1, max, PlayerAction.ChiShape.RYANMEN_HIGH);
        addChi(out, player, suit, rank, rank - 1, rank + 1, max, PlayerAction.ChiShape.KANCHAN);
        if (rank == 3) addChi(out, player, suit, rank, 1, 2, max, PlayerAction.ChiShape.PENCHAN);
        if (rank == 7) addChi(out, player, suit, rank, 8, 9, max, PlayerAction.ChiShape.PENCHAN);
        // Dedup: ryanmen variants can collapse with penchan above; keep first match.
        return out;
    }

    private void addChi(
            List<PlayerAction.Chi> out, TheMahjongPlayer player,
            TheMahjongTile.Suit suit, int heldRank, int rA, int rB, int max,
            PlayerAction.ChiShape shape) {
        if (rA < 1 || rA > max || rB < 1 || rB > max) return;
        if (rA == heldRank || rB == heldRank) return;
        // Suppress duplicates with edge waits (RYANMEN_LOW at rank=8 would dup with PENCHAN at 7).
        if (shape == PlayerAction.ChiShape.RYANMEN_LOW && heldRank == 7) return; // [7,8,9] handled as PENCHAN
        if (shape == PlayerAction.ChiShape.RYANMEN_HIGH && heldRank == 3) return; // [1,2,3] handled as PENCHAN
        if (shape == PlayerAction.ChiShape.RYANMEN_LOW && (heldRank + 2) > max) return;
        if (shape == PlayerAction.ChiShape.RYANMEN_HIGH && (heldRank - 2) < 1) return;
        // Enumerate every distinct (tile-of-rA, tile-of-rB) pair — separates red-five from
        // non-red-five variants when the hand contains both.
        List<TheMahjongTile> aOptions = distinctTilesOfRank(player, suit, rA);
        List<TheMahjongTile> bOptions = distinctTilesOfRank(player, suit, rB);
        for (TheMahjongTile a : aOptions) {
            for (TheMahjongTile b : bOptions) {
                out.add(new PlayerAction.Chi(shape, List.of(a, b)));
            }
        }
    }

    private static List<TheMahjongTile> distinctTilesOfRank(
            TheMahjongPlayer player, TheMahjongTile.Suit suit, int rank) {
        Set<TheMahjongTile> seen = new LinkedHashSet<>();
        for (TheMahjongTile t : player.currentHand()) {
            if (t.suit() == suit && t.rank() == rank) seen.add(t);
        }
        return new ArrayList<>(seen);
    }

    /**
     * Seats with at least one non-Pass legal action against the held tile. Used to decide
     * whether to enter {@link MatchPhase.AwaitingClaims} or skip the window entirely.
     */
    private Set<Integer> computeClaimEligibleSeats(TheMahjongTile held, MatchPhase.ClaimSource source) {
        TheMahjongRound round = currentRound();
        int fromSeat = round.claimSourceSeat().orElseThrow();
        Set<Integer> out = new LinkedHashSet<>();
        for (int s = 0; s < players.size(); s++) {
            if (s == fromSeat) continue;
            List<PlayerAction> opts = computeClaimActions(s, held, source);
            // opts always contains Pass; eligible iff there's at least one non-Pass entry.
            if (opts.size() > 1) out.add(s);
        }
        return out;
    }

    // ---- helpers --------------------------------------------------------

    private TheMahjongMatch matchWithRound(TheMahjongRound updated) {
        // Match state stays IN_ROUND even when the round itself is ENDED — the match
        // contract is that advanceRound() consumes an ENDED round from IN_ROUND state.
        return new TheMahjongMatch(
                match.playerCount(), match.startingPoints(), match.targetPoints(),
                match.roundCount(), TheMahjongMatch.State.IN_ROUND, match.tileSet(),
                match.ruleSet(), match.completedRounds(), updated);
    }

    private TheMahjongMeld lastMeldOf(int seat) {
        var melds = currentRound().players().get(seat).melds();
        return melds.get(melds.size() - 1);
    }

    private void broadcast(MatchEvent event) {
        for (MahjongPlayerInterface p : players) p.onEvent(event);
    }

    private static Map<TileKey, List<TheMahjongTile>> groupBySuitRank(List<TheMahjongTile> tiles) {
        Map<TileKey, List<TheMahjongTile>> out = new HashMap<>();
        for (TheMahjongTile t : tiles) {
            // Group by suit+rank only; red and non-red five sit in the same bucket.
            TileKey k = new TileKey(t.suit(), t.rank(), false);
            out.computeIfAbsent(k, x -> new ArrayList<>()).add(t);
        }
        return out;
    }

    private record TileKey(TheMahjongTile.Suit suit, int rank, boolean redDora) {
        static TileKey of(TheMahjongTile t) { return new TileKey(t.suit(), t.rank(), t.redDora()); }
    }
}
