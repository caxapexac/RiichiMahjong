package com.mahjongcore;

import com.mahjongcore.rules.ClaimWindowRules;

import java.util.Arrays;

/**
 * Authoritative turn state.
 */
public final class MahjongGameState {

    public enum TurnPhase {
        MUST_DRAW,
        MUST_DISCARD,
        CLAIM_WINDOW
    }

    public enum ActionResult {
        OK,
        NOT_YOUR_TURN,
        WRONG_PHASE,
        EMPTY_WALL,
        ILLEGAL_DISCARD,
        MISSING_DRAWN_TILE
    }

    public int handNumber;
    /** Consecutive dealer-continues count; +300 per honba on wins, resets to 0 on non-dealer win. */
    public int honba;
    /** Accumulated riichi bet sticks on the table (1000 pts per declaration); collected by the next winner. */
    public int riichiPot;
    public int currentTurnSeat;
    public TurnPhase phase = TurnPhase.MUST_DRAW;
    public int lastDrawnCode = -1;
    public boolean lastDrawWasRinshan = false;
    public boolean scoreAsNotFirstRound = false;

    /** Seat who discarded the tile under claim; {@code -1} when not in {@link TurnPhase#CLAIM_WINDOW}. */
    public int claimDiscarderSeat = -1;
    /** Tile code on the river tail being claimed. */
    public int claimTileCode = -1;
    /** Next seat to draw if all players pass the claim window. */
    public int claimNextDrawerSeat = -1;
    /** True when the active claim window is a chankan (rob-the-kan) window, not a normal discard window. */
    public boolean claimIsChankanWindow = false;

    public final ClaimWindowRules.ClaimIntent[] claimIntent = new ClaimWindowRules.ClaimIntent[4];
    public final int[] claimChiTileA = new int[4];
    public final int[] claimChiTileB = new int[4];
    public final boolean[] riichiDeclared = new boolean[4];
    public final boolean[] riichiPending = new boolean[4];
    /** True for a seat that declared riichi and has not yet completed one full discard cycle or had any call made. */
    public final boolean[] ippatsuEligible = new boolean[4];
    /** Temporary furiten from declined winning-shape discard; cleared on next self discard. */
    public final boolean[] temporaryFuriten = new boolean[4];
    /** Permanent furiten after riichi when declining a winning-shape discard (or equivalent missed win). */
    public final boolean[] riichiPermanentFuriten = new boolean[4];
    /** Per-seat history of discarded tile codes this hand (includes tiles later called by others). */
    public final boolean[][] seenDiscardsBySeatAndTile = new boolean[4][34];

    public MahjongGameState() {
        Arrays.fill(claimIntent, ClaimWindowRules.ClaimIntent.NONE);
        Arrays.fill(claimChiTileA, -1);
        Arrays.fill(claimChiTileB, -1);
        Arrays.fill(riichiDeclared, false);
        Arrays.fill(riichiPending, false);
        Arrays.fill(ippatsuEligible, false);
        Arrays.fill(temporaryFuriten, false);
        Arrays.fill(riichiPermanentFuriten, false);
        for (int s = 0; s < 4; s++) {
            Arrays.fill(seenDiscardsBySeatAndTile[s], false);
        }
    }

    /** Dealer opens in {@link TurnPhase#MUST_DRAW} like every draw turn. */
    public void beginHandFromPrepared(int dealerSeat) {
        currentTurnSeat = dealerSeat;
        phase = TurnPhase.MUST_DRAW;
        lastDrawnCode = -1;
        lastDrawWasRinshan = false;
        scoreAsNotFirstRound = false;
        Arrays.fill(riichiDeclared, false);
        Arrays.fill(riichiPending, false);
        Arrays.fill(ippatsuEligible, false);
        Arrays.fill(temporaryFuriten, false);
        Arrays.fill(riichiPermanentFuriten, false);
        for (int s = 0; s < 4; s++) {
            Arrays.fill(seenDiscardsBySeatAndTile[s], false);
        }
        clearClaimWindowFields();
    }

    private void clearClaimWindowFields() {
        claimDiscarderSeat = -1;
        claimTileCode = -1;
        claimNextDrawerSeat = -1;
        claimIsChankanWindow = false;
        Arrays.fill(claimIntent, ClaimWindowRules.ClaimIntent.NONE);
        Arrays.fill(claimChiTileA, -1);
        Arrays.fill(claimChiTileB, -1);
    }

    public boolean isClaimWindowActive() {
        return phase == TurnPhase.CLAIM_WINDOW;
    }

    public void resetClaimIntentsForNewWindow() {
        Arrays.fill(claimIntent, ClaimWindowRules.ClaimIntent.NONE);
        Arrays.fill(claimChiTileA, -1);
        Arrays.fill(claimChiTileB, -1);
    }

    /** After all players pass the claim window: next player draws. */
    public void finishClaimWindowWithAllPass() {
        clearClaimWindowFields();
        phase = TurnPhase.MUST_DRAW;
    }

    /** After a meld claim the claimant discards next (no rinshan in Phase 6). */
    public void finishClaimWithMeld(int claimantSeat) {
        clearClaimWindowFields();
        currentTurnSeat = claimantSeat;
        phase = TurnPhase.MUST_DISCARD;
    }

}
