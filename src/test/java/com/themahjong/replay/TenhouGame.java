package com.themahjong.replay;

import java.util.List;

/**
 * A fully parsed Tenhou game log (one mjloggm element = one game, possibly multiple rounds).
 *
 * @param gameId  filename stem used to identify the fixture
 * @param rounds  one entry per INIT element, in order
 */
public record TenhouGame(String gameId, List<TenhouRound> rounds) {}
