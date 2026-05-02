package com.riichimahjongforge.player;

import com.riichimahjongforge.MahjongTableBlockEntity;
import com.riichimahjongforge.TableMatchPhase;
import com.mahjongcore.rules.ClaimIntentRules;
import com.mahjongcore.rules.ClaimWindowRules;
import com.mahjongcore.rules.WinRules;
import com.mahjongcore.MahjongGameState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.mahjongcore.hands.Mentsu;
import com.mahjongcore.rules.ClaimLegalityRules;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

public class BotSeatPlayer implements MahjongSeatPlayer {

    private static final int DRAW_DELAY_TICKS = 7;
    private static final int DISCARD_DELAY_TICKS = 7;
    private static final int CLAIM_DELAY_TICKS = 7;

    private final UUID uuid;

    // Turn delay: tracks when this bot's current MUST_DRAW/MUST_DISCARD delay expires.
    private MahjongGameState.TurnPhase scheduledTurnPhase = null;
    private long nextTurnActionTime = Long.MIN_VALUE;

    // Claim delay: tracks when this bot's claim decision delay expires.
    private long nextClaimDecisionTime = Long.MIN_VALUE;

    /** Stable UUID per compass seat; never use for real players. */
    public static UUID uuidForSeat(int seatIndex) {
        return switch (seatIndex) {
            case 0 -> UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-000000000001");
            case 1 -> UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-000000000002");
            case 2 -> UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-000000000003");
            case 3 -> UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-000000000004");
            default -> throw new IllegalArgumentException("seatIndex " + seatIndex);
        };
    }

    public static boolean isBotUuid(@Nullable UUID id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < MahjongTableBlockEntity.SEAT_COUNT; i++) {
            if (uuidForSeat(i).equals(id)) {
                return true;
            }
        }
        return false;
    }

    public BotSeatPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public String actorDebugLabel(ServerLevel level, int seat) {
        return "bot:seat" + (seat + 1);
    }

    @Override
    @Nullable
    public ServerPlayer onlinePlayer(ServerLevel level) {
        return null;
    }

    @Override
    public void tick(ServerLevel level, MahjongTableBlockEntity table, int seat) {
        MahjongGameState gs = table.activeGameState();
        if (gs == null || table.currentMatchPhase() != TableMatchPhase.IN_MATCH) return;

        if (gs.isClaimWindowActive()) {
            tickClaimWindow(level, table, seat, gs);
            return;
        }

        if (gs.currentTurnSeat != seat || !table.isSeatEnabled(seat)) {
            return;
        }

        if (gs.phase == MahjongGameState.TurnPhase.MUST_DRAW) {
            tickDraw(level, table, seat, gs);
        } else if (gs.phase == MahjongGameState.TurnPhase.MUST_DISCARD) {
            tickDiscard(level, table, seat, gs);
        }
    }

    private void tickDraw(
            ServerLevel level, MahjongTableBlockEntity table, int seat, MahjongGameState gs) {
        if (scheduledTurnPhase != gs.phase) {
            scheduledTurnPhase = gs.phase;
            nextTurnActionTime = level.getGameTime() + DRAW_DELAY_TICKS;
            return;
        }
        if (level.getGameTime() < nextTurnActionTime) return;
        table.performSeatDraw(level, seat);
        scheduledTurnPhase = null;
        nextTurnActionTime = Long.MIN_VALUE;
    }

    private void tickDiscard(
            ServerLevel level, MahjongTableBlockEntity table, int seat, MahjongGameState gs) {
        if (scheduledTurnPhase != gs.phase) {
            scheduledTurnPhase = gs.phase;
            nextTurnActionTime = level.getGameTime() + DISCARD_DELAY_TICKS;
            return;
        }
        if (level.getGameTime() < nextTurnActionTime) return;
        List<Integer> kanCandidates = table.visibleKanCandidateCodes(seat);
        if (!kanCandidates.isEmpty() && table.applySeatKanForCode(level, seat, kanCandidates.get(0))) {
            scheduledTurnPhase = null;
            nextTurnActionTime = Long.MIN_VALUE;
            return;
        }
        table.performSeatDiscard(level, seat);
        scheduledTurnPhase = null;
        nextTurnActionTime = Long.MIN_VALUE;
    }

    private void tickClaimWindow(
            ServerLevel level, MahjongTableBlockEntity table, int seat, MahjongGameState gs) {
        if (gs.claimIntent[seat] != ClaimWindowRules.ClaimIntent.NONE) {
            nextClaimDecisionTime = Long.MIN_VALUE;
            return;
        }
        if (nextClaimDecisionTime == Long.MIN_VALUE) {
            nextClaimDecisionTime = level.getGameTime() + CLAIM_DELAY_TICKS;
            return;
        }
        if (level.getGameTime() < nextClaimDecisionTime) return;
        fillClaimDecision(table, gs, seat);
        table.onClaimDecisionApplied(seat);
        nextClaimDecisionTime = Long.MIN_VALUE;
    }

    private void fillClaimDecision(MahjongTableBlockEntity table, MahjongGameState gs, int seat) {
        if (table.passiveBots() || !table.seatHasLegalClaim(seat)) {
            gs.claimIntent[seat] = ClaimWindowRules.ClaimIntent.PASS;
            return;
        }
        int t = gs.claimTileCode;
        ArrayDeque<Integer> hand = table.visibleConcealedHandCodes(seat);
        ArrayList<Mentsu> melds = table.visibleMeldsAsMentsuList(seat);
        if (WinRules.canRon(
                gs.isClaimWindowActive(),
                seat,
                gs.claimDiscarderSeat,
                table.deterministicPlayOrder(),
                gs.riichiDeclared,
                gs.ippatsuEligible,
                gs.claimIsChankanWindow,
                t,
                hand,
                melds,
                gs.handNumber,
                gs.scoreAsNotFirstRound,
                table.getLiveWallRemainingFromInventory(),
                table.getDoraIndicatorCodesForScoring())) {
            gs.claimIntent[seat] = ClaimWindowRules.ClaimIntent.RON;
            return;
        }
        if (ClaimLegalityRules.canDaiminKan(hand, t)) {
            gs.claimIntent[seat] = ClaimWindowRules.ClaimIntent.DAIMIN_KAN;
            return;
        }
        if (ClaimLegalityRules.canPon(hand, t)) {
            gs.claimIntent[seat] = ClaimWindowRules.ClaimIntent.PON;
            return;
        }
        if (ClaimIntentRules.isKamicha(table.deterministicPlayOrder(), gs.claimDiscarderSeat, seat)) {
            List<ClaimLegalityRules.ChiPair> pairs = ClaimLegalityRules.findChiPairs(hand, t);
            if (!pairs.isEmpty()) {
                ClaimLegalityRules.ChiPair p = pairs.get(0);
                gs.claimIntent[seat] = ClaimWindowRules.ClaimIntent.CHI;
                gs.claimChiTileA[seat] = p.tileA();
                gs.claimChiTileB[seat] = p.tileB();
                return;
            }
        }
        gs.claimIntent[seat] = ClaimWindowRules.ClaimIntent.PASS;
    }

    @Override
    public void resetDelay() {
        scheduledTurnPhase = null;
        nextTurnActionTime = Long.MIN_VALUE;
        nextClaimDecisionTime = Long.MIN_VALUE;
    }

    @Override
    public void onSeatVacated() {
        resetDelay();
    }
}
