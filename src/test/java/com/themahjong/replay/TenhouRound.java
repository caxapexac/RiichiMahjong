package com.themahjong.replay;

import com.themahjong.TheMahjongTile;
import java.util.List;

/**
 * Parsed state for one hand (between two INIT elements) in a Tenhou log.
 *
 * @param roundNumber  seed[0]: 0=East-1, 1=East-2, ..., 4=South-1, etc.
 * @param honba        seed[1]
 * @param riichiSticks seed[2]
 * @param dealer       oya attribute (0-3)
 * @param doraIndicator first dora indicator from seed[5]
 * @param startingScores ten attribute (points per seat at round start, in 100-point units)
 * @param initialHands  hai0-hai3 decoded to TheMahjongTile lists (index = seat)
 * @param actions       ordered event sequence for this hand
 */
public record TenhouRound(
        int roundNumber,
        int honba,
        int riichiSticks,
        int dealer,
        TheMahjongTile doraIndicator,
        int[] startingScores,
        List<List<TheMahjongTile>> initialHands,
        List<TenhouAction> actions
) {
    /** Number of active seats — 3 for sanma, 4 for standard. Derived from non-empty initial hands. */
    public int playerCount() {
        return (int) initialHands.stream().filter(h -> !h.isEmpty()).count();
    }
}
