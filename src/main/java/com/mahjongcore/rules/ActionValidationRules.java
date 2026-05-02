package com.mahjongcore.rules;

public final class ActionValidationRules {

    private ActionValidationRules() {}

    public enum GateResult {
        OK,
        WRONG_PHASE,
        NOT_YOUR_TURN
    }

    public static GateResult validateDrawAction(
            boolean inMatch,
            boolean mustDrawPhase,
            boolean isCurrentTurnSeat,
            boolean seatEnabled,
            boolean seatOccupied) {
        if (!inMatch || !mustDrawPhase || !seatOccupied) {
            return GateResult.WRONG_PHASE;
        }
        if (!isCurrentTurnSeat || !seatEnabled) {
            return GateResult.NOT_YOUR_TURN;
        }
        return GateResult.OK;
    }

    public static GateResult validateDiscardAction(boolean mustDiscardPhase, boolean isCurrentTurnSeat) {
        if (!isCurrentTurnSeat) {
            return GateResult.NOT_YOUR_TURN;
        }
        if (!mustDiscardPhase) {
            return GateResult.WRONG_PHASE;
        }
        return GateResult.OK;
    }
}
