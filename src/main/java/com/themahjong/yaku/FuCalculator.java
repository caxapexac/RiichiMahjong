package com.themahjong.yaku;

import com.themahjong.TheMahjongRuleSet;
import com.themahjong.TheMahjongMeld;
import com.themahjong.TheMahjongTile;
import com.themahjong.yaku.HandShape.ConcealedGroup;
import com.themahjong.yaku.HandShape.Standard;

/**
 * Computes the fu (minipoints) for a winning hand.
 *
 * Rules applied:
 *   - Chitoitsu → fixed 25 fu
 *   - Kokushimusou → fixed 30 fu (yakuman; fu is informational only)
 *   - Pinfu tsumo → fixed 20 fu
 *   - Standard: base (30 for closed ron, 20 otherwise) + tsumo bonus (+2) +
 *     group fu + pair fu + wait fu, rounded up to nearest 10
 *
 * Double-wind pair (seat wind == round wind) counts as 4 fu.
 */
public final class FuCalculator {

    private FuCalculator() {}

    public static int calculate(HandShape shape, WinContext ctx, TheMahjongRuleSet rules) {
        if (shape instanceof HandShape.Chitoitsu) return 25;
        if (shape instanceof HandShape.Kokushimusou) return 30;
        Standard s = (Standard) shape;

        // Pinfu: ron = 30 fu (20 base + 10 menzen bonus), tsumo = 20 fu (no bonuses).
        // Short-circuit so the wait fu calc cannot pick a kanchan/penchan from a sequence
        // that also contains the winning tile but isn't the actual completing group.
        if (NonYakuman.checkPinfu(s, ctx)) return ctx.tsumo() ? 20 : 30;

        // Base: closed ron (incl. chankan) gets the +10 menzen bonus folded in
        boolean ronLike = ctx.winType() != WinContext.WinType.TSUMO;
        int fu = (ronLike && s.closed()) ? 30 : 20;

        if (ctx.tsumo()) fu += 2; // tsumo bonus (skipped for pinfu above)

        // The "ron-completed concealed triplet treated as open" rule applies only when
        // the wait was genuinely shanpon. If the decomposition is annotated (came from
        // {@link HandShape#decomposeForWin}), use the wait shape directly; otherwise
        // scan groups via {@link #winTileCanOnlyCompleteTriplet}.
        boolean shanponRon = ronLike
                && (s.waitShape().isPresent()
                        ? s.waitShape().get() == HandShape.WaitShape.SHANPON
                        : winTileCanOnlyCompleteTriplet(s, ctx.winningTile()));

        for (TheMahjongMeld meld : s.melds()) fu += meldFu(meld);
        for (ConcealedGroup group : s.closedGroups()) fu += closedGroupFu(group, ctx, shanponRon);

        fu += pairFu(s.pair(), ctx, rules.doubleWindPairFu4());
        fu += s.waitShape().map(HandShape.WaitShape::fu).orElseGet(() -> waitFu(s, ctx.winningTile()));

        return Math.max(30, roundUpTo10(fu));
    }

    // -------------------------------------------------------------------------
    // Group fu
    // -------------------------------------------------------------------------

    private static int meldFu(TheMahjongMeld meld) {
        if (meld instanceof TheMahjongMeld.Chi)       return 0;
        if (meld instanceof TheMahjongMeld.Pon p)     return tripletFu(p.tiles().get(0), false);
        if (meld instanceof TheMahjongMeld.Daiminkan d) return kanFu(d.tiles().get(0), false);
        if (meld instanceof TheMahjongMeld.Kakan k)   return kanFu(k.tiles().get(0), false);
        if (meld instanceof TheMahjongMeld.Ankan a)   return kanFu(a.tiles().get(0), true);
        return 0;
    }

    private static int closedGroupFu(ConcealedGroup group, WinContext ctx, boolean shanponRon) {
        if (group instanceof ConcealedGroup.Sequence) return 0;
        if (group instanceof ConcealedGroup.Triplet t) {
            // Treat as open only when the win really was a shanpon ron on this triplet.
            boolean closed = !(shanponRon && ctx.winningTile().matchesSuitRank(t.tiles().get(0)));
            return tripletFu(t.tiles().get(0), closed);
        }
        return 0;
    }

    /**
     * True when the winning tile fits no sequence or pair in this decomposition —
     * meaning the only valid wait it could have completed is shanpon on a triplet.
     * Used by both fu calculation (to decide whether to demote a closed triplet to
     * "open via ron") and yaku detection (sanankou / suuankou pre-formed-triplet count).
     */
    static boolean winTileCanOnlyCompleteTriplet(Standard s, TheMahjongTile winTile) {
        if (winTile.matchesSuitRank(s.pair())) return false;
        for (ConcealedGroup g : s.closedGroups()) {
            if (g instanceof ConcealedGroup.Sequence seq) {
                for (TheMahjongTile t : seq.tiles()) {
                    if (winTile.matchesSuitRank(t)) return false;
                }
            }
        }
        return true;
    }

    private static int tripletFu(TheMahjongTile tile, boolean closed) {
        boolean yaochu = tile.terminal() || tile.honor();
        return yaochu ? (closed ? 8 : 4) : (closed ? 4 : 2);
    }

    private static int kanFu(TheMahjongTile tile, boolean closed) {
        boolean yaochu = tile.terminal() || tile.honor();
        return yaochu ? (closed ? 32 : 16) : (closed ? 16 : 8);
    }

    // -------------------------------------------------------------------------
    // Pair fu
    // -------------------------------------------------------------------------

    private static int pairFu(TheMahjongTile pair, WinContext ctx, boolean doubleWindPairFu4) {
        if (pair.suit() == TheMahjongTile.Suit.DRAGON) return 2;
        if (pair.suit() == TheMahjongTile.Suit.WIND) {
            int rank = pair.rank();
            boolean isSeat  = rank == ctx.seatWind().tileRank();
            boolean isRound = rank == ctx.roundWind().tileRank();
            if (isSeat && isRound) return doubleWindPairFu4 ? 4 : 2;
            if (isSeat || isRound) return 2;
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // Wait fu
    // -------------------------------------------------------------------------

    /**
     * Determines wait type from the decomposition and winning tile.
     *
     * The same winning tile can fit several positions in a hand (e.g. mid of one
     * sequence and edge of another). The player chooses the interpretation that
     * maximises score, so we return the highest fu among all possible waits:
     *   tanki / kanchan / penchan = 2 fu
     *   ryanmen / shanpon          = 0 fu
     *
     * Pinfu hands (which require a 0-fu wait) are handled by the early return
     * in {@link #calculate} and never reach this method.
     */
    static int waitFu(Standard s, TheMahjongTile winTile) {
        int best = -1;
        for (ConcealedGroup group : s.closedGroups()) {
            if (group instanceof ConcealedGroup.Triplet t) {
                if (winTile.matchesSuitRank(t.tiles().get(0))) best = Math.max(best, 0); // shanpon
            } else if (group instanceof ConcealedGroup.Sequence seq) {
                TheMahjongTile low  = seq.tiles().get(0);
                TheMahjongTile mid  = seq.tiles().get(1);
                TheMahjongTile high = seq.tiles().get(2);
                if (winTile.matchesSuitRank(mid)) best = Math.max(best, 2); // kanchan
                else if (winTile.matchesSuitRank(low)) {
                    best = Math.max(best, high.rank() == low.suit().maxRank() ? 2 : 0); // penchan/ryanmen
                } else if (winTile.matchesSuitRank(high)) {
                    best = Math.max(best, low.rank() == 1 ? 2 : 0); // penchan/ryanmen
                }
            }
        }
        if (winTile.matchesSuitRank(s.pair())) best = Math.max(best, 2); // tanki
        return Math.max(best, 0);
    }

    static int roundUpTo10(int fu) {
        return ((fu + 9) / 10) * 10;
    }
}
