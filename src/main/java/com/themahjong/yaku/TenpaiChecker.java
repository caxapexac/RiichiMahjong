package com.themahjong.yaku;

import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongTile;

import java.util.ArrayList;
import java.util.List;

/**
 * Determines whether a player's hand is in tenpai (one tile away from a complete winning hand).
 */
public final class TenpaiChecker {

    private TenpaiChecker() {}

    /**
     * Returns all tiles that would complete the player's hand (the tenpai wait set).
     * Checks every unique suit+rank combination (34 types), ignoring red-dora variants.
     *
     * <p>The player's hand must be at the standard pre-draw size:
     * {@code 13 - 3 * openMeldCount} tiles (e.g. 13 for a closed hand, 10 with one open meld).
     *
     * @return one representative tile (non-red) per winning type; empty if not in tenpai
     */
    public static List<TheMahjongTile> winningTiles(TheMahjongPlayer player) {
        List<TheMahjongTile> hand = player.currentHand();
        List<TheMahjongTile> result = new ArrayList<>();

        for (TheMahjongTile.Suit suit : TheMahjongTile.Suit.values()) {
            for (int rank = 1; rank <= suit.maxRank(); rank++) {
                TheMahjongTile candidate = new TheMahjongTile(suit, rank, false);
                List<TheMahjongTile> withCandidate = new ArrayList<>(hand);
                withCandidate.add(candidate);
                if (!HandShape.decompose(withCandidate, player.melds()).isEmpty()) {
                    result.add(candidate);
                }
            }
        }
        return result;
    }

    /** True if the player's hand is in tenpai. */
    public static boolean inTenpai(TheMahjongPlayer player) {
        return !winningTiles(player).isEmpty();
    }
}
