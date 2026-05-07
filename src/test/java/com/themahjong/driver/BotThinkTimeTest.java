package com.themahjong.driver;

import com.themahjong.TheMahjongMatch;
import com.themahjong.driver.bots.StupidPassiveBot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that bot think-time actually delays action commits — not just that the
 * configuration compiles. Drives the bot through {@code chooseAction} polls and asserts
 * on phase progression as deltas accumulate.
 */
class BotThinkTimeTest {

    private static StupidPassiveBot slowBot() {
        return new StupidPassiveBot(
                /*draw=*/ 1.0, /*discard=*/ 1.0, /*claim=*/ 0.5,
                /*win=*/  1.0, /*pass=*/    0.5, /*riichi=*/ 1.5,
                /*abort=*/ 1.0);
    }

    private TheMahjongDriver newDriverWithBot(MahjongPlayerInterface seat0) {
        TheMahjongMatch match = TheMahjongMatch.defaultTenhou().withPlayerCount(4).validate();
        List<MahjongPlayerInterface> players = new ArrayList<>();
        players.add(seat0);
        for (int i = 1; i < 4; i++) players.add(new StupidPassiveBot());
        return new TheMahjongDriver(match, players, new Random(42));
    }

    @Test
    void slowBotDoesNotDrawUntilThresholdElapses() {
        TheMahjongDriver d = newDriverWithBot(slowBot());
        d.startMatch();
        // Dealer is seat 0 → AwaitingDraw(0). With drawSeconds=1.0, partial advances
        // should not progress past AwaitingDraw.
        assertInstanceOf(MatchPhase.AwaitingDraw.class, d.currentPhase());
        d.advance(0.3);
        assertInstanceOf(MatchPhase.AwaitingDraw.class, d.currentPhase(),
                "0.3s elapsed; threshold 1.0s — must still be awaiting draw");
        d.advance(0.3);
        assertInstanceOf(MatchPhase.AwaitingDraw.class, d.currentPhase(),
                "0.6s elapsed; threshold 1.0s — must still be awaiting draw");
        // Crossing the threshold lets the draw commit.
        d.advance(0.5);
        assertFalse(d.currentPhase() instanceof MatchPhase.AwaitingDraw,
                "1.1s elapsed; threshold 1.0s — must have advanced past draw");
    }

    @Test
    void instantBotCommitsOnFirstAdvance() {
        TheMahjongDriver d = newDriverWithBot(new StupidPassiveBot());
        d.startMatch();
        d.advance(0.0);
        assertFalse(d.currentPhase() instanceof MatchPhase.AwaitingDraw,
                "instant bot should advance past AwaitingDraw on dt=0");
    }

    @Test
    void thresholdResetsBetweenPhases() {
        // Per-request reset: time accumulated under one phase must not carry over to the
        // next. After the draw commits on tick 1.1s, the AwaitingDiscard phase starts
        // fresh with 0s elapsed; despite total wall-clock being 1.1s, the discard timer
        // (also 1.0s threshold) has not elapsed yet.
        TheMahjongDriver d = newDriverWithBot(slowBot());
        d.startMatch();
        d.advance(1.1); // commits draw, transitions to AwaitingDiscard with elapsed=0
        assertInstanceOf(MatchPhase.AwaitingDiscard.class, d.currentPhase());
        d.advance(0.5);
        assertInstanceOf(MatchPhase.AwaitingDiscard.class, d.currentPhase(),
                "0.5s on the new request; threshold 1.0s — must still be awaiting discard");
        d.advance(0.6);
        assertFalse(d.currentPhase() instanceof MatchPhase.AwaitingDiscard,
                "1.1s on the new request — discard must have committed");
    }

    @Test
    void humanLikePresetIsValid() {
        // Sanity: factory produces a bot the driver accepts and that doesn't insta-commit.
        StupidPassiveBot h = StupidPassiveBot.humanLike();
        TheMahjongDriver d = newDriverWithBot(h);
        d.startMatch();
        d.advance(0.0);
        assertInstanceOf(MatchPhase.AwaitingDraw.class, d.currentPhase(),
                "humanLike bot should not commit at dt=0");
    }
}
