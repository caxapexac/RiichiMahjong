package com.themahjong.yaku;

import com.themahjong.TheMahjongDiscard;
import com.themahjong.TheMahjongPlayer;
import com.themahjong.TheMahjongTile;

import java.util.List;

/**
 * Single source of truth for furiten — the rule preventing a player from declaring ron when
 * their wait set overlaps tiles they've already passed on (whether by their own discards or
 * by declining a ron opportunity).
 *
 * <p>Three independent flavors, all OR'd together by {@link #isFuriten}:
 * <ol>
 *   <li><b>Discard-river furiten</b>: any tile in the player's wait set appears anywhere in
 *       their own discard pile. Computed on demand from {@code player.discards()} and
 *       {@link TenpaiChecker#winningTiles}; not stored. Cleared implicitly when the player's
 *       wait changes (next draw might give a different wait).
 *   <li><b>Temporary furiten</b>: the player declined a ron opportunity since their last self
 *       draw. Tracked as the non-emptiness of {@link TheMahjongPlayer#temporaryFuritenTiles}.
 *       Cleared on the player's next {@code draw()}.
 *   <li><b>Riichi permanent furiten</b>: the player declined a ron while in riichi. Once set,
 *       the player cannot ron for the remainder of the round. Tracked by
 *       {@link TheMahjongPlayer#riichiPermanentFuriten}.
 * </ol>
 *
 * <p>All ron-legality checks should go through {@link #canRon} — never reimplement the rule
 * inline.
 */
public final class Furiten {

    private Furiten() {}

    /** True when the player is in any of the three furiten states. */
    public static boolean isFuriten(TheMahjongPlayer player) {
        if (player.riichiPermanentFuriten()) return true;
        if (player.temporaryFuriten()) return true;
        return inDiscardRiverFuriten(player);
    }

    /**
     * True when at least one tile in the player's current wait set already sits in their own
     * discard river. Returns false if the player is not tenpai.
     */
    public static boolean inDiscardRiverFuriten(TheMahjongPlayer player) {
        List<TheMahjongTile> waits = TenpaiChecker.winningTiles(player);
        if (waits.isEmpty()) return false;
        for (TheMahjongDiscard d : player.discards()) {
            for (TheMahjongTile w : waits) {
                if (d.tile().matchesSuitRank(w)) return true;
            }
        }
        return false;
    }

    /**
     * True when {@code winningTile} would legally complete a ron for {@code player}: the tile
     * is in their wait set AND the player is not in any furiten state. Use this as the single
     * gate for "can the engine accept a ron declaration right now?"
     */
    public static boolean canRon(TheMahjongPlayer player, TheMahjongTile winningTile) {
        if (isFuriten(player)) return false;
        for (TheMahjongTile w : TenpaiChecker.winningTiles(player)) {
            if (w.matchesSuitRank(winningTile)) return true;
        }
        return false;
    }
}
