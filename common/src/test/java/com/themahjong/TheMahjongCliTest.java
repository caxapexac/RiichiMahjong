package com.themahjong;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TheMahjongCliTest {

    @Test
    void renderMatchShowsCreatedAndStartedStates() {
        TheMahjongMatch createdMatch = TheMahjongMatch.defaults().validate();
        String createdOutput = TheMahjongCli.renderMatch(createdMatch);
        TheMahjongMatch startedMatch = createdMatch.startRound();

        String startedOutput = TheMahjongCli.renderMatch(startedMatch);

        assertTrue(createdOutput.contains("Match state: NOT_STARTED"));
        assertTrue(createdOutput.contains("Current round: none"));
        assertTrue(startedOutput.contains("Match state: IN_ROUND"));
        assertTrue(startedOutput.contains("Current round state: SETUP"));
        assertTrue(startedOutput.contains("Player points: seat 0=30000"));
    }
}
