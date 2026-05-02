package com.riichimahjongforge;

/**
 * High-level table session phase (lobby vs in-match stub). Server-authoritative; replicated via block entity sync.
 */
public enum TableMatchPhase {
    WAITING,
    IN_MATCH,
    HAND_RESULT
}
