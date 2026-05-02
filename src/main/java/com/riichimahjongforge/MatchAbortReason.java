package com.riichimahjongforge;

/** Why a match returned to {@link TableMatchPhase#WAITING} (for packets / client copy). */
public enum MatchAbortReason {
    TABLE_BROKEN,
    PLAYER_LEFT,
    KICK,
    SIDE_CLOSED,
    RESET,
    GENERIC
}
